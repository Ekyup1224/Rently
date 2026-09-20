-- Step 5: the things that make a marketplace feel inhabited rather than
-- transactional — what people say about a stay, what they say to each other, and
-- getting hosts their money in one payment rather than one per booking.

-- Two-way reviews.
--
-- Both sides write before either can read, which is the only arrangement that
-- produces honest ones: a host who can see "two stars" before writing their own
-- review writes a different review, and a guest who fears that writes nothing.
-- A review becomes visible when its counterpart arrives, or when the window
-- closes and the other side has said nothing.
create table reviews (
    id            uuid           primary key,
    booking_id    uuid           not null references bookings (id) on delete cascade,
    author_id     uuid           not null references users (id),
    -- Who is being reviewed: the place, or the person who stayed in it.
    subject       varchar(16)    not null,
    -- Set for a SUPPLY review, so a listing can show its own reviews without
    -- walking back through the booking.
    supply_kind   varchar(16),
    supply_id     uuid,
    rating        integer        not null,
    -- cleanliness / accuracy / location / value, each 1-5. Optional, and a map
    -- rather than columns because the set will change and nobody should need a
    -- migration to add "check-in" to it.
    sub_ratings   jsonb          not null default '{}'::jsonb,
    comment       text,
    -- The subject's reply. One per review, and only from the side being reviewed.
    response      text,
    responded_at  timestamptz,
    visible       boolean        not null default false,
    published_at  timestamptz,
    status        varchar(16)    not null default 'PUBLISHED',
    hidden_reason varchar(500),
    created_at    timestamptz    not null default now(),

    -- One review per person per stay.
    constraint uq_reviews_author unique (booking_id, author_id),
    constraint ck_reviews_subject check (subject in ('SUPPLY', 'GUEST')),
    constraint ck_reviews_rating check (rating between 1 and 5),
    constraint ck_reviews_status check (status in ('PUBLISHED', 'HIDDEN')),
    constraint ck_reviews_sub_ratings check (jsonb_typeof(sub_ratings) = 'object'),
    constraint ck_reviews_supply check (
        subject <> 'SUPPLY' or (supply_kind is not null and supply_id is not null)),
    constraint ck_reviews_supply_kind check (
        supply_kind is null or supply_kind in ('PROPERTY', 'HOTEL', 'ROOM_TYPE')),
    constraint ck_reviews_hidden check (status <> 'HIDDEN' or hidden_reason is not null)
);

create index ix_reviews_booking on reviews (booking_id);
create index ix_reviews_author on reviews (author_id, created_at desc);
-- What a listing page reads: visible, published reviews for one place.
create index ix_reviews_supply on reviews (supply_id, created_at desc)
    where visible and status = 'PUBLISHED';
-- What the nightly publisher looks for.
create index ix_reviews_unpublished on reviews (created_at) where not visible;

-- Ratings cached on the listing itself.
--
-- Denormalised deliberately: search sorts and filters by rating, and a subquery
-- over reviews for every row in every search is the wrong trade. Maintained by
-- the review service when a review becomes visible, which is the only moment the
-- number can change.
alter table properties
    add column rating_average numeric(3, 2),
    add column rating_count   integer not null default 0;

alter table hotels
    add column rating_average numeric(3, 2),
    add column rating_count   integer not null default 0;

-- One conversation per booking.
--
-- No cold messaging: a thread needs a stay to exist, which removes the channel
-- a scammer uses to reach someone before any money is at risk, and means every
-- message has context a moderator can read it against.
create table conversations (
    id              uuid        primary key,
    booking_id      uuid        not null references bookings (id) on delete cascade,
    created_at      timestamptz not null default now(),
    last_message_at timestamptz,

    constraint uq_conversations_booking unique (booking_id)
);

create table conversation_participants (
    conversation_id uuid        not null references conversations (id) on delete cascade,
    user_id         uuid        not null references users (id) on delete cascade,
    -- Drives the unread count; null means they have never opened it.
    last_read_at    timestamptz,

    primary key (conversation_id, user_id)
);

create index ix_conversation_participants_user on conversation_participants (user_id);

create table messages (
    id              uuid        primary key,
    conversation_id uuid        not null references conversations (id) on delete cascade,
    sender_id       uuid        not null references users (id),
    body            text        not null,
    -- Set when the message trips the off-platform-payment check. The message is
    -- still delivered: a false positive must not silently swallow someone's
    -- words, and the flag is for a human to judge.
    flagged_reason  varchar(64),
    sent_at         timestamptz not null default now(),

    constraint ck_messages_body check (length(trim(body)) > 0)
);

create index ix_messages_conversation on messages (conversation_id, sent_at desc);
create index ix_messages_flagged on messages (sent_at desc) where flagged_reason is not null;

-- Payouts grouped into one transfer per payee.
--
-- A host with eleven stays this week wants one payment and one line on their
-- bank statement, and whoever sends it wants one file rather than eleven
-- transfers typed by hand.
create table payout_batches (
    id           uuid           primary key,
    created_at   timestamptz    not null default now(),
    created_by   uuid           references users (id),
    payout_count integer        not null default 0,
    total        numeric(14, 2) not null default 0,
    currency     varchar(3)     not null default 'MNT',
    status       varchar(16)    not null default 'OPEN',
    exported_at  timestamptz,

    constraint ck_payout_batches_status check (status in ('OPEN', 'EXPORTED', 'SETTLED'))
);

alter table payouts
    add column batch_id uuid references payout_batches (id);

create index ix_payouts_batch on payouts (batch_id);
