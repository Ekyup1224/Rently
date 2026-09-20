-- Payouts for stays that were already paid for before V7 existed.
--
-- Without this, every booking confirmed before the trust module landed has money
-- taken from a guest and nothing recording what is owed to the host: the ledger
-- would start mid-story, and those hosts would simply never be paid.
--
-- The hold is written as 24 hours rather than read from app.trust.payout-hold,
-- because a migration cannot see application config. That is the value the
-- setting shipped with; if it is changed later, these rows keep the terms they
-- were created under, which is the same promise `payouts.release_after` makes
-- for every row after them.
insert into payouts (id, booking_id, payee_user_id, organization_id, amount, currency,
                     status, release_after, created_at, updated_at, version)
select
    gen_random_uuid(),
    b.id,
    b.host_user_id,
    b.organization_id,
    b.host_payout,
    b.currency,
    -- Everything starts as PENDING and goes through the same release rules as a
    -- new booking: arrived, verified, unflagged. Nothing is grandfathered past
    -- the checks just because it is old.
    'PENDING',
    (b.check_in + time '00:00') at time zone 'Asia/Ulaanbaatar' + interval '24 hours',
    now(),
    now(),
    0
from bookings b
where b.status in ('CONFIRMED', 'CHECKED_IN', 'CHECKED_OUT', 'COMPLETED')
  and b.payment_status in ('PAID', 'PARTIALLY_REFUNDED')
  and not exists (select 1 from payouts p where p.booking_id = b.id);
