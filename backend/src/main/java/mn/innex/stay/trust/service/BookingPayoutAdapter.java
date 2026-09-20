package mn.innex.stay.trust.service;

import java.util.UUID;

import mn.innex.stay.booking.domain.Booking;
import mn.innex.stay.booking.scheduler.PayoutReleasePort;
import mn.innex.stay.booking.service.PayoutPort;
import org.springframework.stereotype.Component;

/** Connects the booking module's payout port to this module's implementation. */
@Component
public class BookingPayoutAdapter implements PayoutPort, PayoutReleasePort {

    private final PayoutService payouts;

    public BookingPayoutAdapter(PayoutService payouts) {
        this.payouts = payouts;
    }

    @Override
    public void scheduleForBooking(Booking booking) {
        payouts.schedule(booking);
    }

    @Override
    public void cancelForBooking(UUID bookingId, String reason) {
        payouts.cancelForBooking(bookingId, reason);
    }

    @Override
    public int releaseDue() {
        return payouts.releaseDue();
    }
}
