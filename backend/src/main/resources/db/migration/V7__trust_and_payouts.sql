-- Trust and safety, and the money that makes it work.
--
-- The problem: nothing stops someone opening a host account, publishing photos
-- taken from a real listing, and collecting for a house that does not exist.
-- Approval queues alone cannot solve it, because a convincing fake passes review.
--
-- What actually stops it is that the money is held until the guest has arrived.
-- A scam that cannot be paid out is not worth running, so `payouts` below is the
-- centre of this migration; the flags, fingerprints and KYC records exist to
-- decide whether a payout may be released.

-- What the platform owes a host for one booking, and whether it may be sent yet.
--
-- Payouts are rows rather than a computed figure because a payment has to be
-- auditable after the fact: what was owed, when it became releasable, who
-- released it, and what the bank reference was.
create table payouts (
    id              uuid           primary key,
    booking_id      uuid           not null references bookings (id),
    -- Who gets paid. A house pays its owner; a hotel pays the organization, which
    -- is why both are recorded rather than one polymorphic "payee".
    payee_user_id   uuid           not null references users (id),
    organization_id uuid           references organizations (id),
    amount          numeric(14, 2) not null,
    currency        varchar(3)     not null default 'MNT',
    status          varchar(16)    not null default 'PENDING',
    -- Earliest the money may move: check-in plus the hold window. Stored rather
    -- than recomputed so changing the policy later cannot silently move money
    -- that was already promised on the old terms.
    release_after   timestamptz    not null,
    blocked_reason  varchar(64),
    released_at     timestamptz,
    released_by     uuid           references users (id),
    paid_at         timestamptz,
    -- The bank or provider reference for the actual transfer.
    provider_ref    varchar(128),
    note            varchar(500),
    created_at      timestamptz    not null default now(),
    updated_at      timestamptz    not null default now(),
    version         bigint         not null default 0,

    -- One payout per booking: the guard against paying the same stay twice.
    constraint uq_payouts_booking unique (booking_id),
    constraint ck_payouts_status check (status in
        ('PENDING', 'BLOCKED', 'RELEASED', 'PAID', 'CANCELLED')),
    constraint ck_payouts_amount check (amount >= 0),
    -- A blocked payout must say why, or nobody can act on it.
    constraint ck_payouts_blocked_reason check (status <> 'BLOCKED' or blocked_reason is not null),
    constraint ck_payouts_paid_ref check (status <> 'PAID' or provider_ref is not null)
);

create index ix_payouts_payee on payouts (payee_user_id, created_at desc);
create index ix_payouts_organization on payouts (organization_id, created_at desc);
-- The release sweep's query: everything pending whose time has come.
create index ix_payouts_due on payouts (release_after) where status = 'PENDING';
create index ix_payouts_status on payouts (status, created_at desc);

-- A perceptual hash of every uploaded photo, across all three galleries.
--
-- One table rather than a column on each photo table, because the interesting
-- comparison is across kinds: a stolen house photo turning up on a hotel is
-- exactly the case worth catching.
create table photo_fingerprints (
    id            uuid        primary key,
    -- No foreign key: the three galleries are separate tables, and a fingerprint
    -- outliving its photo by a few minutes is harmless. Deletion is by photo_id.
    photo_id      uuid        not null,
    supply_kind   varchar(16) not null,
    supply_id     uuid        not null,
    -- Who published it. A match under the same owner is ordinary (relisting,
    -- shared hotel photography); a match under a different one is the signal.
    owner_user_id uuid        not null references users (id),
    -- 64-bit difference hash, stored signed because Postgres has no unsigned type.
    phash         bigint      not null,
    created_at    timestamptz not null default now(),

    constraint uq_photo_fingerprints_photo unique (photo_id),
    constraint ck_photo_fingerprints_kind check (supply_kind in ('PROPERTY', 'HOTEL', 'ROOM_TYPE'))
);

-- Near-duplicate search is a Hamming distance over every row, so this index only
-- helps the exact-match case. At this size that is fine; a bk-tree or a
-- pg_similarity extension is the answer if the table ever gets large.
create index ix_photo_fingerprints_phash on photo_fingerprints (phash);
create index ix_photo_fingerprints_supply on photo_fingerprints (supply_kind, supply_id);

-- Anything a human needs to look at before a listing can be trusted.
-- Duplicate photos and guest reports share one queue because the decision is the
-- same: is this listing real, and should its money move?
create table listing_flags (
    id             uuid        primary key,
    supply_kind    varchar(16) not null,
    supply_id      uuid        not null,
    type           varchar(32) not null,
    status         varchar(16) not null default 'OPEN',
    -- Who raised it: a guest for a report, null for a machine-raised flag.
    raised_by      uuid        references users (id),
    booking_id     uuid        references bookings (id),
    summary        varchar(500) not null,
    -- Evidence: the matching photo ids and distance, or the guest's own words.
    details        jsonb       not null default '{}'::jsonb,
    resolved_by    uuid        references users (id),
    resolved_at    timestamptz,
    resolution_note varchar(500),
    created_at     timestamptz not null default now(),

    constraint ck_listing_flags_kind check (supply_kind in ('PROPERTY', 'HOTEL', 'ROOM_TYPE')),
    constraint ck_listing_flags_type check (type in ('DUPLICATE_PHOTO', 'GUEST_REPORT')),
    constraint ck_listing_flags_status check (status in ('OPEN', 'DISMISSED', 'UPHELD')),
    constraint ck_listing_flags_details check (jsonb_typeof(details) = 'object'),
    constraint ck_listing_flags_resolved check (
        status = 'OPEN' or (resolved_by is not null and resolved_at is not null))
);

-- The queue, and the "is this listing flagged?" check that gates approval.
create index ix_listing_flags_open on listing_flags (created_at desc) where status = 'OPEN';
create index ix_listing_flags_supply on listing_flags (supply_kind, supply_id, status);

-- Identity documents, submitted by a host and reviewed by an admin.
--
-- Deliberately not required to list: demanding documents before a host has seen
-- any value costs more supply than it saves in fraud. It is required to be paid,
-- which is where it actually bites.
create table kyc_submissions (
    id                uuid         primary key,
    user_id           uuid         not null references users (id) on delete cascade,
    document_type     varchar(32)  not null,
    -- Stored as given; the registry number is what an official check is run
    -- against, and it is not a secret in the way a password is.
    document_number   varchar(64)  not null,
    full_name         varchar(160) not null,
    -- Storage keys, not URLs: these are private objects, unlike listing photos.
    document_image_key varchar(512) not null,
    selfie_image_key  varchar(512),
    status            varchar(16)  not null default 'PENDING',
    reviewed_by       uuid         references users (id),
    reviewed_at       timestamptz,
    review_note       varchar(500),
    created_at        timestamptz  not null default now(),

    constraint ck_kyc_submissions_type check (document_type in
        ('NATIONAL_ID', 'PASSPORT', 'DRIVING_LICENCE', 'BUSINESS_REGISTRATION')),
    constraint ck_kyc_submissions_status check (status in ('PENDING', 'VERIFIED', 'REJECTED')),
    constraint ck_kyc_submissions_reviewed check (
        status = 'PENDING' or (reviewed_by is not null and reviewed_at is not null))
);

create index ix_kyc_submissions_user on kyc_submissions (user_id, created_at desc);
-- At most one submission awaiting review per person.
create unique index uq_kyc_submissions_pending on kyc_submissions (user_id) where status = 'PENDING';
