-- Step 3: hotel supply. A hotel belongs to an organization, sells room types,
-- and each room type has counted per-day inventory.
--
-- The essential difference from houses: a house is exclusive (one booking per
-- night, enforced by an exclusion constraint), while a room type is *counted* —
-- ten rooms take ten concurrent bookings and must refuse the eleventh. See V6
-- for how that limit is enforced.

create table hotels (
    id                  uuid           primary key,
    organization_id     uuid           not null references organizations (id),
    name                varchar(180)   not null,
    description         text,
    -- Null means unrated, which is different from zero stars.
    star_rating         integer,
    address_line        varchar(255),
    district            varchar(120),
    city                varchar(120)   not null,
    country             varchar(2)     not null default 'MN',
    latitude            double precision,
    longitude           double precision,
    amenities           jsonb          not null default '[]'::jsonb,
    policies            text,
    check_in_from       time,
    check_out_by        time,
    currency            varchar(3)     not null default 'MNT',
    cancellation_policy varchar(16)    not null default 'MODERATE',
    status              varchar(24)    not null default 'DRAFT',
    rejection_reason    text,
    published_at        timestamptz,
    created_at          timestamptz    not null default now(),
    updated_at          timestamptz    not null default now(),
    version             bigint         not null default 0,

    constraint ck_hotels_star_rating check (star_rating is null or star_rating between 1 and 5),
    constraint ck_hotels_status check (status in
        ('DRAFT', 'PENDING_REVIEW', 'APPROVED', 'REJECTED', 'PAUSED', 'SUSPENDED')),
    constraint ck_hotels_cancellation check (cancellation_policy in ('FLEXIBLE', 'MODERATE', 'STRICT')),
    constraint ck_hotels_latitude check (latitude is null or latitude between -90 and 90),
    constraint ck_hotels_longitude check (longitude is null or longitude between -180 and 180),
    constraint ck_hotels_amenities check (jsonb_typeof(amenities) = 'array'),
    -- Same rule as properties: nothing publicly visible without what a guest
    -- needs to find and judge it.
    constraint ck_hotels_publishable check (
        status <> 'APPROVED'
        or (latitude is not null and longitude is not null and description is not null))
);

create index ix_hotels_organization on hotels (organization_id);
create index ix_hotels_status on hotels (status);
create index ix_hotels_geo on hotels (latitude, longitude) where status = 'APPROVED';
create index ix_hotels_amenities on hotels using gin (amenities jsonb_path_ops);

-- 'simple' for the same reason as properties: no Mongolian stemmer exists, and
-- English stemming rules mangle Cyrillic tokens.
alter table hotels
    add column search_vector tsvector generated always as (
        to_tsvector('simple',
            coalesce(name, '') || ' ' ||
            coalesce(city, '') || ' ' ||
            coalesce(district, '') || ' ' ||
            coalesce(description, ''))
    ) stored;

create index ix_hotels_search_vector on hotels using gin (search_vector);

create table hotel_photos (
    id           uuid          primary key,
    hotel_id     uuid          not null references hotels (id) on delete cascade,
    storage_key  varchar(512)  not null,
    content_type varchar(64)   not null,
    size_bytes   bigint        not null,
    width        integer,
    height       integer,
    alt_text     varchar(255),
    sort_order   integer       not null default 0,
    is_cover     boolean       not null default false,
    uploaded_at  timestamptz   not null default now(),

    constraint uq_hotel_photos_key unique (storage_key),
    constraint ck_hotel_photos_size check (size_bytes > 0),
    constraint ck_hotel_photos_type check (content_type in ('image/jpeg', 'image/png', 'image/webp'))
);

create index ix_hotel_photos_hotel on hotel_photos (hotel_id, sort_order);
create unique index uq_hotel_photos_cover on hotel_photos (hotel_id) where is_cover;

