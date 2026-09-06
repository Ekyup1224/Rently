package mn.innex.stay.listing.repo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import mn.innex.stay.listing.domain.Amenity;
import mn.innex.stay.listing.domain.PropertyType;

/**
 * Everything the guest search can filter on.
 *
 * @param text        free text matched against title, city, district and description
 * @param city        exact-ish city match, separate from {@code text}
 * @param checkIn     inclusive; when set with {@code checkOut}, only listings free
 *                    for the whole range are returned
 * @param checkOut    exclusive, the morning the guest leaves
 * @param guests      minimum capacity required
 * @param types       any of these property types, or all when empty
 * @param amenities   listings must have *all* of these
 * @param instantBook when true, only listings that book without host approval
 * @param latitude    with {@code longitude} and {@code radiusKm}, restricts to a
 *                    bounding box around the point and orders by distance
 * @param sort        one of price_asc, price_desc, newest, distance, relevance
 */
public record PropertySearchCriteria(
        String text,
        String city,
        LocalDate checkIn,
        LocalDate checkOut,
        Integer guests,
        List<PropertyType> types,
        List<Amenity> amenities,
        Boolean instantBook,
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
        return hasDates() ? java.time.temporal.ChronoUnit.DAYS.between(checkIn, checkOut) : 0;
    }
}
