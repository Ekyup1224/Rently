package mn.innex.stay.listing.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One day of a listing's calendar.
 *
 * @param status     BOOKED beats BLOCKED beats AVAILABLE, since a booked night
 *                   cannot be freed by unblocking it
 * @param price      the nightly price in force, base or overridden
 * @param overridden whether {@code price} came from a calendar override
 * @param minStay    minimum nights for a stay starting this day
 */
public record CalendarDayResponse(
        LocalDate date,
        DayStatus status,
        BigDecimal price,
        boolean overridden,
        Integer minStay) {

    public enum DayStatus {
        AVAILABLE,
        /** Closed by the owner. */
        BLOCKED,
        /** Held by a booking. Owner-facing calendars show this; public ones report BLOCKED. */
        BOOKED
    }
}
