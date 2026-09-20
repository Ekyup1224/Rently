package mn.innex.stay.booking.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import mn.innex.stay.common.Money;
import mn.innex.stay.common.PlatformTime;
import mn.innex.stay.common.supply.CancellationPolicy;
import org.springframework.stereotype.Component;

/**
 * Turns a cancellation policy into an amount.
 *
 * <p>Refunds apply to the accommodation total — nights plus cleaning fee. The
 * guest service fee is returned only when the accommodation is fully refunded,
 * because a partial cancellation still consumed platform work.
 *
 * <p>Every calculation takes the policy as an argument rather than reading it from
 * the listing, so a booking is always judged by the terms it was made under.
 */
@Component
public class RefundCalculator {

    /**
     * What a guest gets back for cancelling today.
     *
     * @param policy        the policy snapshotted on the booking
     * @param checkIn       first night of the stay
     * @param accommodation nights plus cleaning fee
     * @param serviceFee    the guest service fee charged on top
     * @param firstNight    price of the first night, withheld by FLEXIBLE after its cutoff
     */
    public BigDecimal refundFor(CancellationPolicy policy, LocalDate checkIn,
                                BigDecimal accommodation, BigDecimal serviceFee,
                                BigDecimal firstNight) {
        return refundFor(policy, checkIn, PlatformTime.today(), accommodation, serviceFee, firstNight);
    }

    /** Testable form: the cancellation date is explicit. */
    public BigDecimal refundFor(CancellationPolicy policy, LocalDate checkIn, LocalDate cancelledOn,
                                BigDecimal accommodation, BigDecimal serviceFee,
                                BigDecimal firstNight) {
        long daysBefore = ChronoUnit.DAYS.between(cancelledOn, checkIn);
        boolean beforeCutoff = daysBefore >= policy.getFreeCancellationDays();

        BigDecimal accommodationRefund;
        if (beforeCutoff) {
            accommodationRefund = Money.percentOf(accommodation,
                    BigDecimal.valueOf(policy.getRefundPercentBeforeCutoff()));
        } else if (policy.isWithholdFirstNightAfterCutoff()) {
            // Withhold one night rather than a percentage of the whole stay.
            accommodationRefund = Money.subtract(accommodation, firstNight);
            if (accommodationRefund.signum() < 0) {
                accommodationRefund = Money.ZERO;
            }
        } else {
            accommodationRefund = Money.percentOf(accommodation,
                    BigDecimal.valueOf(policy.getRefundPercentAfterCutoff()));
        }

        // The service fee comes back only when the guest is made whole on the stay.
        boolean fullAccommodationRefund = accommodationRefund.compareTo(Money.of(accommodation)) >= 0;
        BigDecimal feeRefund = fullAccommodationRefund ? Money.of(serviceFee) : Money.ZERO;

        return Money.add(accommodationRefund, feeRefund);
    }

    /**
     * The refund terms to show a guest before they book, one entry per policy
     * boundary. Shown at booking time so the terms are never a surprise later.
     */
    public List<Quote.RefundWindow> scheduleFor(CancellationPolicy policy, LocalDate checkIn,
                                                BigDecimal accommodation, BigDecimal serviceFee,
                                                BigDecimal firstNight) {
        List<Quote.RefundWindow> windows = new ArrayList<>();
        LocalDate cutoff = checkIn.minusDays(policy.getFreeCancellationDays());

        BigDecimal beforeAmount = refundFor(policy, checkIn, cutoff.minusDays(1),
                accommodation, serviceFee, firstNight);
        windows.add(new Quote.RefundWindow(cutoff, beforeAmount,
                describe(policy.getRefundPercentBeforeCutoff(), cutoff, true)));

        BigDecimal afterAmount = refundFor(policy, checkIn, checkIn,
                accommodation, serviceFee, firstNight);
        windows.add(new Quote.RefundWindow(checkIn, afterAmount,
                policy.isWithholdFirstNightAfterCutoff()
                        ? "Cancel after " + cutoff + ": everything except the first night"
                        : describe(policy.getRefundPercentAfterCutoff(), cutoff, false)));

        return windows;
    }

    private String describe(int percent, LocalDate cutoff, boolean before) {
        String when = before ? "Cancel before " + cutoff : "Cancel on or after " + cutoff;
        if (percent >= 100) {
            return when + ": full refund";
        }
        if (percent <= 0) {
            return when + ": no refund";
        }
        return when + ": " + percent + "% refund";
    }
}
