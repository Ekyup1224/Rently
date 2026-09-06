-- Step 2: house and apartment listings owned by HOUSE_OWNER accounts.

create table properties (
    id                  uuid          primary key,
    owner_user_id       uuid          not null references users (id),
    title               varchar(150)  not null,
    description         text,
    property_type       varchar(32)   not null,
    max_guests          integer       not null,
    bedrooms            integer       not null default 0,
    beds                integer       not null default 1,
    bathrooms           numeric(3, 1) not null default 1.0,
    address_line        varchar(255),
    district            varchar(120),
    city                varchar(120)  not null,
    country             varchar(2)    not null default 'MN',
    latitude            double precision,
    longitude           double precision,
    amenities           jsonb         not null default '[]'::jsonb,
    house_rules         text,
    check_in_from       time,
    check_out_by        time,
    base_price          numeric(14, 2) not null,
    cleaning_fee        numeric(14, 2) not null default 0,
    currency            varchar(3)    not null default 'MNT',
    min_stay_nights     integer       not null default 1,
    max_stay_nights     integer,
    cancellation_policy varchar(16)   not null default 'MODERATE',
    instant_book        boolean       not null default false,
    status              varchar(24)   not null default 'DRAFT',
    rejection_reason    text,
    published_at        timestamptz,
    created_at          timestamptz   not null default now(),
    updated_at          timestamptz   not null default now(),
    version             bigint        not null default 0,

    constraint ck_properties_type check (property_type in
        ('APARTMENT', 'HOUSE', 'GER', 'CABIN', 'VILLA', 'STUDIO', 'TOWNHOUSE', 'GUESTHOUSE')),
    constraint ck_properties_status check (status in
        ('DRAFT', 'PENDING_REVIEW', 'APPROVED', 'REJECTED', 'PAUSED', 'SUSPENDED')),
    constraint ck_properties_cancellation check (cancellation_policy in ('FLEXIBLE', 'MODERATE', 'STRICT')),
    constraint ck_properties_guests check (max_guests between 1 and 50),
    constraint ck_properties_rooms check (bedrooms >= 0 and beds >= 1 and bathrooms >= 0),
    constraint ck_properties_price check (base_price >= 0 and cleaning_fee >= 0),
    constraint ck_properties_stay check (
        min_stay_nights >= 1 and (max_stay_nights is null or max_stay_nights >= min_stay_nights)),
    constraint ck_properties_latitude check (latitude is null or latitude between -90 and 90),
    constraint ck_properties_longitude check (longitude is null or longitude between -180 and 180),
    -- Amenities are a JSON array of enum-validated strings, never an object.
    constraint ck_properties_amenities check (jsonb_typeof(amenities) = 'array'),
    -- A listing cannot be publicly visible without the data guests need to find it.
    constraint ck_properties_publishable check (
        status <> 'APPROVED'
        or (latitude is not null and longitude is not null and description is not null))
);

comment on column properties.status is
    'DRAFT and PENDING_REVIEW are owner-only. PAUSED is owner-initiated, SUSPENDED is admin-initiated.';
comment on column properties.base_price is 'Per night, in the listing currency. Overridable per day.';

create index ix_properties_owner on properties (owner_user_id);
create index ix_properties_status on properties (status);
-- Search always filters to visible listings first, so status leads the index.
create index ix_properties_search on properties (status, city, base_price);
create index ix_properties_geo on properties (latitude, longitude) where status = 'APPROVED';
create index ix_properties_amenities on properties using gin (amenities jsonb_path_ops);

-- Full-text search over the guest-visible text. The 'simple' configuration is
-- deliberate: Postgres ships no Mongolian stemmer, and applying English rules to
-- Cyrillic tokens mangles them. 'simple' just lower-cases and de-duplicates.
alter table properties
    add column search_vector tsvector generated always as (
        to_tsvector('simple',
            coalesce(title, '') || ' ' ||
            coalesce(city, '') || ' ' ||
            coalesce(district, '') || ' ' ||
            coalesce(description, ''))
    ) stored;

create index ix_properties_search_vector on properties using gin (search_vector);

create table property_photos (
    id           uuid          primary key,
    property_id  uuid          not null references properties (id) on delete cascade,
    storage_key  varchar(512)  not null,
    content_type varchar(64)   not null,
    size_bytes   bigint        not null,
    width        integer,
    height       integer,
    alt_text     varchar(255),
    sort_order   integer       not null default 0,
    is_cover     boolean       not null default false,
    uploaded_at  timestamptz   not null default now(),

    constraint uq_property_photos_key unique (storage_key),
    constraint ck_property_photos_size check (size_bytes > 0),
    constraint ck_property_photos_type check (content_type in ('image/jpeg', 'image/png', 'image/webp'))
);

create index ix_property_photos_property on property_photos (property_id, sort_order);
-- At most one cover per listing.
create unique index uq_property_photos_cover on property_photos (property_id) where is_cover;

-- Deviations from the listing's defaults, one row per exceptional day. A missing
-- row means "available at the base price": storing a row for every calendar day
-- would be mostly empty and would need backfilling as the year rolls forward.
--
-- Booked dates are NOT recorded here. They are derived from the bookings table,
-- so there is exactly one source of truth and the two cannot drift apart.
create table property_availability (
    id              uuid    primary key,
    property_id     uuid    not null references properties (id) on delete cascade,
    day             date    not null,
    is_blocked      boolean not null default false,
    price_override  numeric(14, 2),
    min_stay_nights integer,

    constraint uq_property_availability_day unique (property_id, day),
    constraint ck_property_availability_price check (price_override is null or price_override >= 0),
    constraint ck_property_availability_stay check (min_stay_nights is null or min_stay_nights >= 1),
    -- A row must actually say something, or it is just noise in the calendar.
    constraint ck_property_availability_meaningful check (
        is_blocked or price_override is not null or min_stay_nights is not null)
);

create index ix_property_availability_lookup on property_availability (property_id, day);
