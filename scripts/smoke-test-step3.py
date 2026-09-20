#!/usr/bin/env python3
"""End-to-end check of the Step 3 hotel loop against a running stack.

Onboards a hotel business, builds a hotel with room types and inventory, puts it
in the unified search alongside houses, books rooms, proves the oversell guard,
runs the front desk, and reports occupancy.

Development only: reads OTP codes from the backend log and uses the simulated
payment provider.

    python3 scripts/smoke-test-step3.py --log /tmp/backend.log
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


def money(value):
    """Jackson 3 serializes BigDecimal as a JSON number, not a string."""
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
    """Role claims are fixed when a token is issued, so a new grant needs a refresh."""
    status, session = call("POST", "/auth/refresh", {"refreshToken": refresh_token})
    if status != 200:
        raise SystemExit(f"refresh failed: {status} {session}")
    return session["accessToken"], session["refreshToken"]


def tiny_png(colour=(120, 130, 160), width=64, height=48):
    rows = [bytes(colour) * width for _ in range(height)]
    raw = b"".join(b"\x00" + row for row in rows)

    def chunk(kind, data):
        return (struct.pack(">I", len(data)) + kind + data
                + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF))

    return (b"\x89PNG\r\n\x1a\n"
            + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw, 6)) + chunk(b"IEND", b""))


def upload_photo(token, presign_path, confirm_path):
    """Runs presign -> PUT -> confirm. @return (status, body) of the confirm."""
    image = tiny_png()
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
                {"storageKey": presigned["storageKey"], "altText": "Room"}, token=token)



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
    manager_phone = "+9769" + stamp + "4"
    desk_phone = "+9769" + stamp + "5"
    guest_phone = "+9769" + stamp + "6"
    rival_phone = "+9769" + stamp + "7"

    check_in = date.today() + timedelta(days=40)
    check_out = check_in + timedelta(days=2)

    print("\nonboarding a hotel business")
    status, admin_session = call("POST", "/auth/login",
                                 {"email": args.admin_email, "password": args.admin_password})
    if not check("admin signed in", status == 200, admin_session):
        return report()
    admin_token = admin_session["accessToken"]

    manager_token, manager_refresh, manager_id = sign_in(manager_phone)

    status, application = call("POST", "/users/me/host-applications", {
        "requestedRole": "HOTEL_MANAGER",
        "organizationName": f"Blue Sky Hotels {stamp} LLC",
        "organizationRegistrationNo": f"REG{stamp}",
        "note": "42 rooms, three room types",
    }, token=manager_token)
    check("hotel application submitted", status == 201, application)

    status, body = call("GET", "/hotel/hotels", token=manager_token)
    check("no hotel access before approval", status == 403, body)

    status, decided = call("POST", f"/admin/host-applications/{application['id']}/approve",
                           {"note": "Registration verified"}, token=admin_token)
    check("admin approved the application",
          status == 200 and decided["status"] == "APPROVED", decided)

    manager_token, manager_refresh = refresh(manager_refresh)
    status, me = call("GET", "/auth/me", token=manager_token)
    hotel_roles = [grant for grant in me["roles"] if grant["role"] == "HOTEL_MANAGER"]
    check("approval granted an org-scoped HOTEL_MANAGER role",
          len(hotel_roles) == 1 and hotel_roles[0].get("organizationId"), me["roles"])
    check("the organization was created by the approval",
          hotel_roles and hotel_roles[0].get("organizationName", "").startswith("Blue Sky"),
          hotel_roles)

    print("\nbuilding the hotel")
    status, hotel = call("POST", "/hotel/hotels",
                         {"name": f"Blue Sky Hotel {stamp}", "city": "Ulaanbaatar"},
                         token=manager_token)
    if not check("hotel created", status == 201 and hotel["status"] == "DRAFT", hotel):
        return report()
    hotel_id = hotel["id"]

    status, body = call("POST", f"/hotel/hotels/{hotel_id}/submit", token=manager_token)
    check("an incomplete hotel cannot be submitted",
          status == 400 and body.get("code") == "hotel_incomplete", body)
    check("and it says a room type is missing",
          "room type" in json.dumps(body).lower(), body)

    call("PATCH", f"/hotel/hotels/{hotel_id}", {
        "description": "A business hotel on Peace Avenue, ten minutes from the square.",
        "addressLine": "Peace Avenue 14", "district": "Chingeltei",
        "latitude": 47.9210, "longitude": 106.9180, "starRating": 4,
        "cancellationPolicy": "MODERATE",
        "amenities": ["WIFI", "RECEPTION_24H", "RESTAURANT", "GYM", "AIRPORT_SHUTTLE",
                      "ELEVATOR", "PARKING_FREE"],
        "policies": "Photo ID required at check-in.",
    }, token=manager_token)
    upload_photo(manager_token, f"/hotel/hotels/{hotel_id}/photos/upload-url",
                 f"/hotel/hotels/{hotel_id}/photos")

    status, standard = call("POST", f"/hotel/hotels/{hotel_id}/room-types",
                            {"name": "Standard double", "capacity": 2, "totalRooms": 3,
                             "basePrice": 220000}, token=manager_token)
    check("room type created", status == 201 and standard["totalRooms"] == 3, standard)
    room_type_id = standard["id"]

    status, suite = call("POST", f"/hotel/hotels/{hotel_id}/room-types",
                         {"name": "Suite", "capacity": 4, "totalRooms": 1, "basePrice": 480000},
                         token=manager_token)
    check("a second room type created", status == 201, suite)
    suite_id = suite["id"]

    call("PATCH", f"/hotel/room-types/{room_type_id}",
         {"amenities": ["MINIBAR", "SAFE", "DESK", "PRIVATE_BATHROOM", "AIR_CONDITIONING"],
          "bedConfig": "1 queen bed"}, token=manager_token)
    upload_photo(manager_token, f"/hotel/room-types/{room_type_id}/photos/upload-url",
                 f"/hotel/room-types/{room_type_id}/photos")

    status, hotel = call("POST", f"/hotel/hotels/{hotel_id}/submit", token=manager_token)
    check("a complete hotel submits", status == 200 and hotel["status"] == "PENDING_REVIEW", hotel)

    status, body = call("GET", f"/hotels/{hotel_id}")
    check("an unapproved hotel is not publicly visible", status == 404, body)

    status, hotel = call("PATCH", f"/admin/hotels/{hotel_id}/status", {"status": "APPROVED"},
                         token=admin_token)
    check("admin approved the hotel", status == 200 and hotel["status"] == "APPROVED", hotel)

    print("\na house to unify with")
    owner_token, owner_refresh, owner_id = sign_in("+9769" + stamp + "9")
    status, _ = call("POST", f"/admin/users/{owner_id}/roles", {"role": "HOUSE_OWNER"},
                     token=admin_token)
    owner_token, _ = refresh(owner_refresh)
    status, house = call("POST", "/owner/properties",
                         {"title": f"Ger camp {stamp}", "propertyType": "GER",
                          "city": "Ulaanbaatar", "maxGuests": 4}, token=owner_token)
    check("house listing created", status == 201, house)
    call("PATCH", f"/owner/properties/{house['id']}", {
        "description": "A ger by the river.", "addressLine": "Gachuurt valley",
        "latitude": 47.9405, "longitude": 107.1210, "basePrice": 180000,
        "amenities": ["STOVE_HEATING", "PARKING_FREE"]}, token=owner_token)
    upload_photo(owner_token, f"/owner/properties/{house['id']}/photos/upload-url",
                 f"/owner/properties/{house['id']}/photos")
    call("POST", f"/owner/properties/{house['id']}/submit", token=owner_token)
    status, approved = call("PATCH", f"/admin/properties/{house['id']}/status",
                            {"status": "APPROVED"}, token=admin_token)
    check("house listing is live", status == 200 and approved["status"] == "APPROVED", approved)
    check("the house photo has an id the gallery can act on",
          approved["photos"] and approved["photos"][0].get("id"), approved.get("photos"))

    print("\nunified search")
    status, results = call("GET", "/listings/search?city=Ulaanbaatar&size=50")
    supply_types = {row["supplyType"] for row in results.get("rows", [])}
    check("search returns both supply types in one result set",
          status == 200 and supply_types == {"PROPERTY", "HOTEL"}, sorted(supply_types))
    hotel_row = next((row for row in results["rows"] if row["id"] == hotel_id), None)
    check("the new hotel is in the results", hotel_row is not None)
    check("a hotel card prices from its cheapest room",
          hotel_row and money(hotel_row["nightlyFrom"]) == 220000.00, hotel_row)
    check("a hotel card carries its star rating and room type count",
          hotel_row and hotel_row["starRating"] == 4 and hotel_row["roomTypeCount"] == 2,
          hotel_row)
    check("house-only fields are absent from a hotel card",
          hotel_row and hotel_row.get("bedrooms") is None
          and hotel_row.get("propertyType") is None, hotel_row)

    status, results = call("GET", "/listings/search?supplyTypes=HOTEL&size=50")
    check("supplyTypes=HOTEL excludes houses",
          all(row["supplyType"] == "HOTEL" for row in results["rows"]) and results["total"] > 0,
          results.get("total"))
    status, results = call("GET", "/listings/search?supplyTypes=PROPERTY&size=50")
    check("supplyTypes=PROPERTY excludes hotels",
          all(row["supplyType"] == "PROPERTY" for row in results["rows"]), results.get("total"))
    status, results = call("GET", "/listings/search?starRating=4&size=50")
    check("a star-rating filter narrows to hotels",
          all(row["supplyType"] == "HOTEL" for row in results["rows"])
          and any(row["id"] == hotel_id for row in results["rows"]), results.get("total"))
    status, results = call("GET", "/listings/search?amenities=RESTAURANT&size=50")
    check("hotel amenity filter works",
          any(row["id"] == hotel_id for row in results["rows"]), results.get("total"))
    status, results = call("GET", "/listings/search?q=Blue+Sky&size=50")
    check("full-text search finds the hotel by name",
          any(row["id"] == hotel_id for row in results["rows"]), results.get("total"))
    status, results = call("GET", "/listings/search?sort=price_asc&size=50")
    prices = [money(row["nightlyFrom"]) for row in results["rows"]]
    check("sorting spans both supply types", prices == sorted(prices), prices[:6])

    print("\nhotel page, availability and quoting")
    status, page = call("GET", f"/hotels/{hotel_id}")
    check("public hotel page resolves", status == 200 and page["name"], page)
    check("it lists the sellable room types", len(page["roomTypes"]) == 2, page.get("roomTypes"))
    check("a hotel publishes its street address, unlike a home",
          page.get("addressLine") is not None, list(page))
    check("room types do not expose how many rooms the hotel has",
          all("totalRooms" not in room for room in page["roomTypes"]), page["roomTypes"][0])

    status, availability = call(
        "GET", f"/hotels/{hotel_id}/availability?checkIn={check_in}&checkOut={check_out}&guests=2")
    standard_row = next((row for row in availability if row["roomTypeId"] == room_type_id), None)
    check("availability lists every room type", status == 200 and len(availability) == 2,
          availability)
    check("all three standard rooms are free",
          standard_row and standard_row["roomsLeft"] == 3 and standard_row["bookable"],
          standard_row)
    check("it prices the whole stay",
          standard_row and money(standard_row["totalForStay"]) == 440000.00, standard_row)

    status, quote = call("POST", f"/hotels/{hotel_id}/room-types/{room_type_id}/quote?rooms=2",
                         {"checkIn": str(check_in), "checkOut": str(check_out), "guests": 4})
    check("quoting two rooms doubles the nightly lines",
          status == 200 and money(quote["nightlySubtotal"]) == 880000.00, quote)
    check("a hotel quote has no cleaning fee", money(quote["cleaningFee"]) == 0.00, quote)
    check("the quote identifies the room type it priced",
          quote["supplyType"] == "HOTEL" and quote["supplyId"] == room_type_id
          and quote["rooms"] == 2, quote)

    status, body = call("POST", f"/hotels/{hotel_id}/room-types/{room_type_id}/quote?rooms=1",
                        {"checkIn": str(check_in), "checkOut": str(check_out), "guests": 3})
    check("a party too big for one room is refused",
          status == 400 and body.get("code") == "too_many_guests", body)

    print("\nbooking rooms")
    guest_token, _, guest_id = sign_in(guest_phone)
    status, booking = call("POST", "/bookings", {
        "roomTypeId": room_type_id, "checkIn": str(check_in), "checkOut": str(check_out),
        "guests": 4, "rooms": 2, "message": "Late arrival",
    }, token=guest_token)
    if not check("two rooms booked", status == 201, booking):
        return report()
    booking_id = booking["id"]
    check("a hotel booking skips host approval",
          booking["status"] == "PENDING_PAYMENT", booking)
    check("the trip card names the hotel and the room",
          booking["listing"]["supplyType"] == "HOTEL"
          and booking["listing"]["roomTypeName"] == "Standard double"
          and booking["listing"]["rooms"] == 2, booking.get("listing"))

    status, availability = call(
        "GET", f"/hotels/{hotel_id}/availability?checkIn={check_in}&checkOut={check_out}&guests=2")
    standard_row = next(row for row in availability if row["roomTypeId"] == room_type_id)
    check("one standard room is left", standard_row["roomsLeft"] == 1, standard_row)

    print("\nthe oversell guard")
    rival_token, _, _ = sign_in(rival_phone)
    status, body = call("POST", "/bookings", {
        "roomTypeId": room_type_id, "checkIn": str(check_in), "checkOut": str(check_out),
        "guests": 2, "rooms": 2,
    }, token=rival_token)
    check("booking more rooms than remain is refused",
          status == 409 and body.get("code") == "rooms_unavailable", body)

    status, last = call("POST", "/bookings", {
        "roomTypeId": room_type_id, "checkIn": str(check_in), "checkOut": str(check_out),
        "guests": 2, "rooms": 1,
    }, token=rival_token)
    check("the last room can still be booked", status == 201, last)

    status, body = call("POST", "/bookings", {
        "roomTypeId": room_type_id, "checkIn": str(check_in), "checkOut": str(check_out),
        "guests": 2, "rooms": 1,
    }, token=rival_token)
    check("a fully booked room type refuses one more",
          status == 409 and body.get("code") == "rooms_unavailable", body)

    status, availability = call(
        "GET", f"/hotels/{hotel_id}/availability?checkIn={check_in}&checkOut={check_out}&guests=2")
    standard_row = next(row for row in availability if row["roomTypeId"] == room_type_id)
    check("availability reports it as full and unbookable",
          standard_row["roomsLeft"] == 0 and not standard_row["bookable"], standard_row)
    check("and says why", standard_row["unavailableReason"] is not None, standard_row)

    status, results = call(
        "GET", f"/listings/search?checkIn={check_in}&checkOut={check_out}&guests=4&size=50")
    check("the hotel still appears while its suite is free",
          any(row["id"] == hotel_id for row in results["rows"]), results.get("total"))

    print("\ninventory")
    status, matrix = call(
        "GET", f"/hotel/hotels/{hotel_id}/inventory?from={check_in}&to={check_out}",
        token=manager_token)
    standard_inv = next((row for row in matrix if row["roomTypeId"] == room_type_id), None)
    check("the matrix returns a dense row per room type",
          status == 200 and len(matrix) == 2 and len(standard_inv["nights"]) == 3, matrix)
    first_night = standard_inv["nights"][0]
    check("the database-maintained counter shows the rooms sold",
          first_night["booked"] == 3 and first_night["remaining"] == 0, first_night)
    check("the checkout day is untouched",
          standard_inv["nights"][2]["booked"] == 0, standard_inv["nights"][2])

    status, body = call("PUT", f"/hotel/room-types/{room_type_id}/inventory",
                        {"from": str(check_in), "to": str(check_in), "availableCount": 1},
                        token=manager_token)
    check("closing rooms that are already sold is refused",
          status == 409 and body.get("code") == "rooms_already_sold", body)

    later = check_in + timedelta(days=10)
    status, body = call("PUT", f"/hotel/room-types/{room_type_id}/inventory",
                        {"from": str(later), "to": str(later + timedelta(days=3)),
                         "rate": 280000, "minStayNights": 2}, token=manager_token)
    check("a seasonal rate applies", status == 200 and body["nightsUpdated"] == 4, body)

    status, quote = call("POST", f"/hotels/{hotel_id}/room-types/{room_type_id}/quote",
                         {"checkIn": str(later), "checkOut": str(later + timedelta(days=2)),
                          "guests": 2})
    check("the override shows in a quote", money(quote["nightlySubtotal"]) == 560000.00, quote)

    status, body = call("PUT", f"/hotel/room-types/{room_type_id}/inventory",
                        {"from": str(later), "to": str(later), "stopSell": True},
                        token=manager_token)
    status, body = call("POST", f"/hotels/{hotel_id}/room-types/{room_type_id}/quote",
                        {"checkIn": str(later), "checkOut": str(later + timedelta(days=2)),
                         "guests": 2})
    check("stop-sell closes the night",
          status == 409 and body.get("code") == "rooms_unavailable", body)
    call("PUT", f"/hotel/room-types/{room_type_id}/inventory",
         {"from": str(later), "to": str(later), "stopSell": False}, token=manager_token)

    print("\npayment and the front desk")
    status, payment = call("POST", f"/bookings/{booking_id}/payments", {"provider": "SIMULATED"},
                           token=guest_token, headers={"Idempotency-Key": f"hotel-{stamp}"})
    check("payment opened", status == 201, payment)
    status, settled = call("POST", f"/bookings/{booking_id}/payments/{payment['id']}"
                           + "/simulate-settlement", token=guest_token)
    check("payment settled", status == 200 and settled["status"] == "SUCCEEDED", settled)

    status, booking = call("GET", f"/bookings/{booking_id}", token=guest_token)
    check("the reservation is confirmed", booking["status"] == "CONFIRMED", booking)

    status, desk = call("GET", f"/hotel/bookings?hotelId={hotel_id}&scope=arriving",
                        token=manager_token)
    check("it appears in the arrivals list",
          any(row["id"] == booking_id for row in desk.get("rows", [])), desk)
    desk_row = next(row for row in desk["rows"] if row["id"] == booking_id)
    check("the desk sees the payout, not the guest total",
          desk_row.get("hostPayout") is not None and desk_row.get("total") is None, desk_row)

    status, body = call("POST", f"/hotel/bookings/{booking_id}/check-out", token=manager_token)
    check("cannot check out before checking in",
          status == 409 and body.get("code") == "booking_not_checked_in", body)

    status, arrived = call("POST", f"/hotel/bookings/{booking_id}/check-in", token=manager_token)
    check("guest checked in", status == 200 and arrived["status"] == "CHECKED_IN", arrived)
    status, left = call("POST", f"/hotel/bookings/{booking_id}/check-out", token=manager_token)
    check("guest checked out", status == 200 and left["status"] == "CHECKED_OUT", left)

    print("\nstaff permissions")
    desk_token, desk_refresh, desk_id = sign_in(desk_phone)
    status, staff = call("POST", f"/hotel/hotels/{hotel_id}/staff", {"phone": desk_phone},
                         token=manager_token)
    check("front-desk staff added", status == 201, staff)
    desk_token, _ = refresh(desk_refresh)

    status, body = call("GET", f"/hotel/bookings?hotelId={hotel_id}&scope=all", token=desk_token)
    check("staff can see reservations", status == 200, body)
    status, body = call("PUT", f"/hotel/room-types/{room_type_id}/inventory",
                        {"from": str(later), "to": str(later), "rate": 1}, token=desk_token)
    check("staff cannot change rates",
          status == 403 and body.get("code") == "manager_role_required", body)
    status, body = call("PATCH", f"/hotel/hotels/{hotel_id}", {"name": "Renamed by staff"},
                        token=desk_token)
    check("staff cannot edit the hotel", status == 403, body)

    status, body = call("POST", f"/hotel/hotels/{hotel_id}/staff", {"phone": guest_phone},
                        token=desk_token)
    check("staff cannot add more staff", status == 403, body)

    print("\noccupancy report")
    status, report_body = call(
        "GET", f"/hotel/reports/occupancy?hotelId={hotel_id}&from={check_in}&to={check_out}",
        token=manager_token)
    check("occupancy reported", status == 200, report_body)
    check("room-nights sold counted from the inventory counters",
          report_body["roomNightsSold"] == 6, report_body)
    check("revenue and ADR are reported separately",
          money(report_body["roomRevenue"]) > 0 and money(report_body["averageDailyRate"]) > 0,
          report_body)

    # A window nobody has edited or booked has no inventory rows at all. Capacity
    # there is still every room on sale, or one booked night makes a hotel look
    # sold out for the month.
    quiet_from = check_in + timedelta(days=200)
    status, quiet = call(
        "GET", f"/hotel/reports/occupancy?hotelId={hotel_id}&from={quiet_from}"
        + f"&to={quiet_from + timedelta(days=1)}", token=manager_token)
    check("untouched nights still count towards capacity",
          status == 200 and quiet["roomNightsAvailable"] == 8, quiet)
    check("and nothing is sold in them",
          quiet["roomNightsSold"] == 0 and money(quiet["occupancyPercent"]) == 0, quiet)

    print("\ncancellation releases inventory")
    status, cancelled = call("POST", f"/bookings/{last['id']}/cancel", {"reason": "Plans changed"},
                             token=rival_token)
    check("a reservation cancelled", status == 200, cancelled)
    status, availability = call(
        "GET", f"/hotels/{hotel_id}/availability?checkIn={check_in}&checkOut={check_out}&guests=2")
    standard_row = next(row for row in availability if row["roomTypeId"] == room_type_id)
    check("the room is sellable again",
          standard_row["roomsLeft"] == 1 and standard_row["bookable"], standard_row)

    print("\nisolation between businesses")
    other_token, other_refresh, other_id = sign_in("+9769" + stamp + "8")
    status, other_app = call("POST", "/users/me/host-applications", {
        "requestedRole": "HOTEL_MANAGER", "organizationName": f"Rival Hotels {stamp}",
        "organizationRegistrationNo": f"RIV{stamp}"}, token=other_token)
    call("POST", f"/admin/host-applications/{other_app['id']}/approve", {}, token=admin_token)
    other_token, _ = refresh(other_refresh)
    status, body = call("GET", f"/hotel/hotels/{hotel_id}", token=other_token)
    check("another business cannot read this hotel", status == 404, body)
    status, body = call("PATCH", f"/hotel/hotels/{hotel_id}", {"name": "Taken over"},
                        token=other_token)
    check("nor edit it", status == 404, body)

    retire(admin_token, hotels=[hotel_id], listings=[house['id']])
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
