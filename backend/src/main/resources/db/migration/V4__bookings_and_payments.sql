-- Bookings, payments and the provider callback log.

-- Needed by the overlap-prevention constraint below: btree_gist lets a GiST index
-- mix an equality column with a range column.
create extension if not exists btree_gist;

create table bookings (
    id                  uuid           primary key,
    -- Short human-readable code for support conversations, e.g. SB-7KQ2M9.
    reference           varchar(16)    not null,
    booking_type        varchar(16)    not null default 'PROPERTY',
    property_id         uuid           references properties (id),
    guest_user_id       uuid           not null references users (id),
    -- Denormalized: the owner at booking time is the counterparty even if the
    -- listing later changes hands, and payouts should not join through properties.
    host_user_id        uuid           not null references users (id),

    check_in            date           not null,
    check_out           date           not null,
    nights              integer        generated always as (check_out - check_in) stored,
    guest_count         integer        not null,

    status              varchar(28)    not null,
    payment_status      varchar(24)    not null default 'UNPAID',
    currency            varchar(3)  not null default 'MNT',

    -- What the guest pays.
    nightly_subtotal    numeric(14, 2) not null,
    cleaning_fee        numeric(14, 2) not null default 0,
    guest_service_fee   numeric(14, 2) not null default 0,
    tax                 numeric(14, 2) not null default 0,
    total               numeric(14, 2) not null,
    -- What the host receives. host_payout is total-side minus commission, so the
    -- parts always sum exactly with no rounding drift.
    host_commission     numeric(14, 2) not null default 0,
    host_payout         numeric(14, 2) not null default 0,
    commission_rule_id  uuid           references commission_rules (id),

    -- Snapshot: the policy in force when the guest booked, not today's policy.
    cancellation_policy varchar(16)    not null,
    guest_message       text,
    host_response_note  text,

    -- Deadline for the current pending state (host response, or payment hold).
    expires_at          timestamptz,
    approved_at         timestamptz,
    confirmed_at        timestamptz,
    cancelled_at        timestamptz,
    cancelled_by        uuid           references users (id),
    cancellation_reason text,
    refund_amount       numeric(14, 2),
    completed_at        timestamptz,

    created_at          timestamptz    not null default now(),
    updated_at          timestamptz    not null default now(),
    version             bigint         not null default 0,

    constraint uq_bookings_reference unique (reference),
    constraint ck_bookings_type check (booking_type = 'PROPERTY'),
    constraint ck_bookings_property_present check (
        booking_type <> 'PROPERTY' or property_id is not null),
    constraint ck_bookings_dates check (check_out > check_in),
    constraint ck_bookings_guests check (guest_count >= 1),
    constraint ck_bookings_status check (status in (
        'PENDING_HOST_APPROVAL', 'PENDING_PAYMENT', 'CONFIRMED',
        'CHECKED_IN', 'CHECKED_OUT', 'COMPLETED',
        'DECLINED', 'EXPIRED', 'CANCELLED_BY_GUEST', 'CANCELLED_BY_HOST')),
    constraint ck_bookings_payment_status check (payment_status in (
        'UNPAID', 'PROCESSING', 'PAID', 'PARTIALLY_REFUNDED', 'REFUNDED', 'FAILED')),
    constraint ck_bookings_amounts check (
        nightly_subtotal >= 0 and cleaning_fee >= 0 and guest_service_fee >= 0
        and tax >= 0 and total >= 0 and host_commission >= 0 and host_payout >= 0),
    constraint ck_bookings_total check (
        total = nightly_subtotal + cleaning_fee + guest_service_fee + tax),
    constraint ck_bookings_refund check (refund_amount is null or refund_amount between 0 and total)
);

comment on table bookings is
    'Polymorphic over supply types. Step 2 carries PROPERTY only; room_type_id and the HOTEL type arrive with Step 3.';

