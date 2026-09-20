#!/usr/bin/env python3
"""Seeds demo data: a verified house owner with live listings, a hotel business
with room types and inventory, and a guest.

Development only — it reads OTP codes from the backend log and uses the simulated
payment provider. Prints the phone numbers to sign in with; codes appear in the
backend log as ``[DEV SMS] ... body=NNNNNN``.

    python3 scripts/seed-demo.py --log /tmp/backend.log
"""

import argparse
import json
import re
import math
import random
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
MANAGER_PHONE = "+97611000003"
DESK_PHONE = "+97611000004"
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

# One hotel, so the partner portal's hotel pages and the unified search have
# something real in them. Room counts are deliberately small: three standard
# rooms make the oversell guard reachable by hand in the browser.
HOTEL = {
    "create": {"name": "Blue Sky Hotel Ulaanbaatar", "city": "Ulaanbaatar"},
    "detail": {
        "description": "A business hotel on Peace Avenue, ten minutes' walk from "
                       "Sukhbaatar Square, with a restaurant and an airport shuttle.",
        "addressLine": "Peace Avenue 14", "district": "Chingeltei",
        "latitude": 47.9210, "longitude": 106.9180, "starRating": 4,
        "cancellationPolicy": "MODERATE",
        "amenities": ["WIFI", "RECEPTION_24H", "RESTAURANT", "GYM", "AIRPORT_SHUTTLE",
                      "ELEVATOR", "PARKING_FREE"],
        "policies": "Photo ID required at check-in. Children under 6 stay free.",
        "checkInFrom": "14:00:00", "checkOutBy": "12:00:00",
    },
    "colour": (70, 100, 140),
    "room_types": [
        {
            "create": {"name": "Standard double", "capacity": 2, "totalRooms": 3,
                       "basePrice": 220000},
            "detail": {"description": "26 m2 with a queen bed and a work desk.",
                       "bedConfig": "1 queen bed", "sizeSqm": 26,
                       "amenities": ["MINIBAR", "SAFE", "DESK", "PRIVATE_BATHROOM",
                                     "AIR_CONDITIONING"]},
            "colour": (80, 110, 150),
        },
        {
            "create": {"name": "Twin room", "capacity": 2, "totalRooms": 4,
                       "basePrice": 240000},
            "detail": {"description": "Two single beds, popular with colleagues sharing.",
                       "bedConfig": "2 single beds", "sizeSqm": 28,
                       "amenities": ["SAFE", "DESK", "PRIVATE_BATHROOM", "AIR_CONDITIONING"]},
            "colour": (95, 120, 155),
        },
        {
            "create": {"name": "Suite", "capacity": 4, "totalRooms": 1,
                       "basePrice": 480000},
            "detail": {"description": "A corner suite with a sitting room and city views.",
                       "bedConfig": "1 king bed and a sofa bed", "sizeSqm": 52,
                       "minStayNights": 2,
                       "amenities": ["MINIBAR", "SAFE", "DESK", "PRIVATE_BATHROOM",
                                     "AIR_CONDITIONING", "BATHTUB", "CITY_VIEW"]},
            "colour": (120, 100, 140),
        },
    ],
}


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


def _png(width, height, rows):
    """Wraps finished RGB rows in the smallest valid PNG container."""
    raw = b"".join(b"\x00" + bytes(row) for row in rows)

    def chunk(kind, data):
        return (struct.pack(">I", len(data)) + kind + data
                + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF))

    return (b"\x89PNG\r\n\x1a\n"
            + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw, 6)) + chunk(b"IEND", b""))


def _mix(first, second, amount):
    """Blends two colours; amount 0 keeps the first, 1 gives the second."""
    amount = max(0.0, min(1.0, amount))
    return tuple(int(a + (b - a) * amount) for a, b in zip(first, second))


def _shade(colour, factor):
    return tuple(max(0, min(255, int(channel * factor))) for channel in colour)


def _rect(rows, x0, y0, x1, y1, colour):
    """Fills a rectangle, clipped to the canvas."""
    height, width = len(rows), len(rows[0]) // 3
    x0, x1 = max(0, int(x0)), min(width, int(x1))
    y0, y1 = max(0, int(y0)), min(height, int(y1))
    if x1 <= x0:
        return
    band = bytes(colour) * (x1 - x0)
    for y in range(y0, y1):
        rows[y][x0 * 3:x1 * 3] = band


def _vgrad(rows, x0, y0, x1, y1, top, bottom, curve=1.0):
    """Vertical gradient inside a rectangle."""
    span = max(1, int(y1) - int(y0))
    for step in range(span):
        colour = _mix(top, bottom, (step / span) ** curve)
        _rect(rows, x0, int(y0) + step, x1, int(y0) + step + 1, colour)


