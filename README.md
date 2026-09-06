# Stay booking platform

A four-sided marketplace combining Airbnb-style house/apartment rentals with
Trip.com-style hotel room booking, for the Mongolian market (MN/EN, MNT).
Product name is still TBD; the Java package is `mn.innex.stay`.

Full design: [`docs/build-spec.md`](docs/build-spec.md).

**Build status: Steps 1–2 of 5 complete.** Accounts and roles (Step 1), plus the
house rental loop end to end: listings, photos, calendar, search, quoting,
booking, payment and cancellation (Step 2). Hotels are Step 3.

## Layout

| Path | What it is |
| --- | --- |
| `backend/` | Spring Boot modular monolith (Java 21). One deployable, module boundaries by package. |
| `web-owner-hotel-admin/` | React + Vite + Ant Design + AG Grid portal shared by House Owner, Hotel and Super Admin, with role-based routing. |
| `client-app/` | Next.js PWA for guests. |
| `scripts/smoke-test.py` | End-to-end check of the whole Step 1 auth surface against a running stack. |
| `scripts/smoke-test-step2.py` | The same for the house rental loop: list, approve, search, quote, book, pay, cancel. |
| `scripts/seed-demo.py` | Creates a verified owner with three live listings, a guest, and a pending booking request. |
| `docs/api/step1.http`, `docs/api/step2.http` | Request collections for every endpoint. |

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
cd backend && ./mvnw test          # 66 tests: unit + integration on Testcontainers
python3 scripts/smoke-test.py --log /tmp/backend.log        # auth surface
python3 scripts/smoke-test-step2.py --log /tmp/backend.log  # house rental loop
python3 scripts/seed-demo.py --log /tmp/backend.log         # demo data to click through
```

The integration tests start real Postgres and Redis containers, so `mvn test`
genuinely exercises the Flyway migrations and Hibernate's schema validation. One
of them runs eight guests at a booking simultaneously and asserts that exactly
one wins — the double-booking guard is the kind of thing only concurrency tests
find. The smoke tests read OTP codes out of the backend log, which only works
while `app.otp.delivery=log`.

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

## What Step 2 contains

**Listings.** Owners create a draft from four fields, fill in the rest, and submit
for review; the API reports exactly what is still missing rather than a generic
rejection. Photos upload browser-to-bucket via presigned URLs and are only
registered after storage confirms it holds the object. Editing a live listing is
free, except that changing its location or property type returns it to review,
because the approval was a judgement about those facts.

**Calendar.** Only deviations from the listing's defaults are stored — one row per
exceptional day — so a year of "nothing special" costs nothing. Booked dates are
deliberately *not* written there: they are derived from bookings, so the calendar
and the reservations cannot drift apart. Bulk edits work across a date range with
an optional weekday mask, which is how weekend and seasonal pricing is actually
set.

**Search.** One SQL query filters on text (Postgres full-text over a generated
`tsvector`), city, capacity, price, amenities (`jsonb @>`), instant-book and a
geographic bounding box, and excludes anything blocked or booked for the requested
dates. Filtering availability in the same query rather than afterwards is what
keeps paging correct.

**Pricing.** `POST /listings/{id}/quote` is the single source of truth for what a
stay costs, and `POST /bookings` runs the same code — the client never sends a
price. Money is `numeric(14,2)` and `BigDecimal` end to end; the host payout is
derived by subtracting commission from the total so the two always reconcile.

**Booking.** Instant-book and request-to-book, with a payment hold and a host
response window that a scheduled job enforces so abandoned checkouts do not
strand inventory. Cancellation refunds follow the policy snapshotted on the
booking, not the listing's current one.

**Double-booking is impossible at the storage layer.** A Postgres exclusion
constraint over `(property_id, daterange(check_in, check_out, '[)'))` rejects
overlaps regardless of what the application does, so two guests racing for the
last weekend get one success and one clean 409. `PENDING_PAYMENT` holds the dates,
and a stay ending the day another begins is correctly allowed.

**Payments.** A `PaymentGateway` interface with a simulated provider that mimics
QPay's invoice/QR/callback shape, so the whole book → pay → confirm loop is real
code today. Charges are idempotent, callbacks are signature-verified before
anything is acted on, and every delivery is logged and deduped because providers
redeliver by design.

## Deliberate decisions worth knowing

- **Cancellation tiers are business policy in one enum**
  (`listing/domain/CancellationPolicy`): flexible = full refund until 24 h before
  then the first night withheld; moderate = full until 5 days then 50%; strict =
  50% until 7 days then nothing. The guest service fee comes back only on a full
  refund. Change the numbers there, not in the booking code.
- **Commission is effective-dated and never edited.** A rate change closes the old
  rule and opens a new one, and every booking records which rule priced it, so a
  payout from six months ago is still explainable. Seeded at 10% host / 0% guest.
- **The listing module has no compile-time dependency on booking**, even though
  search and the calendar need booked dates: booking implements an `OccupancyPort`
  that listing declares. The module graph runs payment → booking → listing → user
  with no cycles, which is what keeps any of them extractable later.
- **Booking creation retries once on a lock failure.** Concurrent inserts contend
  on the exclusion constraint's GiST index, and Postgres resolves some of that
  contention with a deadlock rather than a constraint violation; one retry in a
  fresh transaction tells a spurious deadlock apart from genuinely taken dates.

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
- **No real payment provider yet.** The simulated gateway settles payments without
  money moving, which is why `app.payments.simulated.enabled` must be false
  everywhere but a laptop. `QpayPaymentGateway` is the next thing to write once
  merchant credentials exist; it implements the same interface and nothing else
  changes.
- **Payouts are not automated.** The earnings screen reports what completed stays
  are worth to a host, not what has been paid. Step 5.
- KYC `Document` storage is designed but not built (deferred to Step 4 with the
  approval queue). Per the spec: national ID numbers and business documents need
  encryption at rest and strict access control **before** launch.
- **Map tiles come from OpenStreetMap's public servers**, which their usage policy
  does not permit for production traffic. A keyed provider (MapTiler, Stadia) or
  self-hosted tiles is needed before launch; the style object in
  `components/ResultsMap.tsx` is the only thing that changes.
- Tax is 0%. Mongolian VAT treatment of short-term rentals needs the legal review
  the spec calls for; the rate is `app.booking.tax-percent` when someone qualified
  answers.
- Search filters on the listing's *base* price, so a per-night override is not
  reflected in a price-range filter. The quote endpoint is always exact.
- The scheduled jobs assume a single application instance. They are idempotent, so
  the worst case is duplicated queries, but a scheduler lock belongs there before
  scaling out.
- Portal bundle is ~2 MB (608 KB gzipped) in one chunk. Fine for an internal
  tool; split it if it starts to hurt.
- No service worker in the PWA. A valid manifest over HTTPS is enough to install;
  push notifications arrive in Step 5.
