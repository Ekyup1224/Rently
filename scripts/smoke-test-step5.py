#!/usr/bin/env python3
"""End-to-end check of reviews, messaging and payout runs against a running stack.

Proves the four promises Step 5 makes: a review stays blind until both sides have
written, a booking's thread is private to its two people, a host asking to be paid
directly is caught and their money frozen, and everything released can be turned
into one bank file per host without paying anybody twice.

Development only: reads OTP codes from the backend log and uses the simulated
payment provider.

    python3 scripts/smoke-test-step5.py --log /tmp/backend.log
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
API = "http://localhost:8080/api/v1"
LOG_PATH = None


def call(method, path, body=None, token=None):
    """@return (status_code, parsed_body_or_None)"""
    request = urllib.request.Request(API + path, method=method)
    if token:
        request.add_header("Authorization", "Bearer " + token)
    data = None
    if body is not None:
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


def sign_in(phone, name=None):
    """@return (access_token, refresh_token, user_id)"""
    offset = log_size()
    status, body = call("POST", "/auth/otp/request", {"phone": phone})
    if status != 200:
        raise SystemExit(f"otp/request failed for {phone}: {status} {body}")
    status, session = call("POST", "/auth/otp/verify",
                           {"phone": phone, "code": latest_code(offset)})
    if status != 200:
        raise SystemExit(f"otp/verify failed for {phone}: {status} {session}")
    token = session["accessToken"]
    if name:
        call("PATCH", "/users/me", {"fullName": name}, token=token)
    return token, session["refreshToken"], session["user"]["id"]


def refresh(refresh_token):
    status, session = call("POST", "/auth/refresh", {"refreshToken": refresh_token})
    if status != 200:
        raise SystemExit(f"refresh failed: {status} {session}")
    return session["accessToken"], session["refreshToken"]


def png(seed, width=200, height=150):
    """A textured image whose *coarse* structure is unique per seed.

    Two constraints pull against each other. It needs fine texture, because the
    fingerprint compares each pixel with its neighbour and a flat image hashes to
    all zeros whatever its colour. But fine texture alone is not enough: the hash
    downsamples to 9x8 before comparing, so a fixed pattern with only the colours
    varying produces near-identical hashes and every run's listing is flagged as a
    duplicate of the last run's. So the large-scale shape — a seeded diagonal wave
    and a blob — varies too, which is what the hash actually sees.
    """
    random_state = seed
    phase = (seed % 17) + 3
    blob_x, blob_y = 20 + seed % 140, 15 + (seed // 7) % 110
    rows = []
    for y in range(height):
        row = bytearray()
        for x in range(width):
            random_state = (random_state * 1103515245 + 12345) & 0x7FFFFFFF
            grain = (random_state >> 16) & 0x3F
            # Large-scale structure: this is the part the 9x8 hash keeps.
            wave = ((x * phase + y * (seed % 11 + 2)) // 13) % 2
            in_blob = (x - blob_x) ** 2 + (y - blob_y) ** 2 < 900
            if in_blob:
                row += bytes((230 - grain, 200 - grain, 60 + grain))
            elif wave:
                row += bytes((30 + grain, 70 + grain, 150 + grain))
            else:
                row += bytes((190 + grain // 2, 150 + grain, 90 + grain))
        rows.append(bytes(row))
    raw = b"".join(b"\x00" + row for row in rows)

    def chunk(kind, data):
        return (struct.pack(">I", len(data)) + kind + data
                + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF))

    return (b"\x89PNG\r\n\x1a\n"
            + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw, 6)) + chunk(b"IEND", b""))


def upload_photo(token, listing_id, image):
    status, presigned = call(
        "POST", f"/owner/properties/{listing_id}/photos/upload-url",
        {"contentType": "image/png", "sizeBytes": len(image)}, token=token)
    if status != 200:
        return status, presigned
    put = urllib.request.Request(presigned["uploadUrl"], method="PUT", data=image)
    put.add_header("Content-Type", "image/png")
    with urllib.request.urlopen(put, timeout=30) as response:
        if response.status not in (200, 204):
            raise SystemExit(f"presigned PUT failed: {response.status}")
    return call("POST", f"/owner/properties/{listing_id}/photos",
                {"storageKey": presigned["storageKey"], "altText": "Listing photo"},
                token=token)


def publish_house(admin_token, owner_token, title, image):
    """A complete, approved, instantly bookable listing. @return its id."""
    status, listing = call("POST", "/owner/properties",
                           {"title": title, "propertyType": "GER", "city": "Ulaanbaatar",
                            "maxGuests": 4}, token=owner_token)
    if status != 201:
        raise SystemExit(f"could not create {title}: {status} {listing}")
    listing_id = listing["id"]
    call("PATCH", f"/owner/properties/{listing_id}", {
        "description": "A ger by the river, for step 5 tests.",
        "addressLine": "Gachuurt valley", "district": "Bayanzurkh",
        "latitude": 47.9405, "longitude": 107.1210, "basePrice": 180000,
        "minStayNights": 1, "instantBook": True,
        "amenities": ["STOVE_HEATING"]}, token=owner_token)
    upload_photo(owner_token, listing_id, image)
    call("POST", f"/owner/properties/{listing_id}/submit", token=owner_token)
    call("PATCH", f"/admin/properties/{listing_id}/status", {"status": "APPROVED"},
         token=admin_token)
    return listing_id


def pay_for(guest_token, booking_id):
    """Takes a booking through the simulated gateway to CONFIRMED."""
    status, payment = call("POST", f"/bookings/{booking_id}/payments",
                           {"provider": "SIMULATED"}, token=guest_token)
    if status != 201:
        raise SystemExit(f"could not start payment: {status} {payment}")
    status, settled = call(
        "POST", f"/bookings/{booking_id}/payments/{payment['id']}/simulate-settlement",
        token=guest_token)
    if status != 200:
        raise SystemExit(f"could not settle: {status} {settled}")



def retire(admin_token, hotels=(), listings=()):
    """Takes this run's supply back off sale.

    A smoke test that publishes a listing and walks away leaves it in live
    search, so after a dozen runs the demo front page is a wall of "Blue Sky
    Hotel 305243". Suspending rather than deleting, because bookings and payouts
    reference these rows — the same reason the product suspends rather than
    deletes.
    """
    for hotel_id in hotels:
        call("PATCH", f"/admin/hotels/{hotel_id}/status",
             {"status": "SUSPENDED", "reason": "Smoke test fixture"}, token=admin_token)
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
    host_phone = "+9767" + stamp + "1"
    guest_phone = "+9767" + stamp + "2"
    stranger_phone = "+9767" + stamp + "3"

    print("\nsetting up")
    status, admin_session = call("POST", "/auth/login",
                                 {"email": args.admin_email, "password": args.admin_password})
    if not check("admin signed in", status == 200, admin_session):
        return report()
    admin_token = admin_session["accessToken"]

    host_token, host_refresh, host_id = sign_in(host_phone, "Bat Erdene")
    call("POST", f"/admin/users/{host_id}/roles", {"role": "HOUSE_OWNER"}, token=admin_token)
    host_token, host_refresh = refresh(host_refresh)

    guest_token, _, guest_id = sign_in(guest_phone, "Saraa Dorj")
    stranger_token, _, _ = sign_in(stranger_phone, "Passing By")

    listing_id = publish_house(admin_token, host_token, f"Step 5 ger {stamp}", png(int(stamp)))
    check("a listing is published", listing_id is not None)

    # Today, so it can be checked in and out within the run.
    check_in = date.today()
    check_out = check_in + timedelta(days=1)
    status, booking = call("POST", "/bookings",
                           {"propertyId": listing_id, "checkIn": check_in.isoformat(),
                            "checkOut": check_out.isoformat(), "guests": 2},
                           token=guest_token)
    if not check("a stay is booked", status == 201, booking):
        return report()
    booking_id = booking["id"]
    pay_for(guest_token, booking_id)

    print("\nmessaging")
    status, thread = call("POST", f"/conversations/for-booking/{booking_id}",
                          token=guest_token)
    check("the guest opens the booking's thread", status == 201, thread)
    conversation_id = thread["id"]

    status, again = call("POST", f"/conversations/for-booking/{booking_id}", token=host_token)
    check("the host opens the same thread, not a second one",
          status == 201 and again["id"] == conversation_id, again)

    status, denied = call("GET", f"/conversations/{conversation_id}/messages",
                          token=stranger_token)
    check("a stranger cannot read it", status in (403, 404), denied)

    status, sent = call("POST", f"/conversations/{conversation_id}/messages",
                        {"body": "Сайн байна уу, бид 18 цагт ирнэ."}, token=guest_token)
    check("an ordinary message goes through untouched",
          status == 201 and sent.get("flaggedReason") is None, sent)

    status, flagged = call("POST", f"/conversations/{conversation_id}/messages",
                           {"body": "Хаанбанк 5301234567 руу шилжүүлээрэй, хямд болно"},
                           token=host_token)
    check("a host asking for a bank transfer is flagged",
          status == 201 and flagged.get("flaggedReason") is not None, flagged)

    status, seen = call("GET", f"/conversations/{conversation_id}/messages", token=guest_token)
    theirs = [row for row in seen.get("rows", []) if row["id"] == flagged["id"]]
    check("the guest still sees the message itself", len(theirs) == 1, seen)
    check("but is not told it was flagged",
          theirs and theirs[0].get("flaggedReason") is None, theirs)

    status, payouts = call("GET", "/owner/payouts", token=host_token)
    mine = [row for row in payouts.get("rows", []) if row["bookingId"] == booking_id]
    check("and the host's money is frozen",
          mine and mine[0]["status"] == "BLOCKED", mine)

    status, queue = call("GET", "/admin/messages/flagged?size=50", token=admin_token)
    check("it reaches the moderation queue",
          any(row["id"] == flagged["id"] for row in queue.get("rows", [])), queue)

    status, denied = call("GET", "/admin/messages/flagged", token=host_token)
    check("which a host cannot read", status == 403, denied)

    print("\nclearing the flag so the stay can finish")
    status, flags = call("GET", "/admin/flags?status=OPEN&size=50", token=admin_token)
    listing_flags = [row for row in flags.get("rows", []) if row["supplyId"] == listing_id]
    check("the listing carries the flag", len(listing_flags) >= 1, flags)
    for flag in listing_flags:
        call("POST", f"/admin/flags/{flag['id']}/dismiss",
             {"note": "Smoke test: host warned"}, token=admin_token)

    print("\nreviews")
    status, too_early = call("POST", f"/bookings/{booking_id}/review",
                             {"rating": 5, "comment": "Lovely"}, token=guest_token)
    check("a stay that is not over cannot be reviewed",
          status == 409 and too_early.get("code") == "stay_not_finished", too_early)

    call("POST", f"/owner/bookings/{booking_id}/check-in", token=host_token)
    status, done = call("POST", f"/owner/bookings/{booking_id}/check-out", token=host_token)
    check("the stay is checked in and out", status == 200, done)

    status, guest_review = call("POST", f"/bookings/{booking_id}/review",
                                {"rating": 5,
                                 "subRatings": {"cleanliness": 5, "location": 4},
                                 "comment": "Warm and spotless."}, token=guest_token)
    check("the guest writes a review", status == 201, guest_review)
    check("which is not visible yet", guest_review.get("visible") is False, guest_review)

    status, public = call("GET", f"/listings/{listing_id}/reviews")
    check("so it is absent from the listing page", public.get("total") == 0, public)

    status, twice = call("POST", f"/bookings/{booking_id}/review",
                         {"rating": 1, "comment": "Actually, no."}, token=guest_token)
    check("nobody reviews the same stay twice",
          status == 409 and twice.get("code") == "already_reviewed", twice)

    status, outsider = call("POST", f"/bookings/{booking_id}/review",
                            {"rating": 1, "comment": "Never went."}, token=stranger_token)
    check("nor can someone who was not there", status == 404, outsider)

    status, host_review = call("POST", f"/bookings/{booking_id}/review",
                               {"rating": 4, "comment": "Tidy guests."}, token=host_token)
    check("the host writes theirs", status == 201, host_review)

    status, both = call("GET", f"/bookings/{booking_id}/reviews", token=guest_token)
    check("and both become visible at once",
          len(both) == 2 and all(row["visible"] for row in both), both)

    status, public = call("GET", f"/listings/{listing_id}/reviews")
    check("the guest's review is now on the listing", public.get("total") == 1, public)
    rows = public.get("rows", [])
    check("attributed to a first name only, never a phone number",
          rows and rows[0]["authorName"] == "Saraa"
          and "phone" not in json.dumps(rows[0]).lower(), rows)
    check("the host's review of the guest stays off the listing",
          all(row["subject"] == "SUPPLY" for row in rows), rows)

    status, listing = call("GET", f"/listings/{listing_id}")
    check("and the listing's rating reflects it",
          listing.get("ratingCount") == 1 and float(listing.get("ratingAverage")) == 5.0,
          {"average": listing.get("ratingAverage"), "count": listing.get("ratingCount")})

    status, responded = call("POST", f"/reviews/{guest_review['id']}/response",
                             {"body": "Thank you, come again."}, token=host_token)
    check("the host can reply to it once", status == 200, responded)
    status, twice = call("POST", f"/reviews/{guest_review['id']}/response",
                         {"body": "Again."}, token=host_token)
    check("but not twice", twice.get("code") == "already_responded", twice)

    print("\nmoderation")
    status, queue = call("GET", "/admin/reviews?size=50", token=admin_token)
    check("reviews reach the admin queue",
          any(row["id"] == guest_review["id"] for row in queue.get("rows", [])), queue)

    status, hidden = call("POST", f"/admin/reviews/{guest_review['id']}/hide",
                          {"reason": "Smoke test"}, token=admin_token)
    check("an admin can hide one", status == 200 and hidden["visible"] is False, hidden)
    status, public = call("GET", f"/listings/{listing_id}/reviews")
    check("which removes it from the listing", public.get("total") == 0, public)

    status, listing = call("GET", f"/listings/{listing_id}")
    check("and takes it back out of the rating",
          listing.get("ratingCount") == 0, listing.get("ratingCount"))

    status, restored = call("POST", f"/admin/reviews/{guest_review['id']}/restore",
                            token=admin_token)
    check("and restore it", status == 200 and restored["visible"] is True, restored)

    print("\npayout runs")
    # The stay started and the flag is cleared; identity is the last condition.
    call("POST", f"/admin/users/{host_id}/kyc", {"status": "VERIFIED"}, token=admin_token)
    status, swept = call("POST", "/admin/payouts/release-due", token=admin_token)
    check("the sweep runs", status == 200, swept)

    status, payouts = call("GET", "/owner/payouts", token=host_token)
    mine = [row for row in payouts.get("rows", []) if row["bookingId"] == booking_id]
    # The hold is 24 hours from check-in, so a stay that started today is not due
    # yet however clean it is. That the sweep leaves it alone is the point of it.
    check("but leaves a payout still inside its 24-hour hold alone",
          mine and mine[0]["status"] == "PENDING", mine)
    payout_id = mine[0]["id"] if mine else None

    status, released = call("POST", f"/admin/payouts/{payout_id}/release",
                            {"note": "Smoke test: bringing the hold forward"},
                            token=admin_token)
    check("an admin can release it by hand", status == 200, released)

    status, payouts = call("GET", "/owner/payouts", token=host_token)
    mine = [row for row in payouts.get("rows", []) if row["bookingId"] == booking_id]
    if not check("the host's payout is released",
                 mine and mine[0]["status"] == "RELEASED", mine):
        return report()
    owed = mine[0]["amount"]

    status, batch = call("POST", "/admin/payout-batches", token=admin_token)
    check("a run is assembled", status == 200 and batch["payoutCount"] >= 1, batch)
    batch_id = batch["id"]

    status, lines = call("GET", f"/admin/payout-batches/{batch_id}/lines", token=admin_token)
    ours = [row for row in lines if row["payeeName"] == "Bat Erdene"]
    check("with one line for our host", len(ours) == 1, lines)
    check("for the amount they were owed", ours and ours[0]["amount"] == owed, ours)
    check("naming the stays behind it",
          ours and booking["reference"] in ours[0]["bookings"], ours)

    status, empty = call("POST", "/admin/payout-batches", token=admin_token)
    check("a second run finds nothing left to pay",
          status == 409 and empty.get("code") == "nothing_to_pay", empty)

    status, exported = call("POST", f"/admin/payout-batches/{batch_id}/exported",
                            token=admin_token)
    check("the run can be marked sent", status == 200 and exported["status"] == "EXPORTED",
          exported)
    status, twice = call("POST", f"/admin/payout-batches/{batch_id}/exported",
                         token=admin_token)
    check("but only once",
          status == 409 and twice.get("code") == "batch_already_exported", twice)

    status, settled = call("POST", f"/admin/payout-batches/{batch_id}/settled",
                           {"providerRef": f"SMOKE-{stamp}"}, token=admin_token)
    check("and settled", status == 200 and settled["status"] == "SETTLED", settled)

    status, payouts = call("GET", "/owner/payouts", token=host_token)
    mine = [row for row in payouts.get("rows", []) if row["bookingId"] == booking_id]
    check("which marks the host's payout paid",
          mine and mine[0]["status"] == "PAID", mine)
    check("against the bank's reference",
          mine and mine[0].get("providerRef") == f"SMOKE-{stamp}", mine)

    status, twice = call("POST", f"/admin/payout-batches/{batch_id}/settled",
                         {"providerRef": "SMOKE-AGAIN"}, token=admin_token)
    check("a settled run cannot be settled again",
          status == 409 and twice.get("code") == "batch_already_settled", twice)

    print("\nwho may see what")
    status, denied = call("GET", "/admin/payout-batches", token=host_token)
    check("a host cannot read the payout runs", status == 403, denied)
    status, denied = call("GET", "/admin/reviews", token=guest_token)
    check("a guest cannot read the review queue", status == 403, denied)
    status, denied = call("POST", f"/admin/reviews/{guest_review['id']}/hide",
                          {"reason": "no"}, token=host_token)
    check("nor can a host hide a review about them", status == 403, denied)

    retire(admin_token, listings=[listing_id])
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
