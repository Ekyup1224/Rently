package mn.innex.stay.listing.web.dto;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Bulk calendar edit over a date range — how owners actually work: "block the
 * whole of February", "weekends are 20% more all summer".
 *
 * @param to        inclusive, unlike a stay's check-out: an owner blocking
 *                  "the 10th to the 12th" means all three days
 * @param weekdays  restricts the edit to these days of the week; empty means all
 * @param blocked   null leaves availability alone
 * @param price     null leaves pricing alone; use {@code clearPrice} to reset it
 * @param clearPrice removes a previous override, returning those days to base price
 */
public record CalendarUpdateRequest(
        @NotNull LocalDate from,
        @NotNull LocalDate to,
        List<DayOfWeek> weekdays,
        Boolean blocked,
        @DecimalMin("0") BigDecimal price,
        Boolean clearPrice,
        @Min(1) Integer minStayNights,
        Boolean clearMinStay) {

    public boolean clearPriceRequested() {
        return Boolean.TRUE.equals(clearPrice);
    }

    public boolean clearMinStayRequested() {
        return Boolean.TRUE.equals(clearMinStay);
    }
}
