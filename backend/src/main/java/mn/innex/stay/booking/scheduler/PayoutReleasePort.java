package mn.innex.stay.booking.scheduler;

/**
 * How the booking scheduler drives the payout sweep without depending on the
 * trust module. The sweep lives here because this is where the other booking
 * lifecycle timers already are, and one scheduler is easier to reason about than
 * three.
 */
public interface PayoutReleasePort {

    /** @return how many payouts were released */
    int releaseDue();
}
