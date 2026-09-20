package mn.innex.stay.search.repo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import mn.innex.stay.common.supply.Amenity;
import mn.innex.stay.listing.domain.PropertyType;

/**
 * What a guest search can filter on, across both supply types.
 *
 * @param supplyTypes restricts to houses or hotels; empty means both
 * @param types       house categories, ignored when hotels are searched
 * @param amenities   results must advertise all of these
 * @param starRating  minimum hotel star rating; excludes houses when set, since a
 *                    house has no rating to compare
 */
public record SupplySearchCriteria(
        String text,
        String city,
        LocalDate checkIn,
        LocalDate checkOut,
        Integer guests,
        List<String> supplyTypes,
        List<PropertyType> types,
        List<Amenity> amenities,
        Boolean instantBook,
        Integer starRating,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        Double latitude,
        Double longitude,
        Double radiusKm,
        String sort,
        int page,
        int size) {

    public boolean hasDates() {
        return checkIn != null && checkOut != null;
    }

    public boolean hasPoint() {
        return latitude != null && longitude != null;
    }

    public long nights() {
        return hasDates() ? ChronoUnit.DAYS.between(checkIn, checkOut) : 0;
    }

    public boolean includesProperties() {
        // A star-rating filter is meaningless for a house, so asking for one
        // narrows the search to hotels rather than silently keeping houses.
        return starRating == null
                && (supplyTypes == null || supplyTypes.isEmpty() || supplyTypes.contains("PROPERTY"));
    }

    public boolean includesHotels() {
        return supplyTypes == null || supplyTypes.isEmpty() || supplyTypes.contains("HOTEL");
    }
}
