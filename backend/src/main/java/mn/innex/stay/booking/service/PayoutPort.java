package mn.innex.stay.booking.service;

import java.util.UUID;

import mn.innex.stay.booking.domain.Booking;

/**
 * How the booking module tells the trust module that money is now owed, without
 * depending on it.
 *
 * <p>Same reasoning as {@link RefundPort}: trust reads bookings to decide whether
 * a payout may be released, so the dependency already runs trust → booking.
 * Scheduling needs the arrow to point back, so booking declares the port and
 * trust implements it.
 */
public interface PayoutPort {

    /**
     * Schedules what this booking will pay its host, once payment has settled.
     * Implementations must be idempotent — a replayed payment callback must not
     * schedule a second payout.
     */
    void scheduleForBooking(Booking booking);

    /**
     * Voids anything owed on a booking that will not happen. Safe to call for a
     * booking that never had a payout.
     */
    void cancelForBooking(UUID bookingId, String reason);
}
