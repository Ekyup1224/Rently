#!/usr/bin/env python3
"""End-to-end check of the Step 2 house rental loop against a running stack.

Walks the whole path a real listing takes: owner creates a draft, uploads a
photo, submits it, an admin approves it, a guest finds it in search, prices it,
books it, pays, and cancels — verifying the refund, the calendar and the
double-booking guard along the way.

Requires the simulated payment provider (``app.payments.simulated.enabled=true``)
and reads OTP codes from the backend log, so it is a development-only script::

    python3 scripts/smoke-test-step2.py --log /tmp/backend.log

Exits non-zero on the first failed expectation.
"""

import argparse
import json
import re
import struct
import sys
import time
import urllib.error
import urllib.request
import zlib
from datetime import date, timedelta

PASS, FAIL = "\033[32mPASS\033[0m", "\033[31mFAIL\033[0m"
failures = []
BASE = "http://localhost:8080"
API = BASE + "/api/v1"
LOG_PATH = None


def call(method, path, body=None, token=None, headers=None, raw_body=None):
    """@return (status_code, parsed_body_or_None)"""
    url = path if path.startswith("http") else API + path
    request = urllib.request.Request(url, method=method)
    if headers:
        for name, value in headers.items():
            request.add_header(name, value)
    if token:
        request.add_header("Authorization", "Bearer " + token)

    data = None
    if raw_body is not None:
        data = raw_body.encode() if isinstance(raw_body, str) else raw_body
    elif body is not None:
        request.add_header("Content-Type", "application/json")
        data = json.dumps(body).encode()

    try:
        with urllib.request.urlopen(request, data, timeout=20) as response:
            payload = response.read()
            return response.status, (json.loads(payload) if payload else None)
    except urllib.error.HTTPError as error:
        payload = error.read()
        try:
            return error.code, (json.loads(payload) if payload else None)
        except json.JSONDecodeError:
            return error.code, {"raw": payload.decode(errors="replace")}


def money(value):
    """Money arrives as a JSON number (Jackson 3 does not stringify BigDecimal)."""
    return None if value is None else round(float(value), 2)


def check(label, condition, detail=""):
    print(f"  [{PASS if condition else FAIL}] {label}"
          + ("" if condition else f"  <- {detail}"))
    if not condition:
        failures.append(label)
    return condition


def log_size():
    with open(LOG_PATH, "rb") as handle:
        handle.seek(0, 2)
        return handle.tell()


def latest_code(offset):
    for _ in range(24):
        with open(LOG_PATH, "rb") as handle:
            handle.seek(offset)
            found = re.findall(rb"\[DEV SMS\].*?body=(\d+)", handle.read())
        if found:
            return found[-1].decode()
        time.sleep(0.25)
    raise SystemExit("No dev SMS code found; is app.otp.delivery=log?")


def sign_in(phone):
    """Creates or signs in an account by phone.

    @return (access_token, refresh_token, user_id)
    """
    offset = log_size()
    status, body = call("POST", "/auth/otp/request", {"phone": phone})
    if status != 200:
        raise SystemExit(f"otp/request failed for {phone}: {status} {body}")
    status, session = call("POST", "/auth/otp/verify",
                           {"phone": phone, "code": latest_code(offset)})
    if status != 200:
        raise SystemExit(f"otp/verify failed for {phone}: {status} {session}")
    return session["accessToken"], session["refreshToken"], session["user"]["id"]


def refresh(refresh_token):
    """Trades a refresh token for a new pair.

    Used after a role grant: the access token's role claims are fixed at issue
    time, so picking up a new role means refreshing, not signing in again.

    @return (access_token, refresh_token)
    """
    status, session = call("POST", "/auth/refresh", {"refreshToken": refresh_token})
    if status != 200:
        raise SystemExit(f"refresh failed: {status} {session}")
    return session["accessToken"], session["refreshToken"]


def tiny_png():
    """A valid 2x2 PNG, so the photo upload path handles real image bytes."""
    raw = b"".join(b"\x00" + bytes([200, 200, 200]) * 2 for _ in range(2))

    def chunk(kind, data):
        return (struct.pack(">I", len(data)) + kind + data
                + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF))

    return (b"\x89PNG\r\n\x1a\n"
            + chunk(b"IHDR", struct.pack(">IIBBBBB", 2, 2, 8, 2, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw, 9))
            + chunk(b"IEND", b""))


