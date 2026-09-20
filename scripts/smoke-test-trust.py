#!/usr/bin/env python3
"""End-to-end check of trust and safety against a running stack.

Proves the four things that make a fake listing unprofitable: the host's money
waits behind a hold, a stolen photograph is spotted and blocks approval, an
unverified host is not paid, and a guest report freezes everything owed.

Development only: reads OTP codes from the backend log and uses the simulated
payment provider.

    python3 scripts/smoke-test-trust.py --log /tmp/backend.log
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


def call(method, path, body=None, token=None, headers=None):
    """@return (status_code, parsed_body_or_None)"""
    request = urllib.request.Request(API + path, method=method)
    if headers:
        for name, value in headers.items():
            request.add_header(name, value)
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


def sign_in(phone):
    """@return (access_token, refresh_token, user_id)"""
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


def upload_photo(token, presign_path, confirm_path, image):
    status, presigned = call("POST", presign_path,
                             {"contentType": "image/png", "sizeBytes": len(image)}, token=token)
    if status != 200:
        return status, presigned
    put = urllib.request.Request(presigned["uploadUrl"], method="PUT", data=image)
    put.add_header("Content-Type", "image/png")
    with urllib.request.urlopen(put, timeout=30) as response:
        if response.status not in (200, 204):
            raise SystemExit(f"presigned PUT failed: {response.status}")
    return call("POST", confirm_path,
                {"storageKey": presigned["storageKey"], "altText": "Listing photo"}, token=token)


def publish_house(admin_token, owner_token, owner_refresh, title, image, approve=True):
    """A complete, approved listing. @return its id."""
    status, listing = call("POST", "/owner/properties",
                           {"title": title, "propertyType": "GER", "city": "Ulaanbaatar",
                            "maxGuests": 4}, token=owner_token)
    if status != 201:
        raise SystemExit(f"could not create {title}: {status} {listing}")
    listing_id = listing["id"]
    call("PATCH", f"/owner/properties/{listing_id}", {
        "description": "A ger by the river, for trust tests.",
        "addressLine": "Gachuurt valley", "district": "Bayanzurkh",
        "latitude": 47.9405, "longitude": 107.1210, "basePrice": 180000,
        "minStayNights": 1, "instantBook": True,
        "amenities": ["STOVE_HEATING"]}, token=owner_token)
    upload_photo(owner_token, f"/owner/properties/{listing_id}/photos/upload-url",
                 f"/owner/properties/{listing_id}/photos", image)
    call("POST", f"/owner/properties/{listing_id}/submit", token=owner_token)
    if approve:
        call("PATCH", f"/admin/properties/{listing_id}/status", {"status": "APPROVED"},
             token=admin_token)
    return listing_id



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
    honest_phone = "+9768" + stamp + "1"
    thief_phone = "+9768" + stamp + "2"
    guest_phone = "+9768" + stamp + "3"

    check_in = date.today()
    check_out = check_in + timedelta(days=2)

    print("\nsetting up")
    status, admin_session = call("POST", "/auth/login",
                                 {"email": args.admin_email, "password": args.admin_password})
    if not check("admin signed in", status == 200, admin_session):
        return report()
    admin_token = admin_session["accessToken"]

    honest_token, honest_refresh, honest_id = sign_in(honest_phone)
    call("POST", f"/admin/users/{honest_id}/roles", {"role": "HOUSE_OWNER"}, token=admin_token)
    honest_token, honest_refresh = refresh(honest_refresh)

    thief_token, thief_refresh, thief_id = sign_in(thief_phone)
    call("POST", f"/admin/users/{thief_id}/roles", {"role": "HOUSE_OWNER"}, token=admin_token)
    thief_token, thief_refresh = refresh(thief_refresh)

    guest_token, _, guest_id = sign_in(guest_phone)
    call("PATCH", "/users/me", {"fullName": "Trust Test Guest"}, token=guest_token)

    # One photograph, used by the honest host first and stolen afterwards.
    photo = png(int(stamp))
    real_listing = publish_house(admin_token, honest_token, honest_refresh,
                                 f"Honest ger {stamp}", photo)
    check("an honest listing is published", real_listing is not None)

    print("\na stolen photograph")
    # The same image, published by a second account: the case the fingerprinter exists for.
    status, stolen = call("POST", "/owner/properties",
                          {"title": f"Stolen ger {stamp}", "propertyType": "GER",
                           "city": "Ulaanbaatar", "maxGuests": 4}, token=thief_token)
    stolen_id = stolen["id"]
    call("PATCH", f"/owner/properties/{stolen_id}", {
        "description": "Not actually my house.", "addressLine": "Somewhere",
        "district": "Bayanzurkh", "latitude": 47.94, "longitude": 107.12,
        "basePrice": 90000, "minStayNights": 1}, token=thief_token)
    status, confirmed = upload_photo(thief_token,
                                     f"/owner/properties/{stolen_id}/photos/upload-url",
                                     f"/owner/properties/{stolen_id}/photos", photo)
    check("the copied photo still uploads", status == 201, confirmed)

    status, flags = call("GET", "/admin/flags?status=OPEN&size=50", token=admin_token)
    raised = [row for row in flags.get("rows", [])
              if row["supplyId"] == stolen_id and row["type"] == "DUPLICATE_PHOTO"]
    check("it raised a duplicate-photo flag", len(raised) == 1, flags)
    check("with the matching photo as evidence",
          raised and "matches" in (raised[0]["details"] or {}), raised)

    call("POST", f"/owner/properties/{stolen_id}/submit", token=thief_token)
    status, refused = call("PATCH", f"/admin/properties/{stolen_id}/status",
                           {"status": "APPROVED"}, token=admin_token)
    check("a flagged listing cannot be approved",
          status == 409 and refused.get("code") == "listing_flagged", refused)

    print("\nthe payout hold")
    status, booking = call("POST", "/bookings", {
        "propertyId": real_listing, "checkIn": str(check_in), "checkOut": str(check_out),
        "guests": 2}, token=guest_token)
    if not check("a guest books the honest listing", status == 201, booking):
        return report()
    booking_id = booking["id"]

    status, payment = call("POST", f"/bookings/{booking_id}/payments", {"provider": "SIMULATED"},
                           token=guest_token, headers={"Idempotency-Key": f"trust-{stamp}"})
    call("POST", f"/bookings/{booking_id}/payments/{payment['id']}/simulate-settlement",
         token=guest_token)

    status, payouts = call("GET", "/owner/payouts", token=honest_token)
    mine = [row for row in payouts.get("rows", []) if row["bookingId"] == booking_id]
    check("settling the payment schedules a payout", len(mine) == 1, payouts)
    check("which is pending, not paid", mine and mine[0]["status"] == "PENDING", mine)
    check("and the host can see when it releases",
          mine and mine[0].get("releaseAfter") is not None, mine)
    # Null fields are omitted from the JSON entirely, so absent means "not paid".
    check("the guest's money is not sitting with the host",
          mine and mine[0].get("paidAt") is None, mine)

    status, admin_payouts = call("GET", "/admin/payouts?status=PENDING&size=50",
                                 token=admin_token)
    check("the admin console sees it too",
          any(row["bookingId"] == booking_id for row in admin_payouts.get("rows", [])),
          admin_payouts)

    print("\nidentity")
    # The host has not verified, so even a completed stay must not pay out. The
    # release rules are exercised by the integration tests; here we check the
    # account state the rules read, and that documents can actually be filed.
    status, submitted = call("POST", "/users/me/kyc", {
        "documentType": "NATIONAL_ID", "documentNumber": "AA" + stamp,
        "fullName": "Honest Host", "documentImageKey": "kyc/" + stamp + "/id.jpg",
    }, token=honest_token)
    check("a host can submit identity documents", status == 201, submitted)
    check("which start as pending", submitted and submitted["status"] == "PENDING", submitted)

    status, again = call("POST", "/users/me/kyc", {
        "documentType": "NATIONAL_ID", "documentNumber": "AA" + stamp,
        "fullName": "Honest Host", "documentImageKey": "kyc/" + stamp + "/id.jpg",
    }, token=honest_token)
    check("a second submission is refused while one is pending",
          again is not None and status == 409, again)

    status, queue = call("GET", "/admin/kyc?status=PENDING&size=50", token=admin_token)
    check("it reaches the admin queue",
          any(row["userId"] == honest_id for row in queue.get("rows", [])), queue)

    status, reviewed = call("POST", f"/admin/kyc/{submitted['id']}/review",
                            {"outcome": "VERIFIED", "note": "Documents match"},
                            token=admin_token)
    check("an admin can verify them", status == 200 and reviewed["status"] == "VERIFIED",
          reviewed)

    status, me = call("GET", "/auth/me", token=honest_token)
    check("and the account is verified afterwards", me.get("kycStatus") == "VERIFIED", me)

    print("\na guest reports a listing")
    status, report_body = call("POST", f"/listings/{real_listing}/report", {
        "reason": "not_as_described", "details": "There is no ger at this address.",
        "bookingId": booking_id}, token=guest_token)
    check("the report is accepted", status == 201, report_body)

    status, payouts = call("GET", "/owner/payouts", token=honest_token)
    mine = [row for row in payouts.get("rows", []) if row["bookingId"] == booking_id]
    check("it freezes the money owed on that listing",
          mine and mine[0]["status"] == "BLOCKED", mine)
    check("and says why", mine and mine[0].get("blockedReason") == "listing_flagged", mine)

    status, anonymous = call("POST", f"/listings/{real_listing}/report",
                             {"reason": "spite"})
    check("an anonymous report is refused", anonymous is not None and status == 401, anonymous)

    print("\nresolving")
    status, flags = call("GET", "/admin/flags?status=OPEN&size=50", token=admin_token)
    report_flag = next((row for row in flags.get("rows", [])
                        if row["supplyId"] == real_listing and row["type"] == "GUEST_REPORT"),
                       None)
    check("the report is in the review queue", report_flag is not None, flags)

    status, dismissed = call("POST", f"/admin/flags/{report_flag['id']}/dismiss",
                             {"note": "Guest had the wrong address"}, token=admin_token)
    check("an admin can dismiss it", status == 200 and dismissed["status"] == "DISMISSED",
          dismissed)

    status, payouts = call("GET", "/owner/payouts", token=honest_token)
    mine = [row for row in payouts.get("rows", []) if row["bookingId"] == booking_id]
    check("dismissing returns the payout to waiting",
          mine and mine[0]["status"] == "PENDING", mine)
    check("it is not released just because the flag cleared",
          mine and mine[0].get("releasedAt") is None, mine)

    status, upheld = call("POST", f"/admin/flags/{raised[0]['id']}/uphold",
                          {"note": "Photo belongs to another listing"}, token=admin_token)
    check("and can uphold the duplicate-photo flag",
          status == 200 and upheld["status"] == "UPHELD", upheld)

    print("\nwho may see what")
    status, denied = call("GET", "/admin/payouts", token=honest_token)
    check("a host cannot read the platform's payout ledger", status == 403, denied)
    status, denied = call("GET", "/admin/flags", token=guest_token)
    check("nor the review queue", status == 403, denied)
    status, other = call("GET", "/owner/payouts", token=thief_token)
    check("a host sees only their own payouts",
          all(row["bookingId"] != booking_id for row in other.get("rows", [])), other)

    retire(admin_token, listings=[real_listing, stolen_id])
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
