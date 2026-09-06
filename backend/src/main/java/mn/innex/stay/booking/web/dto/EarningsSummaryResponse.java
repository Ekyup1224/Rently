package mn.innex.stay.booking.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * A host's earnings over a period.
 *
 * <p>Earned, not paid: payouts are automated in Step 5, so this reports what the
 * completed stays are worth to the host rather than what has landed in their bank
 * account. The distinction matters and the field names say so.
 *
 * @param confirmedUpcoming value of stays that are booked but have not happened yet
 */
public record EarningsSummaryResponse(
        LocalDate from,
        LocalDate to,
        String currency,
        BigDecimal earnedFromCompletedStays,
        BigDecimal confirmedUpcoming,
        BigDecimal commissionWithheld,
        int completedStays,
        int upcomingStays,
        List<MonthlyEarning> byMonth) {

    /** @param month first day of the month, so clients can format it as they like */
    public record MonthlyEarning(LocalDate month, BigDecimal earned, int stays) {
    }
}
