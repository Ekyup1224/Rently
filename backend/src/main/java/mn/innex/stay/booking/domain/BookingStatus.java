package mn.innex.stay.booking.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Booking lifecycle.
 *
 * <p>{@link #occupiesDates()} decides which states hold a date range against the
 * listing. That set is mirrored by the {@code bookings_no_property_overlap}
 * exclusion constraint in the migration, so if a state is added here it must be
 * added there too — the database, not this enum, is what actually prevents
 * double-booking.
 */
public enum BookingStatus {

    /** Request-to-book: waiting on the host to accept or decline. */
    PENDING_HOST_APPROVAL,

    /** Accepted (or instant-book): the guest has a short window to pay. */
    PENDING_PAYMENT,

    /** Paid and locked in. */
    CONFIRMED,

    CHECKED_IN,

    CHECKED_OUT,

    /** Stay finished and settled. */
    COMPLETED,

    /** The host turned the request down. */
    DECLINED,

    /** The host never answered, or the payment window elapsed. */
    EXPIRED,

    CANCELLED_BY_GUEST,

    CANCELLED_BY_HOST;

    private static final Set<BookingStatus> OCCUPYING = EnumSet.of(
            PENDING_HOST_APPROVAL, PENDING_PAYMENT, CONFIRMED, CHECKED_IN, CHECKED_OUT, COMPLETED);

    private static final Set<BookingStatus> TERMINAL = EnumSet.of(
            COMPLETED, DECLINED, EXPIRED, CANCELLED_BY_GUEST, CANCELLED_BY_HOST);

    /** Whether a booking in this state blocks the dates for everyone else. */
    public boolean occupiesDates() {
        return OCCUPYING.contains(this);
    }

    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }

    /** Whether the guest can still cancel from this state. */
    public boolean isGuestCancellable() {
        return this == PENDING_HOST_APPROVAL || this == PENDING_PAYMENT
                || this == CONFIRMED || this == CHECKED_IN;
    }

    /** The states carrying a deadline that the expiry job enforces. */
    public boolean hasDeadline() {
        return this == PENDING_HOST_APPROVAL || this == PENDING_PAYMENT;
    }

    /** SQL-facing list of the occupying states, for native availability queries. */
    public static Set<BookingStatus> occupyingStates() {
        return OCCUPYING;
    }
}