def _disc(rows, cx, cy, radius, colour):
    height, width = len(rows), len(rows[0]) // 3
    for y in range(max(0, int(cy - radius)), min(height, int(cy + radius) + 1)):
        half = int((radius ** 2 - (y - cy) ** 2) ** 0.5) if abs(y - cy) <= radius else 0
        _rect(rows, cx - half, y, cx + half, y + 1, colour)


def photo_bytes(colour, kind="landscape", width=1000, height=750, seed=0):
    """A stylised placeholder scene in the listing's colour.

    Flat colour swatches make the whole product look unfinished, and demo
    screenshots are how this gets judged long before it has real photography.
    These are plainly not photographs, but they read as a place. Three kinds,
    because a ger camp, a hotel tower and a bedroom should not look alike.
    """
    rnd = random.Random(seed or sum(colour))

    if kind == "room":
        wall_top = _mix(colour, (247, 241, 232), 0.80)
        wall_low = _mix(colour, (255, 250, 244), 0.90)
        floor = _shade(_mix(colour, (150, 105, 68), 0.80), 0.95)
        rows = [bytearray(bytes(wall_top) * width) for _ in range(height)]
        _vgrad(rows, 0, 0, width, height * 0.68, wall_top, wall_low)
        _rect(rows, 0, height * 0.68, width, height, floor)

        # A window with the same dusk sky the exteriors use, plus a frame.
        wx0, wx1 = width * 0.07, width * 0.40
        wy0, wy1 = height * 0.13, height * 0.47
        _rect(rows, wx0 - 8, wy0 - 8, wx1 + 8, wy1 + 8, _mix(wall_low, (90, 80, 70), 0.35))
        _vgrad(rows, wx0, wy0, wx1, wy1, _mix(colour, (58, 96, 158), 0.5), (252, 228, 192), 1.3)
        _rect(rows, (wx0 + wx1) / 2 - 3, wy0, (wx0 + wx1) / 2 + 3, wy1,
              _mix(wall_low, (90, 80, 70), 0.35))

        # The bed: headboard, mattress, a blanket across the foot, two pillows.
        bx0, bx1 = width * 0.30, width * 0.97
        head_y = height * 0.40
        bed_y = height * 0.58
        _rect(rows, bx0 + width * 0.02, head_y, bx1 - width * 0.02, bed_y,
              _shade(_mix(colour, (120, 92, 74), 0.55), 0.85))
        _rect(rows, bx0, bed_y, bx1, height * 0.92, (250, 248, 244))
        _rect(rows, bx0, height * 0.78, bx1, height * 0.92,
              _mix(colour, (255, 255, 255), 0.35))
        for index in range(2):
            px0 = bx0 + width * 0.04 + index * width * 0.30
            _rect(rows, px0, bed_y - height * 0.05, px0 + width * 0.26,
                  bed_y + height * 0.03, (255, 253, 250))

        # A nightstand with a lit lamp, so the frame has something warm in it.
        _rect(rows, width * 0.07, height * 0.64, width * 0.22, height * 0.90,
              _shade(_mix(colour, (120, 92, 74), 0.6), 0.8))
        _rect(rows, width * 0.142, height * 0.56, width * 0.152, height * 0.64,
              _shade(_mix(colour, (120, 92, 74), 0.6), 0.6))
        _disc(rows, int(width * 0.147), int(height * 0.53), int(height * 0.045),
              (252, 226, 168))
        return _png(width, height, rows)

    # Both exteriors share a dusk sky.
    horizon = int(height * 0.60)
    sky_top = _mix(colour, (54, 92, 156), 0.62)
    sky_low = (252, 230, 196)
    rows = [bytearray(bytes(sky_top) * width) for _ in range(height)]
    _vgrad(rows, 0, 0, width, horizon, sky_top, sky_low, 1.5)
    _disc(rows, int(width * 0.74), int(horizon * 0.34), int(height * 0.07),
          (255, 243, 212))

    if kind == "city":
        ground = _shade(_mix(colour, (40, 46, 62), 0.55), 0.8)
        _rect(rows, 0, horizon, width, height, ground)

        towers = []
        x = -rnd.randint(0, 40)
        while x < width:
            block_w = rnd.randint(int(width * 0.07), int(width * 0.13))
            top_y = horizon - rnd.randint(int(height * 0.08), int(height * 0.42))
            towers.append((x, x + block_w, top_y))
            x += block_w + rnd.randint(6, 18)

        for x0, x1, top_y in towers:
            body = _mix(colour, (30, 38, 58), rnd.uniform(0.25, 0.55))
            _rect(rows, x0, top_y, x1, height * 0.94, body)
            # Lit and dark windows, decided per window rather than per column, or
            # whole towers end up striped.
            lit = _mix((252, 214, 130), body, 0.05)
            dark = _shade(body, 1.22)
            for wy in range(int(top_y) + 14, int(height * 0.9), 26):
                for wx in range(int(x0) + 10, int(x1) - 14, 22):
                    _rect(rows, wx, wy, wx + 10, wy + 13,
                          lit if rnd.random() < 0.32 else dark)
        # A pavement strip, so the towers stand on something.
        _rect(rows, 0, height * 0.94, width, height, _shade(ground, 0.75))
        return _png(width, height, rows)

    # A landscape: distant ridges above the horizon, a nearer one below it, then
    # ground. Two sine terms per ridge, because one reads as a cartoon wave.
    _rect(rows, 0, horizon, width, height, _shade(_mix(colour, (140, 120, 80), 0.4), 0.95))
    layers = [
        (0.10, 0.030, _mix(colour, (198, 214, 232), 0.62)),
        (0.05, 0.024, _mix(colour, (150, 172, 196), 0.42)),
        (-0.06, 0.018, _shade(colour, 0.92)),
        (-0.20, 0.012, _shade(colour, 0.72)),
    ]
    for lift, amplitude, tint in layers:
        phase = rnd.uniform(0, 6.28)
        frequency = rnd.uniform(1.0, 1.8)
        base = horizon - height * lift
        for x in range(width):
            angle = phase + frequency * (x / width) * 6.28
            ridge = base + height * amplitude * (math.sin(angle) + 0.45 * math.sin(angle * 2.7))
            _rect(rows, x, ridge, x + 1, height, tint)

    return _png(width, height, rows)


