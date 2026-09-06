#!/usr/bin/env python3
"""Seeds demo data: a verified house owner with live listings, and a guest.

Development only — it reads OTP codes from the backend log and uses the simulated
payment provider. Prints the phone numbers to sign in with; codes appear in the
backend log as ``[DEV SMS] ... body=NNNNNN``.

    python3 scripts/seed-demo.py --log /tmp/backend.log
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

API = "http://localhost:8080/api/v1"
OWNER_PHONE = "+97611000001"
GUEST_PHONE = "+97611000002"
LOG_PATH = None

LISTINGS = [
    {
        "create": {"title": "Ger camp on the Tuul river", "propertyType": "GER",
                   "city": "Ulaanbaatar", "maxGuests": 4},
        "detail": {
            "description": "A traditional ger with a wood stove, 40 km from the city. "
                           "Wake up to the river and horses grazing outside.",
            "addressLine": "Gachuurt valley, plot 14", "district": "Bayanzurkh",
            "latitude": 47.9405, "longitude": 107.1210,
            "basePrice": 180000, "cleaningFee": 25000, "minStayNights": 2,
            "cancellationPolicy": "MODERATE", "instantBook": False,
            "amenities": ["STOVE_HEATING", "PARKING_FREE", "MOUNTAIN_VIEW", "RIVER_VIEW",
                          "BBQ_GRILL", "FIRST_AID_KIT"],
            "houseRules": "No shoes inside the ger. Please keep the stove damper open at night.",
        },
        "colour": (140, 120, 90),
    },
    {
        "create": {"title": "Sunny flat by Sukhbaatar Square", "propertyType": "APARTMENT",
                   "city": "Ulaanbaatar", "maxGuests": 3},
        "detail": {
            "description": "Bright two-room apartment a five-minute walk from the square. "
                           "Fast wifi, a proper desk, and a supermarket downstairs.",
            "addressLine": "Peace Avenue 22, apt 41", "district": "Chingeltei",
            "latitude": 47.9188, "longitude": 106.9176,
            "basePrice": 145000, "cleaningFee": 15000, "minStayNights": 1,
            "cancellationPolicy": "FLEXIBLE", "instantBook": True,
            "amenities": ["WIFI", "KITCHEN", "WASHER", "TV", "WORKSPACE", "HEATING",
                          "ELEVATOR", "HOT_WATER", "SELF_CHECK_IN"],
        },
        "colour": (90, 110, 150),
    },
    {
        "create": {"title": "Cabin near Terelj national park", "propertyType": "CABIN",
                   "city": "Nalaikh", "maxGuests": 6},
        "detail": {
            "description": "Timber cabin at the edge of the park, with a sauna and "
                           "space for six. Best reached by car.",
            "addressLine": "Terelj road km 12", "district": "Terelj",
            "latitude": 47.9891, "longitude": 107.4620,
            "basePrice": 320000, "cleaningFee": 40000, "minStayNights": 2,
            "cancellationPolicy": "STRICT", "instantBook": True,
            "amenities": ["SAUNA", "FIREPLACE", "KITCHEN", "PARKING_FREE", "MOUNTAIN_VIEW",
                          "BBQ_GRILL", "PETS_ALLOWED", "SMOKE_ALARM"],
        },
        "colour": (110, 130, 100),
    },
]


def call(method, path, body=None, token=None, headers=None):
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
    raise SystemExit("No dev SMS code in the log; is app.otp.delivery=log?")


def sign_in(phone):
    offset = log_size()
    status, body = call("POST", "/auth/otp/request", {"phone": phone})
    if status == 429:
        raise SystemExit(f"{phone} is on an OTP cooldown; wait a minute and retry.")
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
    return session["accessToken"]


def photo_bytes(colour, width=800, height=600):
    """A plain PNG in the listing's colour, so galleries have something in them."""
    rows = []
    for y in range(height):
        row = bytearray()
        shade = 1.0 - (y / height) * 0.35
        pixel = bytes(int(channel * shade) for channel in colour)
        row += pixel * width
        rows.append(bytes(row))
    raw = b"".join(b"\x00" + row for row in rows)

    def chunk(kind, data):
        return (struct.pack(">I", len(data)) + kind + data
                + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF))

    return (b"\x89PNG\r\n\x1a\n"
            + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw, 6)) + chunk(b"IEND", b""))


