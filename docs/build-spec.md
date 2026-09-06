# All-in-One Stay Booking Platform — Build Specification

> Product name: **TBD** (deliberately left unnamed for now; `[APP NAME]` placeholders removed).
> Status: spec of record. Sections 1–9 are design; section 10 is the master prompt handed to the coding assistant.
> Build progress and the decisions taken along the way are in the [README](../README.md).

## 1. Vision & positioning

A single marketplace where a guest can search, compare, and book both independent
houses/apartments (peer-to-peer, Airbnb-style) and hotel rooms (inventory-based,
Trip.com-style) in one search and one checkout. Four distinct sides share the same
backend and design system:

- **Client** — the guest who searches, books, pays, and reviews.
- **House Owner** — an individual or small landlord listing one or more properties.
- **Hotel** — a business account managing one or more properties with room-type inventory and staff.
- **Super Admin** — the platform team: approvals, payments oversight, commissions, support.

## 2. Tech stack

| Layer | Choice | Why |
| --- | --- | --- |
| Backend | Spring Boot 3 (Java 21) + Spring Security + Hibernate/JPA | Transfers directly from day-job stack. PostgreSQL instead of Oracle (free, JSONB for flexible fields like amenities). |
| Web frontend (Owner / Hotel / Admin) | React + Vite + Ant Design + AG Grid | Same component library and grid tooling already used daily; the inventory calendar and admin tables map ~1:1 to existing dashboards. |
| Client-facing app | Next.js (React) responsive PWA | Faster to ship than native, SEO for listing pages, installs to home screen. Capacitor wrap or React Native rebuild later, once validated. |
| Caching/sessions | Redis | Session tokens, rate limiting, search result caching. |
| File storage | S3-compatible (AWS S3 or self-hosted MinIO) | Property/room photos, ID documents. |
| Search | Postgres full-text + filtered queries first; Elasticsearch/Meilisearch later | Do not over-engineer search on day one. |
| Payments | QPay + SocialPay (local QR/wallet rails), Stripe (card/international) | Both local providers have merchant APIs and are the standard checkout options on Mongolian e-commerce sites. |
| SMS/OTP | Local aggregator (Mobicom/Unitel/Skytel-compatible gateway) | Phone verification and booking alerts. Confirm current pricing/API per carrier or reseller before committing. |
| Infra | Docker Compose for dev; single VPS (or AWS/DO) behind Nginx; GitHub Actions for CI | No Kubernetes/microservices infra for an MVP. |

## 3. Architecture: modular monolith

Start as a single Spring Boot application with clearly separated modules
(packages/domains): `user`, `listing` (houses), `hotel`, `booking`, `payment`,
`review`, `messaging`, `admin`. Enforce boundaries with package structure and
interfaces, not network calls. Split a module into its own service only once it has
proven it needs independent scaling — splitting a well-modularized monolith is a
straightforward refactor; un-tangling a premature microservices mess is not.

## 4. Roles & permissions

| Role | Scope |
| --- | --- |
| `CLIENT` | Search, book, pay, message, review, manage own trips |
| `HOUSE_OWNER` | CRUD on own properties, own bookings/calendar, own payouts |
| `HOTEL_MANAGER` | CRUD on own hotel(s), room types, inventory, staff accounts, own bookings/reports |
| `HOTEL_STAFF` | Subset of hotel manager scope (e.g. check-in/out, no rate changes) — configurable per hotel |
| `SUPER_ADMIN` | Everything: approvals, commission config, payment oversight, disputes, moderation, analytics |

A single user account can hold `CLIENT` plus one owner/hotel role — a house owner
should still be able to book stays as a guest elsewhere on the platform.

## 5. Core data model

- **User** — id, email, phone, passwordHash, roles[], status, kycStatus, locale, createdAt
- **Property** (house/apartment) — ownerId, title, description, address (lat/lng), propertyType, amenities[], houseRules, photos[], basePrice, cleaningFee, cancellationPolicy, status (draft/pending/approved/rejected/suspended)
- **AvailabilityDay / PricingOverride** — propertyId, date, isBlocked, priceOverride, minStay
- **Hotel** — ownerOrgId, name, address, geo, starRating, amenities[], photos[], policies, status
- **RoomType** — hotelId, name, capacity, bedConfig, basePrice, amenities[], photos[], totalRooms
- **RoomInventoryDay** — roomTypeId, date, availableCount, rateOverride, stopSell
- **Booking** — type (PROPERTY/HOTEL), referenceId (propertyId or roomTypeId), guestId, checkIn, checkOut, guestCount, status (pending/confirmed/checked_in/checked_out/cancelled/completed), totalPrice, paymentStatus
- **Payment** — bookingId, provider (QPAY/SOCIALPAY/STRIPE), amount, status, providerRef
- **Payout** — recipientId (owner or hotel), period, amount, status
- **Review** — bookingId, raterId, rating, comment, response
- **Conversation/Message** — participantIds[], bookingId (optional), body, sentAt
- **Document (KYC)** — userId / hotelId, type (nationalId, businessLicense, ownershipProof), status, fileUrl
- **CommissionRule** — scope (global/category), hostFeePercent, guestFeePercent
- **Dispute/SupportTicket** — bookingId, raisedBy, category, status, resolutionNotes
- **AuditLog** — actorId, action, targetType, targetId, timestamp