def upload_photo(token, presign_path, confirm_path, colour, alt="Listing photo",
                 kind="landscape", seed=0):
    """Runs the presign/PUT/confirm handshake, which is the same for every supply type."""
    image = photo_bytes(colour, kind=kind, seed=seed)
    status, presigned = call("POST", presign_path,
                             {"contentType": "image/png", "sizeBytes": len(image)}, token=token)
    if status != 200:
        raise SystemExit(f"presign failed: {status} {presigned}")
    put = urllib.request.Request(presigned["uploadUrl"], method="PUT", data=image)
    put.add_header("Content-Type", "image/png")
    with urllib.request.urlopen(put, timeout=30) as response:
        if response.status not in (200, 204):
            raise SystemExit(f"upload failed: {response.status}")
    status, photo = call("POST", confirm_path,
                         {"storageKey": presigned["storageKey"], "altText": alt}, token=token)
    if status != 201:
        raise SystemExit(f"photo confirm failed: {status} {photo}")


def seed_hotel(admin_token, guest_token):
    """Onboards the hotel business the way a real manager would, then publishes it."""
    manager_token, manager_refresh, _ = sign_in(MANAGER_PHONE)
    call("PATCH", "/users/me", {"fullName": "Nomin Tsend", "locale": "mn"}, token=manager_token)

    status, application = call("POST", "/users/me/host-applications", {
        "requestedRole": "HOTEL_MANAGER",
        "organizationName": "Blue Sky Hotels LLC",
        "organizationRegistrationNo": "REG5541200",
        "note": "Eight rooms across three room types, on Peace Avenue.",
    }, token=manager_token)
    if status != 201:
        print(f"  hotel manager already onboarded: {status}")
    else:
        status, decided = call("POST", f"/admin/host-applications/{application['id']}/approve",
                               {"note": "Registration verified"}, token=admin_token)
        if status != 200:
            raise SystemExit(f"approving the hotel application failed: {status} {decided}")
    # The org-scoped role grant lands in the access token, so refresh to pick it up.
    manager_token = refresh(manager_refresh)

    status, hotel = call("POST", "/hotel/hotels", HOTEL["create"], token=manager_token)
    if status != 201:
        print(f"  skipped hotel: {status} {hotel}")
        return
    hotel_id = hotel["id"]
    call("PATCH", f"/hotel/hotels/{hotel_id}", HOTEL["detail"], token=manager_token)
    upload_photo(manager_token, f"/hotel/hotels/{hotel_id}/photos/upload-url",
                 f"/hotel/hotels/{hotel_id}/photos", HOTEL["colour"], "Hotel exterior",
                 kind="city", seed=11)
    upload_photo(manager_token, f"/hotel/hotels/{hotel_id}/photos/upload-url",
                 f"/hotel/hotels/{hotel_id}/photos", HOTEL["colour"], "A room at the hotel",
                 kind="room", seed=12)

    check_in = date.today() + timedelta(days=21)
    room_type_ids = []
    for spec in HOTEL["room_types"]:
        status, room_type = call("POST", f"/hotel/hotels/{hotel_id}/room-types",
                                 spec["create"], token=manager_token)
        if status != 201:
            print(f"  skipped room type {spec['create']['name']}: {status} {room_type}")
            continue
        room_type_id = room_type["id"]
        room_type_ids.append(room_type_id)
        call("PATCH", f"/hotel/room-types/{room_type_id}", spec["detail"], token=manager_token)
        upload_photo(manager_token, f"/hotel/room-types/{room_type_id}/photos/upload-url",
                     f"/hotel/room-types/{room_type_id}/photos", spec["colour"],
                     spec["create"]["name"], kind="room",
                     seed=len(spec["create"]["name"]))

        # A weekend premium and one closed night, so the inventory grid has
        # overrides to look at rather than a flat wall of base prices.
        friday = check_in + timedelta(days=(4 - check_in.weekday()) % 7)
        call("PUT", f"/hotel/room-types/{room_type_id}/inventory",
             {"from": str(friday), "to": str(friday + timedelta(days=30)),
              "weekdays": ["FRIDAY", "SATURDAY"],
              "rate": round(spec["create"]["basePrice"] * 1.2)}, token=manager_token)
        print(f"  room type: {spec['create']['name']}")

    call("PUT", f"/hotel/room-types/{room_type_ids[0]}/inventory",
         {"from": str(check_in + timedelta(days=2)), "to": str(check_in + timedelta(days=2)),
          "stopSell": True}, token=manager_token)

    status, hotel = call("POST", f"/hotel/hotels/{hotel_id}/submit", token=manager_token)
    if status != 200:
        raise SystemExit(f"hotel submit failed: {status} {hotel}")
    status, hotel = call("PATCH", f"/admin/hotels/{hotel_id}/status", {"status": "APPROVED"},
                         token=admin_token)
    if status != 200:
        raise SystemExit(f"hotel approval failed: {status} {hotel}")
    print(f"  published: {HOTEL['create']['name']}")

    # A front-desk account, so the staff page and its narrower permissions are
    # clickable rather than theoretical.
    sign_in(DESK_PHONE)
    status, staff = call("POST", f"/hotel/hotels/{hotel_id}/staff", {"phone": DESK_PHONE},
                         token=manager_token)
    if status == 201:
        print(f"  front desk staff: {DESK_PHONE}")

    # One paid reservation arriving soon, so the arrivals list and the occupancy
    # report are not empty.
    status, booking = call("POST", "/bookings", {
        "roomTypeId": room_type_ids[0], "checkIn": str(check_in),
        "checkOut": str(check_in + timedelta(days=2)), "guests": 2, "rooms": 1,
        "message": "Arriving on the late flight.",
    }, token=guest_token)
    if status != 201:
        print(f"  hotel reservation skipped: {status} {booking}")
        return
    booking_id = booking["id"]
    status, payment = call("POST", f"/bookings/{booking_id}/payments", {"provider": "SIMULATED"},
                           token=guest_token, headers={"Idempotency-Key": f"seed-{booking_id}"})
    if status == 201:
        call("POST", f"/bookings/{booking_id}/payments/{payment['id']}/simulate-settlement",
             token=guest_token)
        print(f"  reservation confirmed and paid: {booking['reference']}")


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
        upload_photo(owner_token, f"/owner/properties/{property_id}/photos/upload-url",
                     f"/owner/properties/{property_id}/photos", spec["colour"],
                     "The view from the door", kind="landscape",
                     seed=len(spec["create"]["title"]))
        upload_photo(owner_token, f"/owner/properties/{property_id}/photos/upload-url",
                     f"/owner/properties/{property_id}/photos", spec["colour"],
                     "Inside", kind="room", seed=len(spec["create"]["title"]) + 3)

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

    print("hotel business")
    seed_hotel(admin_token, guest_token)

    print()
    print("Sign in with these phone numbers; the code appears in the backend log:")
    print(f"  owner    {OWNER_PHONE}  (partner portal, localhost:5173)")
    print(f"  manager  {MANAGER_PHONE}  (partner portal, hotel side)")
    print(f"  desk     {DESK_PHONE}  (front desk only: reservations, no rates)")
    print(f"  guest    {GUEST_PHONE}  (guest app, localhost:3000)")
    print(f"  admin    {args.admin_email} / {args.admin_password}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
