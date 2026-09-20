-- Step 3: bookings become genuinely polymorphic, and hotel inventory gains its
-- oversell guarantee.

alter table bookings
    add column room_type_id    uuid references room_types (id),
    -- Several rooms of the same type in one reservation. One reservation covers
    -- one room type; mixing types means separate reservations.
    add column room_count      integer not null default 1,
    -- A hotel payout goes to the business, not to a person.
    add column organization_id uuid references organizations (id);

alter table bookings drop constraint ck_bookings_type;
alter table bookings drop constraint ck_bookings_property_present;

alter table bookings
    add constraint ck_bookings_type check (booking_type in ('PROPERTY', 'HOTEL')),
    add constraint ck_bookings_room_count check (room_count between 1 and 20),
    -- Exactly one supply reference, matching the declared type. Without this a
    -- booking could point at both, or at neither.
    add constraint ck_bookings_supply_reference check (
        (booking_type = 'PROPERTY' and property_id is not null and room_type_id is null)
        or (booking_type = 'HOTEL' and room_type_id is not null and property_id is null)),
    -- A house booking is always a single unit.
    add constraint ck_bookings_property_single_room check (
        booking_type <> 'PROPERTY' or room_count = 1);

create index ix_bookings_room_type_dates on bookings (room_type_id, check_in, check_out)
    where room_type_id is not null;
create index ix_bookings_organization on bookings (organization_id)
    where organization_id is not null;

-- The house overlap constraint is already scoped to `property_id is not null`,
-- so hotel bookings pass through it untouched rather than being wrongly refused.

-- ─── Oversell prevention ─────────────────────────────────────────────────────
--
-- A room type with ten rooms must accept ten concurrent bookings for a night and
-- refuse the eleventh. That is a counted limit, which an exclusion constraint
-- cannot express, so it is enforced by a maintained counter plus the CHECK on
-- room_inventory_day.booked_count.
--
-- The counter is maintained here rather than by the application for two reasons:
-- the database derives it from the bookings themselves, so it cannot drift out of
-- agreement with them; and the guarantee then holds no matter which code path
-- writes a booking, including a manual SQL fix at 2am.

create or replace function apply_room_inventory_delta(
    p_room_type_id uuid,
    p_check_in     date,
    p_check_out    date,
    p_delta        integer
) returns void
language plpgsql as $$
begin
    -- Materialize any night that has no row yet, defaulting to the room type's
    -- physical capacity. This is what lets a hotel sell before filling in a
    -- calendar, while still giving every sold night a row to count against.
    insert into room_inventory_day (room_type_id, day, available_count)
    select p_room_type_id, day::date, room_types.total_rooms
    from generate_series(p_check_in, p_check_out - interval '1 day', interval '1 day') as day
    cross join room_types
    where room_types.id = p_room_type_id
    on conflict (room_type_id, day) do nothing;

    -- Locked in ascending day order so two overlapping stays can never take the
    -- same rows in opposite orders and deadlock. A plain ranged UPDATE gives no
    -- ordering guarantee, and this is exactly the class of contention that shows
    -- up only under load.
    update room_inventory_day
    set booked_count = booked_count + p_delta
    where id in (
        select id
        from room_inventory_day
        where room_type_id = p_room_type_id
          and day >= p_check_in
          and day < p_check_out
        order by day
        for update
    );
end;
$$;

comment on function apply_room_inventory_delta(uuid, date, date, integer) is
    'Adds p_delta to booked_count for each night of a stay, materializing missing inventory rows first.';

create or replace function bookings_maintain_room_inventory() returns trigger
language plpgsql as $$
declare
    -- The statuses that hold inventory. Must stay in step with
    -- BookingStatus.occupiesDates() and with the house exclusion constraint in V4.
    occupying constant text[] := array[
        'PENDING_HOST_APPROVAL', 'PENDING_PAYMENT', 'CONFIRMED',
        'CHECKED_IN', 'CHECKED_OUT', 'COMPLETED'];
    released boolean := false;
    holds    boolean := false;
begin
    if tg_op in ('UPDATE', 'DELETE') then
        released := old.booking_type = 'HOTEL'
            and old.room_type_id is not null
            and old.status = any (occupying);
    end if;

    if tg_op in ('INSERT', 'UPDATE') then
        holds := new.booking_type = 'HOTEL'
            and new.room_type_id is not null
            and new.status = any (occupying);
    end if;

    -- Release first, so a change of dates or room type within one booking does
    -- not transiently double-count and trip the CHECK against itself.
    if released then
        perform apply_room_inventory_delta(
            old.room_type_id, old.check_in, old.check_out, -old.room_count);
    end if;

    if holds then
        perform apply_room_inventory_delta(
            new.room_type_id, new.check_in, new.check_out, new.room_count);
    end if;

    return null;
end;
$$;

create trigger bookings_maintain_room_inventory
    after insert or update or delete on bookings
    for each row execute function bookings_maintain_room_inventory();

comment on trigger bookings_maintain_room_inventory on bookings is
    'Keeps room_inventory_day.booked_count equal to the rooms actually held, so ck_room_inventory_not_oversold makes overselling impossible.';
