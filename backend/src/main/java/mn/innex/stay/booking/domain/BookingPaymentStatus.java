package mn.innex.stay.booking.domain;

/**
 * Money state of a booking, kept separate from {@link BookingStatus} because the
 * two move independently: a cancelled booking can still be awaiting a refund, and
 * a confirmed one can be partially refunded after a change.
 */
public enum BookingPaymentStatus {

    UNPAID,

    /** A charge has been started with a provider but not settled. */
    PROCESSING,

    PAID,

    PARTIALLY_REFUNDED,

    REFUNDED,

    FAILED
}