def upload_photo(token, property_id, colour):
    image = photo_bytes(colour)
    status, presigned = call("POST", f"/owner/properties/{property_id}/photos/upload-url",
                             {"contentType": "image/png", "sizeBytes": len(image)}, token=token)
    if status != 200:
        raise SystemExit(f"presign failed: {status} {presigned}")
    put = urllib.request.Request(presigned["uploadUrl"], method="PUT", data=image)
    put.add_header("Content-Type", "image/png")
    with urllib.request.urlopen(put, timeout=30) as response:
        if response.status not in (200, 204):
            raise SystemExit(f"upload failed: {response.status}")
    status, photo = call("POST", f"/owner/properties/{property_id}/photos",
                         {"storageKey": presigned["storageKey"], "altText": "Listing photo"},
                         token=token)
    if status != 201:
        raise SystemExit(f"photo confirm failed: {status} {photo}")


def main():
    global LOG_PATH
    parser = argparse.ArgumentParser()
    parser.add_argument("--log", required=True)
    parser.add_argument("--admin-email", default="admin@stay.local")
    parser.add_argument("--admin-password", default="ChangeMe123!")
    args = parser.parse_args()
    LOG_PATH = args.log

    status, admin_session = call("POST", "/auth/login",
                                 {"email": args.admin_email, "password": args.admin_password})
    if status != 200:
        raise SystemExit(f"admin sign-in failed: {status} {admin_session}")
    admin_token = admin_session["accessToken"]
    print("signed in as admin")

    owner_token, owner_refresh, owner_id = sign_in(OWNER_PHONE)
    call("POST", f"/admin/users/{owner_id}/roles", {"role": "HOUSE_OWNER"}, token=admin_token)
    # Role claims live in the access token, so refresh to pick up the grant.
    owner_token = refresh(owner_refresh)
    call("PATCH", "/users/me", {"fullName": "Bat Erdene", "locale": "mn"}, token=owner_token)
    print(f"owner ready: {OWNER_PHONE}")

    guest_token, _, _ = sign_in(GUEST_PHONE)
    call("PATCH", "/users/me", {"fullName": "Sarnai Dorj"}, token=guest_token)
    print(f"guest ready: {GUEST_PHONE}")

    check_in = date.today() + timedelta(days=21)
    for spec in LISTINGS:
        status, listing = call("POST", "/owner/properties", spec["create"], token=owner_token)
        if status != 201:
            print(f"  skipped {spec['create']['title']}: {status} {listing}")
            continue
        property_id = listing["id"]
        call("PATCH", f"/owner/properties/{property_id}", spec["detail"], token=owner_token)
        upload_photo(owner_token, property_id, spec["colour"])
        upload_photo(owner_token, property_id,
                     tuple(min(255, channel + 40) for channel in spec["colour"]))

        # A weekend premium, so the calendar has real overrides to look at.
        friday = check_in + timedelta(days=(4 - check_in.weekday()) % 7)
        call("PUT", f"/owner/properties/{property_id}/calendar",
             {"from": str(friday), "to": str(friday + timedelta(days=30)),
              "weekdays": ["FRIDAY", "SATURDAY"],
              "price": round(spec["detail"]["basePrice"] * 1.25)}, token=owner_token)

        call("POST", f"/owner/properties/{property_id}/submit", token=owner_token)
        call("PATCH", f"/admin/properties/{property_id}/status", {"status": "APPROVED"},
             token=admin_token)
        print(f"  published: {spec['create']['title']}")

    # One request-to-book awaiting the owner's response, so the inbox is not empty.
    status, results = call("GET", "/listings/search?instantBook=false")
    pending_listing = next((row for row in results.get("rows", [])
                            if not row["instantBook"]), None)
    if pending_listing:
        status, booking = call("POST", "/bookings", {
            "propertyId": pending_listing["id"],
            "checkIn": str(check_in), "checkOut": str(check_in + timedelta(days=3)),
            "guests": 2, "message": "Travelling with my partner, arriving late evening.",
        }, token=guest_token)
        if status == 201:
            print(f"  booking request created: {booking['reference']}")

    print()
    print("Sign in with these phone numbers; the code appears in the backend log:")
    print(f"  owner  {OWNER_PHONE}  (partner portal, localhost:5173)")
    print(f"  guest  {GUEST_PHONE}  (guest app, localhost:3000)")
    print(f"  admin  {args.admin_email} / {args.admin_password}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
