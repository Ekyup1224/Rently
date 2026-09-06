package mn.innex.stay.booking.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import mn.innex.stay.listing.domain.CancellationPolicy;

/**
 * A priced stay: what the guest would pay, what the host would receive, and the
 * refund terms — computed without creating anything.
 *
 * <p>This is the contract the booking must match. The client never sends prices;
 * it asks for a quote to display, and {@code POST /bookings} recomputes the whole
 * breakdown server-side, so a tampered browser changes nothing.
 *
 * @param nightlyRates    per-night prices, showing where weekend or seasonal
 *                        overrides applied rather than just a total
 * @param guestServiceFee platform fee added on top of the accommodation
 * @param hostPayout      total accommodation minus commission; derived by
 *                        subtraction so the split always reconciles
 * @param refundSchedule  what a cancellation returns, at each policy boundary
 */
public record Quote(
        UUID propertyId,
        LocalDate checkIn,
        LocalDate checkOut,
        int nights,
        int guests,
        String currency,
        List<NightlyRate> nightlyRates,
        BigDecimal nightlySubtotal,
        BigDecimal cleaningFee,
        BigDecimal guestServiceFee,
        BigDecimal tax,
        BigDecimal total,
        BigDecimal hostCommission,
        BigDecimal hostPayout,
        UUID commissionRuleId,
        CancellationPolicy cancellationPolicy,
        List<RefundWindow> refundSchedule,
        boolean instantBook) {

    /**
     * @param overridden true when this night is priced by a calendar override
     *                   rather than the listing's base price
     */
    public record NightlyRate(LocalDate date, BigDecimal amount, boolean overridden) {
    }

    /**
     * @param cancelBefore cancelling strictly before this date returns {@code refundAmount}
     * @param description  human-readable terms for the booking confirmation
     */
    public record RefundWindow(LocalDate cancelBefore, BigDecimal refundAmount, String description) {
    }
}
