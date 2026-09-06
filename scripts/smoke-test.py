#!/usr/bin/env python3
"""End-to-end check of the Step 1 auth surface against a running backend.

Reads one-time codes out of the backend log, which only works while
``app.otp.delivery=log`` — i.e. in development. Run it after ``docker compose up``
and ``./mvnw spring-boot:run``::

    python3 scripts/smoke-test.py --log /tmp/backend.log

Exits non-zero on the first failed expectation.
"""

import argparse
import json
import re
import sys
import time
import urllib.error
import urllib.request
import uuid

PASS, FAIL = "\033[32mPASS\033[0m", "\033[31mFAIL\033[0m"
failures = []


def call(base, method, path, body=None, token=None):
    """@return (status_code, parsed_body_or_None)"""
    request = urllib.request.Request(base + path, method=method)
    request.add_header("Content-Type", "application/json")
    if token:
        request.add_header("Authorization", "Bearer " + token)
    data = json.dumps(body).encode() if body is not None else None
    try:
        with urllib.request.urlopen(request, data, timeout=15) as response:
            raw = response.read()
            return response.status, (json.loads(raw) if raw else None)
    except urllib.error.HTTPError as error:
        raw = error.read()
        try:
            return error.code, (json.loads(raw) if raw else None)
        except json.JSONDecodeError:
            return error.code, {"raw": raw.decode(errors="replace")}


def check(label, condition, detail=""):
    print(f"  [{PASS if condition else FAIL}] {label}{'' if condition else '  <- ' + str(detail)}")
    if not condition:
        failures.append(label)
    return condition


def latest_code(log_path, since_offset):
    """Scrape the newest dev-SMS code written after `since_offset` bytes."""
    for _ in range(20):
        with open(log_path, "rb") as handle:
            handle.seek(since_offset)
            found = re.findall(rb"\[DEV SMS\].*?body=(\d+)", handle.read())
        if found:
            return found[-1].decode()
        time.sleep(0.25)
    raise SystemExit(f"No dev SMS code found in {log_path} after offset {since_offset}. "
                     "Is app.otp.delivery=log?")