> **Flag for later:** national ID numbers and business documents are sensitive data.
> Plan encryption at rest and strict access control on the `Document` table
> **before** launch, not after.

## 6. Feature breakdown by role

### 6.1 Client
Register/login (email + phone OTP, optional social login) · unified search blending
houses and hotels with a map view · filters (dates, guests, price, property type,
amenities, star rating, instant-book) · listing detail page (gallery, amenities,
policies, reviews, availability calendar) · booking flow with transparent price
breakdown (base + cleaning/service fee + tax) · checkout via QPay/SocialPay/card ·
trip management (upcoming/past, cancel/modify per policy) · messaging with
host/hotel · reviews · wishlist · profile, saved payment methods · push/email/SMS
notifications · MN/EN language toggle, MNT currency.

### 6.2 House Owner
Onboarding with ID + payout account verification · listing CRUD with photos,
amenities, house rules, cancellation policy · pricing rules (base + weekend/seasonal
overrides) · calendar management, optional iCal import/export to cross-list on
Airbnb/Booking.com · booking inbox (approve/decline if not instant-book) · guest
messaging · earnings dashboard and payout history · respond to reviews ·
multi-property support.

### 6.3 Hotel
Onboarding with business registration verification · hotel + room type setup
(capacity, bed config, amenities, photos) · inventory & rate calendar (date × room
type grid — the screen where AG Grid experience saves the most time) · bulk rate
updates, stop-sell, min-stay rules · reservation management with check-in/check-out
status · staff accounts with scoped permissions · occupancy/revenue reporting ·
respond to reviews.

### 6.4 Super Admin
User management (verify/suspend, role assignment) · listing/hotel approval queue
with document review · content moderation (flagged reviews/messages/listings) ·
commission & fee configuration · payment oversight (transaction monitoring, payout
holds/approvals, refunds) · dispute/support ticket handling · analytics dashboard
(GMV, booking volume, active listings, take rate) · promotions/featured
listings/static content management · audit log.

## 7. Cross-cutting concerns

- **Commission model:** platform takes a host-side % (industry norm ~3–15%) plus an
  optional guest service fee. Both configurable per category via `CommissionRule`,
  never hardcoded.
- **Cancellation policies:** configurable tiers (flexible/moderate/strict) applied at
  the property/hotel level.
- **Trust & safety:** two-way reviews, verified badges for KYC-completed hosts,
  flagging/moderation queue.
- **Compliance:** plan for Mongolia's data protection expectations around personal
  ID and payment data specifically. Worth a legal review before launch.

## 8. Build order

Demoable at every stage:

1. **Foundations** — auth, roles, DB schema, empty admin shell. **Done 2026-09-06.**
2. **House rental loop** — Owner listing CRUD + Client search/booking/payment, houses only. Shippable v0 on its own. **Done 2026-09-06.**
3. **Hotel loop** — Hotel/room-type CRUD + inventory calendar + Client hotel booking; unify search across both supply types.
4. **Admin console** — approvals, commission config, payment oversight, analytics, disputes.
5. **Polish** — messaging, reviews, payout automation, i18n, mobile wrap.

## 9. Repo structure

```
House_renting_app/
├── backend/                 # Spring Boot modular monolith
│   └── src/main/java/mn/<org>/<app>/
│       ├── user/
│       ├── listing/         # house/property module
│       ├── hotel/
│       ├── booking/
│       ├── payment/
│       ├── review/
│       ├── messaging/
│       └── admin/
├── web-owner-hotel-admin/   # React + Vite + AntD + AG Grid (shared shell, role-based routing)
├── client-app/              # Next.js PWA for guests
└── docs/
    └── build-spec.md        # this file
```

## 10. Working agreement with the coding assistant

For each build-order step: **propose the database schema changes and API endpoints
first, wait for confirmation, then generate the code.** Pause for review after each
step. Ask clarifying questions when a requirement is ambiguous rather than guessing.

---

### Appendix: paste repairs

The source paste arrived with several words truncated. Reconstructed here; please
confirm each reading is what was intended:

| Original fragment | Read as |
| --- | --- |
| `Web frontend (Owner portal, Hotel portalnsole)` | Owner portal, Hotel portal, Admin console |
| `QPay and SocialPay ... for local guests, Stripe for card/intnational guests` | international |
| `once it haproven it needs independent scaling` | has proven |
| `addresat/lng)` | address (lat/lng) |
| `Document (KYC) — usetelId` | userId / hotelId |
| `respond tdmin` (end of 6.3 / start of 6.4) | "respond to reviews." + "### 6.4 Super Admin" |
| `data protection expectations around d payment data` | around personal ID and payment data |
| `one Spring Boot 3 (Java 21) app with clearly separated packostgreSQL as the database` | separated packages. PostgreSQL as the database |
| `scaffold in this sequence and pausr my review` | pause for my review |