-- Double-booking is impossible at the storage layer, under any concurrency, no
-- matter what the application does. Two guests racing for the same weekend get
-- one success and one constraint violation (surfaced as 409), with no
-- select-then-insert window to lose.
--
-- PENDING_PAYMENT holds the dates so nothing is oversold mid-checkout; the
-- expiry job releases the hold. Terminal states (declined, expired, cancelled)
-- are excluded so those dates immediately become bookable again.
alter table bookings
    add constraint bookings_no_property_overlap
        exclude using gist (
            property_id with =,
            daterange(check_in, check_out, '[)') with &&
        ) where (property_id is not null and status in (
            'PENDING_HOST_APPROVAL', 'PENDING_PAYMENT', 'CONFIRMED',
            'CHECKED_IN', 'CHECKED_OUT', 'COMPLETED'));

create index ix_bookings_guest on bookings (guest_user_id, check_in desc);
create index ix_bookings_host on bookings (host_user_id, created_at desc);
create index ix_bookings_property_dates on bookings (property_id, check_in, check_out);
create index ix_bookings_status on bookings (status);
-- Drives the expiry job: only pending rows have a deadline.
create index ix_bookings_expiring on bookings (expires_at) where expires_at is not null;

create table payments (
    id                uuid           primary key,
    booking_id        uuid           not null references bookings (id),
    -- Set on a REFUND to point at the charge being reversed.
    parent_payment_id uuid           references payments (id),
    provider          varchar(16)    not null,
    intent            varchar(16)    not null default 'CHARGE',
    amount            numeric(14, 2) not null,
    currency          varchar(3)  not null default 'MNT',
    status            varchar(16)    not null default 'CREATED',
    provider_ref      varchar(128),
    -- Provider-specific checkout material: QPay returns QR text plus bank deeplinks.
    checkout_payload  jsonb,
    -- Makes a double-tapped Pay button return the existing payment instead of
    -- creating a second charge.
    idempotency_key   varchar(80)    not null,
    failure_code      varchar(64),
    failure_message   varchar(500),
    expires_at        timestamptz,
    paid_at           timestamptz,
    created_at        timestamptz    not null default now(),
    updated_at        timestamptz    not null default now(),
    version           bigint         not null default 0,

    constraint uq_payments_idempotency unique (idempotency_key),
    constraint ck_payments_provider check (provider in ('QPAY', 'SOCIALPAY', 'STRIPE', 'SIMULATED')),
    constraint ck_payments_intent check (intent in ('CHARGE', 'REFUND')),
    constraint ck_payments_status check (status in
        ('CREATED', 'PENDING', 'SUCCEEDED', 'FAILED', 'CANCELLED', 'EXPIRED')),
    constraint ck_payments_amount check (amount > 0),
    constraint ck_payments_refund_parent check (intent <> 'REFUND' or parent_payment_id is not null)
);

-- One provider reference maps to one payment row, so a replayed callback can
-- never be attributed to a second charge.
create unique index uq_payments_provider_ref
    on payments (provider, provider_ref) where provider_ref is not null;
create index ix_payments_booking on payments (booking_id, created_at desc);

-- Every provider callback, stored raw before it is acted on. Payment callbacks
-- arrive more than once by design; this makes replay safe, gives an audit trail
-- when a provider disputes what it sent, and preserves payloads that failed to
-- process for replay after a fix.
create table payment_events (
    id                 uuid        primary key,
    payment_id         uuid        references payments (id),
    provider           varchar(16) not null,
    provider_event_id  varchar(128),
    event_type         varchar(64),
    payload            jsonb       not null,
    signature_verified boolean     not null default false,
    received_at        timestamptz not null default now(),
    processed_at       timestamptz,
    processing_error   varchar(500),

    constraint ck_payment_events_provider check (provider in ('QPAY', 'SOCIALPAY', 'STRIPE', 'SIMULATED'))
);

-- Dedupe key for redelivered callbacks.
create unique index uq_payment_events_provider_event
    on payment_events (provider, provider_event_id) where provider_event_id is not null;
create index ix_payment_events_payment on payment_events (payment_id, received_at desc);
create index ix_payment_events_unprocessed on payment_events (received_at) where processed_at is null;