def upload_photo(owner_token, property_id):
    """Runs the full presign -> PUT -> confirm flow. @return the photo response."""
    image = tiny_png()
    status, presigned = call("POST", f"/owner/properties/{property_id}/photos/upload-url",
                             {"contentType": "image/png", "sizeBytes": len(image)},
                             token=owner_token)
    if status != 200:
        return status, presigned

    put = urllib.request.Request(presigned["uploadUrl"], method="PUT", data=image)
    put.add_header("Content-Type", "image/png")
    with urllib.request.urlopen(put, timeout=20) as response:
        if response.status not in (200, 204):
            raise SystemExit(f"Presigned PUT failed: {response.status}")

    return call("POST", f"/owner/properties/{property_id}/photos",
                {"storageKey": presigned["storageKey"], "altText": "Living room"},
                token=owner_token)



def retire(admin_token, listings=()):
    """Takes this run's supply back off sale.

    A smoke test that publishes a listing and walks away leaves it in live
    search, so after a dozen runs the demo front page is a wall of fixtures.
    Suspending rather than deleting, because bookings and payouts reference
    these rows — the same reason the product suspends rather than deletes.
    """
    for listing_id in listings:
        call("PATCH", f"/admin/properties/{listing_id}/status",
             {"status": "SUSPENDED", "reason": "Smoke test fixture"}, token=admin_token)

