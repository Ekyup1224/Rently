package mn.innex.stay.booking.service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * How the booking module asks for money back without depending on the payment
 * module.
 *
 * <p>The dependency runs payment → booking: payment holds a booking reference and
 * confirms bookings when a charge settles. Cancellation needs the arrow to point
 * the other way, so booking declares this port and payment implements it. That
 * keeps the module graph acyclic, which is what makes either side extractable
 * later.
 */
public interface RefundPort {

    /**
     * Refunds against a booking's settled charge. Implementations must be safe to
     * call for a booking that was never paid, in which case they do nothing.
     *
     * @param amount the gross amount to return to the guest
     */
    void refundForBooking(UUID bookingId, BigDecimal amount, String reason);
}
