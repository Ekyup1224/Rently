package mn.innex.stay.listing.service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * How the listing module learns which dates are already taken, without depending
 * on the booking module.
 *
 * <p>Booked dates are deliberately not duplicated into the availability table —
 * bookings are the single source of truth — so a listing's calendar and its search
 * results cannot be computed without them. The dependency runs booking → listing
 * (a booking references a property), so listing declares this port and booking
 * implements it. That keeps the module graph acyclic, which is what makes either
 * side extractable into its own service later.
 */
public interface OccupancyPort {

    /**
     * Names of the booking statuses that hold dates against a listing.
     *
     * <p>Provided rather than hardcoded in the search SQL so the definition lives
     * in exactly one place on the booking side, next to the database constraint
     * that enforces it.
     */
    List<String> occupyingStatusNames();

    /**
     * Date ranges taken by bookings, for building a calendar.
     *
     * @param toExclusive exclusive upper bound, matching how nights are counted
     */
    List<OccupiedRange> occupiedRanges(UUID propertyId, LocalDate from, LocalDate toExclusive);

    /**
     * @param checkIn  first occupied night
     * @param checkOut the morning the guest leaves; this date is free again
     */
    record OccupiedRange(LocalDate checkIn, LocalDate checkOut) {

        public boolean covers(LocalDate day) {
            return !day.isBefore(checkIn) && day.isBefore(checkOut);
        }
    }
}