-- A sellable category of room. total_rooms is how many the hotel physically has;
-- per-day availability can be lower (rooms out of service, allotments held back).
create table room_types (
    id              uuid           primary key,
    hotel_id        uuid           not null references hotels (id) on delete cascade,
    name            varchar(120)   not null,
    description     text,
    capacity        integer        not null,
    bed_config      varchar(160),
    size_sqm        integer,
    base_price      numeric(14, 2) not null,
    total_rooms     integer        not null,
    amenities       jsonb          not null default '[]'::jsonb,
    min_stay_nights integer        not null default 1,
    max_stay_nights integer,
    status          varchar(16)    not null default 'ACTIVE',
    sort_order      integer        not null default 0,
    created_at      timestamptz    not null default now(),
    updated_at      timestamptz    not null default now(),
    version         bigint         not null default 0,

    constraint ck_room_types_capacity check (capacity between 1 and 20),
    constraint ck_room_types_total check (total_rooms between 1 and 2000),
    constraint ck_room_types_price check (base_price >= 0),
    constraint ck_room_types_status check (status in ('ACTIVE', 'INACTIVE')),
    constraint ck_room_types_stay check (
        min_stay_nights >= 1 and (max_stay_nights is null or max_stay_nights >= min_stay_nights)),
    constraint ck_room_types_amenities check (jsonb_typeof(amenities) = 'array')
);

comment on column room_types.total_rooms is
    'Physical room count. The default per-day availability when no inventory row exists.';

create index ix_room_types_hotel on room_types (hotel_id, sort_order);
create index ix_room_types_sellable on room_types (hotel_id) where status = 'ACTIVE';

create table room_type_photos (
    id            uuid          primary key,
    room_type_id  uuid          not null references room_types (id) on delete cascade,
    storage_key   varchar(512)  not null,
    content_type  varchar(64)   not null,
    size_bytes    bigint        not null,
    width         integer,
    height        integer,
    alt_text      varchar(255),
    sort_order    integer       not null default 0,
    is_cover      boolean       not null default false,
    uploaded_at   timestamptz   not null default now(),

    constraint uq_room_type_photos_key unique (storage_key),
    constraint ck_room_type_photos_size check (size_bytes > 0),
    constraint ck_room_type_photos_type check (content_type in ('image/jpeg', 'image/png', 'image/webp'))
);

create index ix_room_type_photos_room_type on room_type_photos (room_type_id, sort_order);
create unique index uq_room_type_photos_cover on room_type_photos (room_type_id) where is_cover;

-- Per-day inventory. Unlike the house calendar this is not exceptions-only in
-- spirit: hotels genuinely vary availability and rate by date, and a channel
-- manager pushes per-day data. A missing row still means "sellable at
-- total_rooms and base price", so a hotel can start selling without filling in a
-- year, and rows are materialized on demand when a booking needs one.
--
-- booked_count is maintained by a database trigger (V6) from the bookings table,
-- so it cannot drift from reality, and the CHECK below makes overselling
-- impossible regardless of what the application does.
create table room_inventory_day (
    id              uuid           primary key default gen_random_uuid(),
    room_type_id    uuid           not null references room_types (id) on delete cascade,
    day             date           not null,
    available_count integer        not null,
    booked_count    integer        not null default 0,
    rate_override   numeric(14, 2),
    -- Closes the night to *new* bookings without touching existing ones, so it
    -- cannot be a CHECK constraint; the booking service enforces it.
    stop_sell       boolean        not null default false,
    min_stay_nights integer,

    constraint uq_room_inventory_day unique (room_type_id, day),
    constraint ck_room_inventory_available check (available_count >= 0),
    constraint ck_room_inventory_rate check (rate_override is null or rate_override >= 0),
    constraint ck_room_inventory_stay check (min_stay_nights is null or min_stay_nights >= 1),
    -- The whole point: a night can never be sold beyond what is available.
    constraint ck_room_inventory_not_oversold check (booked_count >= 0 and booked_count <= available_count)
);

create index ix_room_inventory_lookup on room_inventory_day (room_type_id, day);

-- Which hotels a HOTEL_STAFF account works at. The role grant (user_roles, scoped
-- to the organization) authorizes them; these rows narrow it to specific hotels,
-- so staff at one hotel cannot see another's reservations even within the same
-- business. A HOTEL_MANAGER needs no rows here: they see every hotel in their org.
create table hotel_staff (
    id          uuid        primary key,
    hotel_id    uuid        not null references hotels (id) on delete cascade,
    user_id     uuid        not null references users (id) on delete cascade,
    assigned_at timestamptz not null default now(),
    assigned_by uuid        references users (id),

    constraint uq_hotel_staff unique (hotel_id, user_id)
);

create index ix_hotel_staff_user on hotel_staff (user_id);