def log_size(log_path):
    with open(log_path, "rb") as handle:
        handle.seek(0, 2)
        return handle.tell()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", default="http://localhost:8080")
    parser.add_argument("--log", required=True, help="path to the backend log")
    parser.add_argument("--admin-email", default="admin@stay.local")
    parser.add_argument("--admin-password", default="ChangeMe123!")
    args = parser.parse_args()
    api = args.base + "/api/v1"

    # A fresh number per run keeps the per-destination OTP cooldown out of the way.
    phone = "9" + str(int(time.time()))[-7:]

    print("\nhealth")
    status, body = call(args.base, "GET", "/actuator/health")
    check("actuator reports UP", status == 200 and body.get("status") == "UP", body)

    print("\nphone-first login (register-or-sign-in)")
    offset = log_size(args.log)
    status, body = call(api, "POST", "/auth/otp/request", {"phone": phone, "locale": "mn"})
    check("otp/request accepted", status == 200, body)
    check("response carries a resend cooldown", (body or {}).get("resendAfterSeconds", 0) > 0, body)

    status, body = call(api, "POST", "/auth/otp/verify", {"phone": phone, "code": "000000"})
    check("wrong code rejected", status == 400 and body.get("code") == "otp_invalid", body)

    code = latest_code(args.log, offset)
    status, session = call(api, "POST", "/auth/otp/verify",
                           {"phone": phone, "code": code, "deviceLabel": "smoke-test"})
    check("correct code returns a session", status == 200 and "accessToken" in (session or {}), session)
    if not session or "accessToken" not in session:
        return report()

    user = session["user"]
    check("phone normalized to E.164", user["phone"] == "+976" + phone, user["phone"])
    check("account activated on first verify", user["status"] == "ACTIVE", user["status"])
    check("CLIENT role granted automatically",
          [grant["role"] for grant in user["roles"]] == ["CLIENT"], user["roles"])
    check("ids are UUID v7", uuid.UUID(user["id"]).version == 7, user["id"])

    guest_token, guest_refresh, guest_id = (
        session["accessToken"], session["refreshToken"], user["id"])

    print("\ncode reuse and rate limits")
    status, body = call(api, "POST", "/auth/otp/verify", {"phone": phone, "code": code})
    check("code cannot be reused", status == 400 and body.get("code") == "otp_expired", body)
    status, body = call(api, "POST", "/auth/otp/request", {"phone": phone})
    check("resend blocked by cooldown", status == 429 and body.get("code") == "otp_cooldown", body)

    print("\nsession endpoints")
    status, body = call(api, "GET", "/auth/me", token=guest_token)
    check("GET /auth/me returns the caller", status == 200 and body.get("id") == guest_id, body)
    status, body = call(api, "GET", "/users/me")
    check("GET /users/me without a token is 401", status == 401, body)
    status, body = call(api, "GET", "/users/me", token="not-a-jwt")
    check("garbage bearer token is 401", status == 401, body)

    status, body = call(api, "PATCH", "/users/me",
                        {"fullName": "Smoke Test", "locale": "en"}, token=guest_token)
    check("profile update applies", status == 200 and body.get("fullName") == "Smoke Test", body)
    check("unset fields are untouched", (body or {}).get("phone") == "+976" + phone, body)

    print("\nrefresh-token rotation")
    status, refreshed = call(api, "POST", "/auth/refresh", {"refreshToken": guest_refresh})
    check("refresh returns a new pair", status == 200 and "refreshToken" in (refreshed or {}), refreshed)
    rotated = (refreshed or {}).get("refreshToken")
    check("refresh token actually rotated", rotated != guest_refresh)
    status, body = call(api, "POST", "/auth/refresh", {"refreshToken": guest_refresh})
    check("replaying the old token is refused",
          status == 401 and body.get("code") == "refresh_token_reused", body)
    # The successor was revoked by the family kill without being replaced, so it
    # reports as a dead session rather than as another replay.
    status, body = call(api, "POST", "/auth/refresh", {"refreshToken": rotated})
    check("reuse detection killed the whole family",
          status == 401 and body.get("code") == "refresh_token_revoked", body)

    print("\nhost application")
    status, body = call(api, "POST", "/users/me/host-applications",
                        {"requestedRole": "HOUSE_OWNER", "note": "smoke"}, token=guest_token)
    check("application submitted", status == 201 and body.get("status") == "PENDING", body)
    status, body = call(api, "POST", "/users/me/host-applications",
                        {"requestedRole": "HOUSE_OWNER"}, token=guest_token)
    check("duplicate application refused",
          status == 409 and body.get("code") == "application_pending", body)
    status, body = call(api, "POST", "/users/me/host-applications",
                        {"requestedRole": "HOTEL_MANAGER"}, token=guest_token)
    check("hotel application needs a business name",
          status == 400 and body.get("code") == "organization_name_required", body)
    status, body = call(api, "POST", "/users/me/host-applications",
                        {"requestedRole": "SUPER_ADMIN"}, token=guest_token)
    check("cannot apply for SUPER_ADMIN",
          status == 400 and body.get("code") == "validation_failed", body)

    print("\nadmin console")
    status, body = call(api, "GET", "/admin/users", token=guest_token)
    check("guest is forbidden from the admin API",
          status == 403 and body.get("code") == "forbidden", body)

    status, admin_session = call(api, "POST", "/auth/login",
                                 {"email": args.admin_email, "password": args.admin_password})
    if not check("seeded admin can sign in with its password", status == 200, admin_session):
        return report()
    admin_token = admin_session["accessToken"]
    check("seeded admin holds SUPER_ADMIN",
          "SUPER_ADMIN" in [grant["role"] for grant in admin_session["user"]["roles"]],
          admin_session["user"]["roles"])

    status, body = call(api, "POST", "/auth/login",
                        {"email": args.admin_email, "password": "wrong-password"})
    check("wrong password is 401 invalid_credentials",
          status == 401 and body.get("code") == "invalid_credentials", body)
    status, body = call(api, "POST", "/auth/login",
                        {"email": "nobody@example.com", "password": "wrong-password"})
    check("unknown email gives the identical error",
          status == 401 and body.get("code") == "invalid_credentials", body)

    status, page = call(api, "GET", "/admin/users?size=5", token=admin_token)
    check("user list is paged", status == 200 and "rows" in (page or {}) and "total" in (page or {}), page)
    status, page = call(api, "GET", f"/admin/users?q={phone}", token=admin_token)
    check("search by phone fragment finds the guest",
          status == 200 and any(row["id"] == guest_id for row in page.get("rows", [])), page)
    status, page = call(api, "GET", "/admin/users?role=SUPER_ADMIN", token=admin_token)
    check("filter by role works", status == 200 and page.get("total", 0) >= 1, page)

    print("\nrole grants")
    status, body = call(api, "POST", f"/admin/users/{guest_id}/roles",
                        {"role": "HOUSE_OWNER"}, token=admin_token)
    check("admin grants HOUSE_OWNER",
          status == 200 and "HOUSE_OWNER" in [g["role"] for g in body.get("roles", [])], body)
    status, body = call(api, "POST", f"/admin/users/{guest_id}/roles",
                        {"role": "HOUSE_OWNER"}, token=admin_token)
    check("duplicate grant refused", status == 409 and body.get("code") == "role_already_held", body)
    status, body = call(api, "POST", f"/admin/users/{guest_id}/roles",
                        {"role": "HOTEL_MANAGER"}, token=admin_token)
    check("hotel role requires an organization",
          status == 400 and body.get("code") == "organization_required", body)
    status, body = call(api, "POST", f"/admin/users/{guest_id}/roles",
                        {"role": "HOUSE_OWNER", "organizationId": str(uuid.uuid4())},
                        token=admin_token)
    check("platform-wide role rejects an organization",
          status == 400 and body.get("code") in ("organization_not_applicable", "role_already_held"), body)
    status, body = call(api, "DELETE", f"/admin/users/{guest_id}/roles/CLIENT", token=admin_token)
    check("CLIENT role is not revocable",
          status == 400 and body.get("code") == "role_not_revocable", body)

    print("\nsuspension revokes sessions")
    status, body = call(api, "PATCH", f"/admin/users/{guest_id}/status",
                        {"status": "SUSPENDED", "reason": "smoke test"}, token=admin_token)
    check("guest suspended", status == 200 and body.get("status") == "SUSPENDED", body)
    status, body = call(api, "POST", "/auth/otp/request", {"phone": phone})
    check("suspended account cannot request a code",
          status == 403 and body.get("code") == "account_not_active", body)
    admin_id = admin_session["user"]["id"]
    status, body = call(api, "PATCH", f"/admin/users/{admin_id}/status",
                        {"status": "SUSPENDED", "reason": "should fail"}, token=admin_token)
    check("admin cannot suspend itself",
          status == 400 and body.get("code") == "cannot_modify_self", body)

    print("\naudit trail")
    status, page = call(api, "GET", f"/admin/audit-logs?targetId={guest_id}", token=admin_token)
    actions = [row["action"] for row in (page or {}).get("rows", [])]
    check("guest actions were audited", status == 200 and "USER_STATUS_CHANGED" in actions, actions)
    check("login was audited", "USER_LOGGED_IN" in actions, actions)
    check("role grant was audited", "USER_ROLE_GRANTED" in actions, actions)

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