def main():
    global LOG_PATH
    parser = argparse.ArgumentParser()
    parser.add_argument("--log", required=True)
    parser.add_argument("--admin-email", default="admin@stay.local")
    parser.add_argument("--admin-password", default="ChangeMe123!")
    args = parser.parse_args()
    LOG_PATH = args.log

    stamp = str(int(time.time()))[-6:]
    owner_phone = "+9769" + stamp + "1"
    guest_phone = "+9769" + stamp + "2"
    rival_phone = "+9769" + stamp + "3"

    check_in = date.today() + timedelta(days=30)
    check_out = check_in + timedelta(days=3)

    print("\nsetup")
    status, admin_session = call("POST", "/auth/login",
                                 {"email": args.admin_email, "password": args.admin_password})
    if not check("admin signed in", status == 200, admin_session):
        return report()
    admin_token = admin_session["accessToken"]

    owner_token, owner_refresh, owner_id = sign_in(owner_phone)
    guest_token, _, guest_id = sign_in(guest_phone)
    check("owner and guest accounts created", bool(owner_id) and bool(guest_id))

    status, _ = call("POST", f"/admin/users/{owner_id}/roles",
                     {"role": "HOUSE_OWNER"}, token=admin_token)
    check("admin granted HOUSE_OWNER", status == 200)

    status, body = call("GET", "/owner/properties", token=owner_token)
    check("a token issued before the grant lacks the new role", status == 403, body)
    # Role claims are baked into the access token, so a refresh is what picks up a
    # newly granted role -- no re-authentication needed.
    owner_token, owner_refresh = refresh(owner_refresh)

    print("\nrole enforcement")
    status, body = call("GET", "/owner/properties", token=guest_token)
    check("a guest cannot reach the owner API",
          status == 403 and body.get("code") == "forbidden", body)
    status, body = call("GET", "/owner/properties", token=owner_token)
    check("the refreshed owner token can", status == 200, body)

    print("\nlisting creation")
    status, listing = call("POST", "/owner/properties",
                           {"title": f"Ger camp by the river {stamp}", "propertyType": "GER",
                            "city": "Ulaanbaatar", "maxGuests": 4}, token=owner_token)
    if not check("draft created", status == 201 and listing.get("status") == "DRAFT", listing):
        return report()
    property_id = listing["id"]
    check("draft reports what is missing", len(listing["readinessProblems"]) > 0,
          listing["readinessProblems"])

    status, body = call("POST", f"/owner/properties/{property_id}/submit", token=owner_token)
    check("an incomplete listing cannot be submitted",
          status == 400 and body.get("code") == "listing_incomplete", body)

    status, listing = call("PATCH", f"/owner/properties/{property_id}", {
        "description": "A traditional ger with a wood stove, 40km from the city.",
        "addressLine": "Gachuurt valley, plot 14",
        "district": "Bayanzurkh",
        "latitude": 47.9405, "longitude": 107.1210,
        "basePrice": 180000, "cleaningFee": 25000,
        "amenities": ["WIFI", "STOVE_HEATING", "PARKING_FREE", "MOUNTAIN_VIEW"],
        "minStayNights": 2, "cancellationPolicy": "MODERATE", "instantBook": False,
    }, token=owner_token)
    check("details saved", status == 200 and money(listing["basePrice"]) == 180000.00, listing)
    check("amenities stored", set(listing["amenities"]) == {
        "WIFI", "STOVE_HEATING", "PARKING_FREE", "MOUNTAIN_VIEW"}, listing.get("amenities"))

    status, body = call("PATCH", f"/owner/properties/{property_id}",
                        {"amenities": ["WIFI", "TELEPORTER"]}, token=owner_token)
    check("an unknown amenity is rejected",
          status == 400 and body.get("code") == "unknown_amenity", body)

    print("\nphotos")
    status, photo = upload_photo(owner_token, property_id)
    check("photo uploaded and confirmed", status == 201 and photo.get("url"), photo)
    check("first photo becomes the cover", photo.get("cover") is True, photo)

    status, body = call("POST", f"/owner/properties/{property_id}/photos",
                        {"storageKey": f"properties/{property_id}/never-uploaded.png"},
                        token=owner_token)
    check("confirming a file that was never uploaded fails",
          status == 400 and body.get("code") == "upload_not_found", body)

    status, body = call("POST", f"/owner/properties/{property_id}/photos",
                        {"storageKey": "properties/00000000-0000-7000-8000-000000000000/x.png"},
                        token=owner_token)
    check("a key from another listing is refused",
          status == 400 and body.get("code") == "storage_key_mismatch", body)

    print("\nreview")
    status, listing = call("POST", f"/owner/properties/{property_id}/submit", token=owner_token)
    check("complete listing submits", status == 200 and listing["status"] == "PENDING_REVIEW",
          listing)

    status, body = call("GET", f"/listings/{property_id}")
    check("an unapproved listing is not publicly visible", status == 404, body)

    status, body = call("PATCH", f"/admin/properties/{property_id}/status",
                        {"status": "REJECTED"}, token=admin_token)
    check("rejection requires a reason",
          status == 400 and body.get("code") == "reason_required", body)

    status, listing = call("PATCH", f"/admin/properties/{property_id}/status",
                           {"status": "APPROVED"}, token=admin_token)
    check("admin approved the listing", status == 200 and listing["status"] == "APPROVED", listing)

    status, detail = call("GET", f"/listings/{property_id}")
    check("public listing page resolves", status == 200 and detail["title"], detail)
    check("public page hides the street address", "addressLine" not in detail, list(detail))
    check("public page shows only the host's first name",
          detail["host"]["displayName"] in ("Host", None) or " " not in detail["host"]["displayName"],
          detail.get("host"))

    print("\nsearch")
    status, results = call("GET", f"/listings/search?q=ger&city=Ulaanbaatar")
    found = any(row["id"] == property_id for row in results.get("rows", []))
    check("full-text search finds it", status == 200 and found, results)

    status, results = call("GET", "/listings/search?amenities=STOVE_HEATING&amenities=WIFI")
    check("amenity filter matches all requested",
          any(row["id"] == property_id for row in results.get("rows", [])), results)
    status, results = call("GET", "/listings/search?amenities=POOL")
    check("amenity filter excludes non-matches",
          not any(row["id"] == property_id for row in results.get("rows", [])), results)
    status, results = call("GET", "/listings/search?guests=9")
    check("capacity filter excludes too-small listings",
          not any(row["id"] == property_id for row in results.get("rows", [])), results)
    status, results = call("GET", "/listings/search?maxPrice=100000")
    check("price filter works",
          not any(row["id"] == property_id for row in results.get("rows", [])), results)
    status, body = call("GET", "/listings/search?checkIn=2020-01-01&checkOut=2020-01-05")
    check("a past date range is rejected",
          status == 400 and body.get("code") == "check_in_in_past", body)
    status, body = call("GET", f"/listings/search?checkIn={check_in}")
    check("half a date range is rejected",
          status == 400 and body.get("code") == "incomplete_date_range", body)

    print("\npricing")
    status, body = call("POST", f"/listings/{property_id}/quote",
                        {"checkIn": str(check_in), "checkOut": str(check_in + timedelta(days=1)),
                         "guests": 2})
    check("a stay under the minimum is refused",
          status == 400 and body.get("code") == "min_stay_not_met", body)

    status, body = call("POST", f"/listings/{property_id}/quote",
                        {"checkIn": str(check_in), "checkOut": str(check_out), "guests": 9})
    check("too many guests is refused",
          status == 400 and body.get("code") == "too_many_guests", body)

    status, quote = call("POST", f"/listings/{property_id}/quote",
                         {"checkIn": str(check_in), "checkOut": str(check_out), "guests": 2})
    if not check("quote returned", status == 200, quote):
        return report()
    check("three nights priced", quote["nights"] == 3 and len(quote["nightlyRates"]) == 3, quote)
    check("nightly subtotal is 3 x 180000", money(quote["nightlySubtotal"]) == 540000.00, quote)
    check("cleaning fee included", money(quote["cleaningFee"]) == 25000.00, quote)
    check("no guest service fee at the seeded 10/0 rate",
          money(quote["guestServiceFee"]) == 0.00, quote)
    check("total adds up", money(quote["total"]) == 565000.00, quote)
    check("host figures are hidden from guests",
          "hostCommission" not in quote and "hostPayout" not in quote, list(quote))
    check("refund schedule is disclosed up front", len(quote["refundSchedule"]) == 2,
          quote.get("refundSchedule"))

    print("\ncalendar overrides")
    peak = check_in + timedelta(days=1)
    status, body = call("PUT", f"/owner/properties/{property_id}/calendar",
                        {"from": str(peak), "to": str(peak), "price": 250000},
                        token=owner_token)
    check("price override saved", status == 200 and body.get("daysUpdated") == 1, body)

    status, quote2 = call("POST", f"/listings/{property_id}/quote",
                          {"checkIn": str(check_in), "checkOut": str(check_out), "guests": 2})
    overridden = [rate for rate in quote2["nightlyRates"] if rate["overridden"]]
    check("the override shows in the quote", len(overridden) == 1
          and money(overridden[0]["amount"]) == 250000.00, quote2["nightlyRates"])
    check("the total reflects the override", money(quote2["nightlySubtotal"]) == 610000.00, quote2)

    status, body = call("DELETE",
                        f"/owner/properties/{property_id}/calendar?from={peak}&to={peak}",
                        token=owner_token)
    check("override cleared", status == 200 and body.get("daysCleared") == 1, body)

    blocked_day = check_in + timedelta(days=1)
    call("PUT", f"/owner/properties/{property_id}/calendar",
         {"from": str(blocked_day), "to": str(blocked_day), "blocked": True}, token=owner_token)
    status, body = call("POST", f"/listings/{property_id}/quote",
                        {"checkIn": str(check_in), "checkOut": str(check_out), "guests": 2})
    check("a blocked night blocks the stay",
          status == 409 and body.get("code") == "dates_unavailable", body)
    status, results = call("GET",
                           f"/listings/search?checkIn={check_in}&checkOut={check_out}")
    check("search excludes a listing with a blocked night",
          not any(row["id"] == property_id for row in results.get("rows", [])), results)
    call("PUT", f"/owner/properties/{property_id}/calendar",
         {"from": str(blocked_day), "to": str(blocked_day), "blocked": False}, token=owner_token)

    print("\nbooking")
    status, body = call("POST", "/bookings", {"propertyId": property_id, "checkIn": str(check_in),
                                              "checkOut": str(check_out), "guests": 2},
                        token=owner_token)
    check("an owner cannot book their own listing",
          status == 400 and body.get("code") == "cannot_book_own_listing", body)

    status, booking = call("POST", "/bookings",
                           {"propertyId": property_id, "checkIn": str(check_in),
                            "checkOut": str(check_out), "guests": 2,
                            "message": "Arriving late evening"}, token=guest_token)
    if not check("booking requested", status == 201, booking):
        return report()
    booking_id = booking["id"]
    check("a non-instant listing waits for the host",
          booking["status"] == "PENDING_HOST_APPROVAL", booking)
    check("the request has a deadline", booking["expiresAt"] is not None, booking)
    check("server-side price was used", money(booking["total"]) == 565000.00, booking)
    check("booking has a support reference", booking["reference"].startswith("SB-"), booking)

    rival_token, _, _ = sign_in(rival_phone)
    status, body = call("POST", "/bookings",
                        {"propertyId": property_id, "checkIn": str(check_in + timedelta(days=1)),
                         "checkOut": str(check_out + timedelta(days=1)), "guests": 2},
                        token=rival_token)
    check("an overlapping booking is refused",
          status == 409 and body.get("code") == "dates_unavailable", body)

    status, results = call("GET", f"/listings/search?checkIn={check_in}&checkOut={check_out}")
    check("a held listing drops out of dated search",
          not any(row["id"] == property_id for row in results.get("rows", [])), results)

    print("\nhost response and payment")
    status, body = call("POST", f"/bookings/{booking_id}/payments", token=guest_token)
    check("cannot pay before the host accepts",
          status == 409 and body.get("code") == "booking_not_awaiting_payment", body)

    status, hosted = call("GET", "/owner/bookings?scope=pending", token=owner_token)
    check("the request appears in the host inbox",
          any(row["id"] == booking_id for row in hosted.get("rows", [])), hosted)
    host_row = next((row for row in hosted.get("rows", []) if row["id"] == booking_id), {})
    check("host sees their payout, not the guest total",
          money(host_row.get("hostPayout")) == 508500.00 and host_row.get("total") is None,
          host_row)
    check("host sees the commission withheld",
          money(host_row.get("hostCommission")) == 56500.00, host_row)

    status, booking = call("POST", f"/owner/bookings/{booking_id}/approve",
                           {"note": "See you then"}, token=owner_token)
    check("host approved", status == 200 and booking["status"] == "PENDING_PAYMENT", booking)

    status, payment = call("POST", f"/bookings/{booking_id}/payments", {"provider": "SIMULATED"},
                           token=guest_token, headers={"Idempotency-Key": f"key-{stamp}"})
    if not check("payment opened", status == 201, payment):
        return report()
    payment_id = payment["id"]
    check("checkout payload carries QR material",
          payment["checkout"].get("qrText") is not None, payment.get("checkout"))
    check("payment amount matches the booking", money(payment["amount"]) == 565000.00, payment)

    status, again = call("POST", f"/bookings/{booking_id}/payments", {"provider": "SIMULATED"},
                         token=guest_token, headers={"Idempotency-Key": f"key-{stamp}"})
    check("the same idempotency key returns the same payment",
          again["id"] == payment_id, again)

    status, body = call("POST", "/payments/callbacks/simulated",
                        raw_body='{"invoiceId":"SIM-forged","status":"PAID"}',
                        headers={"Content-Type": "application/json",
                                 "x-simulated-signature": "deadbeef"})
    check("a callback with a bad signature is rejected",
          status == 401 and body.get("code") == "callback_signature_invalid", body)

    status, settled = call("POST", f"/bookings/{booking_id}/payments/{payment_id}"
                           + "/simulate-settlement", token=guest_token)
    check("payment settled", status == 200 and settled["status"] == "SUCCEEDED", settled)

    status, booking = call("GET", f"/bookings/{booking_id}", token=guest_token)
    check("booking confirmed on settlement",
          booking["status"] == "CONFIRMED" and booking["paymentStatus"] == "PAID", booking)

    status, resettled = call("POST", f"/bookings/{booking_id}/payments/{payment_id}"
                             + "/simulate-settlement", token=guest_token)
    check("re-settling is a harmless no-op", status == 200, resettled)

    print("\ncalendar reflects the booking")
    status, owner_calendar = call("GET", f"/owner/properties/{property_id}/calendar"
                                 + f"?from={check_in}&to={check_out}", token=owner_token)
    booked_days = [day for day in owner_calendar if day["status"] == "BOOKED"]
    check("owner calendar marks the nights BOOKED", len(booked_days) == 3, owner_calendar)
    check("checkout day is free again",
          owner_calendar[-1]["date"] == str(check_out)
          and owner_calendar[-1]["status"] == "AVAILABLE", owner_calendar[-1])

    status, public_calendar = call("GET", f"/listings/{property_id}/availability"
                                   + f"?from={check_in}&to={check_out}")
    check("public calendar hides occupancy as plain BLOCKED",
          all(day["status"] != "BOOKED" for day in public_calendar)
          and sum(1 for day in public_calendar if day["status"] == "BLOCKED") == 3,
          public_calendar)

    print("\nearnings")
    status, earnings = call("GET", f"/owner/earnings/summary?from={check_in - timedelta(days=1)}"
                            + f"&to={check_out + timedelta(days=1)}", token=owner_token)
    check("upcoming earnings counted", status == 200
          and money(earnings["confirmedUpcoming"]) == 508500.00, earnings)
    check("nothing earned yet, since the stay has not happened",
          money(earnings["earnedFromCompletedStays"]) == 0.00, earnings)

    print("\ncancellation and refund")
    status, cancelled = call("POST", f"/bookings/{booking_id}/cancel",
                             {"reason": "Change of plans"}, token=guest_token)
    check("guest cancelled", status == 200
          and cancelled["status"] == "CANCELLED_BY_GUEST", cancelled)
    # 30 days out, MODERATE refunds in full: 610000 accommodation was 565000 here.
    check("full refund this far ahead under MODERATE",
          money(cancelled["refundAmount"]) == 565000.00, cancelled)
    check("payment status reflects the refund",
          cancelled["paymentStatus"] == "REFUNDED", cancelled)

    status, payments = call("GET", f"/bookings/{booking_id}/payments", token=guest_token)
    refunds = [row for row in payments if row["intent"] == "REFUND"]
    check("a refund payment was recorded",
          len(refunds) == 1 and refunds[0]["status"] == "SUCCEEDED", payments)

    status, results = call("GET", f"/listings/search?checkIn={check_in}&checkOut={check_out}")
    check("cancelled dates are bookable again",
          any(row["id"] == property_id for row in results.get("rows", [])), results)

    print("\ninstant book")
    status, _ = call("PATCH", f"/owner/properties/{property_id}",
                     {"instantBook": True}, token=owner_token)
    status, instant = call("POST", "/bookings",
                           {"propertyId": property_id, "checkIn": str(check_in),
                            "checkOut": str(check_out), "guests": 2}, token=rival_token)
    check("instant-book skips host approval",
          status == 201 and instant["status"] == "PENDING_PAYMENT", instant)

    print("\ncommission configuration")
    status, rules = call("GET", "/admin/commission-rules", token=admin_token)
    check("the seeded global rule is 10/0", status == 200
          and any(money(rule["hostFeePercent"]) == 10.00
                  and money(rule["guestFeePercent"]) == 0.00 for rule in rules), rules)
    status, new_rule = call("POST", "/admin/commission-rules",
                            {"scope": "GLOBAL", "hostFeePercent": 12, "guestFeePercent": 3,
                             "note": "smoke test"}, token=admin_token)
    check("a new rate supersedes the old one", status == 201, new_rule)
    status, quote3 = call("POST", f"/listings/{property_id}/quote",
                          {"checkIn": str(check_in + timedelta(days=60)),
                           "checkOut": str(check_in + timedelta(days=63)), "guests": 2})
    check("the new guest fee applies to new quotes",
          money(quote3["guestServiceFee"]) == 16950.00, quote3)
    # Put it back so a re-run starts from the documented rate.
    call("POST", "/admin/commission-rules",
         {"scope": "GLOBAL", "hostFeePercent": 10, "guestFeePercent": 0,
          "note": "restore launch rate"}, token=admin_token)

    retire(admin_token, listings=[property_id])
    return report()


def report():
    print()
    if failures:
        print(f"\033[31m{len(failures)} check(s) failed:\033[0m")
        for failure in failures:
            print("  - " + failure)
        return 1
    print("\033[32mAll checks passed.\033[0m")
    return 0


if __name__ == "__main__":
    sys.exit(main())
