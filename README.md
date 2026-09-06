# Stay booking platform

A four-sided marketplace combining Airbnb-style house/apartment rentals with
Trip.com-style hotel room booking, for the Mongolian market (MN/EN, MNT).
Product name is still TBD; the Java package is `mn.innex.stay`.

Full design: [`docs/build-spec.md`](docs/build-spec.md).

**Build status: Step 1 of 5 complete** — accounts, roles, sessions and the admin
console shell. Listings, booking and payments are Steps 2–5.

## Layout

| Path | What it is |
| --- | --- |
| `backend/` | Spring Boot modular monolith (Java 21). One deployable, module boundaries by package. |
| `web-owner-hotel-admin/` | React + Vite + Ant Design + AG Grid portal shared by House Owner, Hotel and Super Admin, with role-based routing. |
| `client-app/` | Next.js PWA for guests. |
| `scripts/smoke-test.py` | End-to-end check of the whole Step 1 auth surface against a running stack. |
| `docs/api/step1.http` | Request collection for every Step 1 endpoint. |

## Prerequisites

Java 21, Node 20+, Docker (or a local PostgreSQL 16+ and Redis).

## Running it

```bash
# 1. Infrastructure (Postgres on 5433, Redis on 6380, MinIO on 9000/9001)
docker compose up -d

# 2. Backend on :8080
cd backend && ./mvnw spring-boot:run

# 3. Partner portal on :5173
cd web-owner-hotel-admin && npm install && npm run dev

# 4. Guest PWA on :3000
cd client-app && npm install && npm run dev
```

Both frontends proxy `/api` to `localhost:8080`, so the browser stays on one
origin in development and CORS never comes up.

### Using a local Postgres/Redis instead of Docker

The compose file uses non-default host ports (5433, 6380) so it will not collide
with a local install. To point at a local install on default ports:

```bash
POSTGRES_URL=jdbc:postgresql://localhost:5432/stay REDIS_PORT=6379 ./mvnw spring-boot:run
```

Create the database first: `createdb stay`.

### First sign-in

A `SUPER_ADMIN` is seeded on first startup **only if no SUPER_ADMIN exists**, so
this is a no-op on every later start:

- phone `+97699000000`, email `admin@stay.local`, password `ChangeMe123!`

Change it immediately, and override `BOOTSTRAP_ADMIN_*` (see `.env.example`) in
any environment that is not your laptop.

### Getting OTP codes in development

`app.otp.delivery=log` routes codes to the application log instead of an SMS
gateway, so no carrier account is needed:

```
[DEV SMS] to=+976******33 body=418207 is your verification code...
```

Set `app.otp.delivery=sms` and provide a real `SmsSender` bean before deploying
anywhere. Never enable `log` outside development.

## Verifying it works

```bash
cd backend && ./mvnw test          # 32 tests: unit + integration on Testcontainers
python3 scripts/smoke-test.py --log /tmp/backend.log   # 41 checks against a running stack
```

The integration tests start real Postgres and Redis containers, so `mvn test`
genuinely exercises the Flyway migrations and Hibernate's schema validation.
The smoke test reads OTP codes out of the backend log, which only works while
`app.otp.delivery=log`.

## What Step 1 contains

**Authentication.** Phone is the primary identifier. `POST /auth/otp/request` is
both "register" and "sign in": an unknown number gets an account, and the account
activates when it verifies its first code. Email + password is a secondary path
for accounts that set one. Sessions are a short-lived access JWT (15 min) plus an
opaque, rotating refresh token stored only as a SHA-256 hash. Presenting a
rotated refresh token a second time is treated as replay and ends every session
for that user; a token revoked by logout is not, so signing out on one device
leaves the others alone.

**Roles.** One row per grant, so an account can be `CLIENT` plus one host role.
`HOTEL_MANAGER` and `HOTEL_STAFF` grants are scoped to an organization, enforced
by a database CHECK constraint as well as in code. Guests apply for host roles
through `POST /users/me/host-applications`; approval is a Step 4 admin action.

**Admin.** Paged user search, status and KYC changes, role grants and revocation,
and a read-only audit log. Suspending an account or revoking a host role also
revokes its sessions.

**Audit.** Every state change writes an append-only row in its own transaction,
so a failure to audit cannot roll back the change it recorded, or vice versa.

## Deliberate decisions worth knowing

- **Spring Boot 4.1.1, not 3.x.** The spec said Boot 3, but Spring Initializr no
  longer serves it and 3.5 is out of OSS support. Note the renamed starters
  (`spring-boot-starter-webmvc`, `-flyway`, per-starter `-test` artifacts) and
  that `@AutoConfigureMockMvc` moved to `org.springframework.boot.webmvc.test.autoconfigure`.
- **No Swagger UI yet.** No springdoc release supports Boot 4. `docs/api/step1.http`
  covers the endpoints until springdoc 3 lands.
- **JWTs via Spring Security's Nimbus support**, not a third-party JWT library —
  one less dependency, and no Jackson 2/3 conflict under Boot 4.
- **Flyway owns the schema**; Hibernate runs with `ddl-auto: validate`, so a drift
  between an entity and a migration fails startup rather than silently corrupting.
- **UUIDv7 primary keys** — time-ordered for index locality, and not enumerable
  on public endpoints. (Hibernate's `Style.TIME` is UUID v1, which embeds a host
  node id; `VERSION_7` is the right choice.)
- **Access token in memory, refresh token in `localStorage`.** A stolen storage
  dump is not a live session, but the refresh token is XSS-reachable; rotation and
  replay detection limit the damage. Moving it to an HttpOnly cookie is the next
  hardening step.

## Known gaps

- No SMS gateway contract yet — `LoggingSmsSender` stands in.
- No S3 client code; MinIO runs in compose ready for Step 2 photo uploads.
- KYC `Document` storage is designed but not built. Per the spec: national ID
  numbers and business documents need encryption at rest and strict access
  control **before** launch.
- Portal bundle is ~2 MB (608 KB gzipped) in one chunk. Fine for an internal
  tool; split it if it starts to hurt.
- No service worker in the PWA. A valid manifest over HTTPS is enough to install;
  push notifications arrive in Step 5.
