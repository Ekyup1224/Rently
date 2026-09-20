package mn.innex.stay.search.repo;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One search result, flattened across supply types.
 *
 * <p>The union of two quite different things, so fields that only make sense for
 * one are null for the other: {@code bedrooms} and {@code propertyType} for a
 * house, {@code starRating} and {@code roomTypeCount} for a hotel. The client
 * branches on {@code supplyType} rather than guessing.
 *
 * @param nightlyFrom for a hotel, the cheapest sellable room type's base price
 * @param maxGuests   for a hotel, the largest room type's capacity
 */
public record SupplySearchRow(
        String supplyType,
        UUID id,
        String title,
        String propertyType,
        Integer starRating,
        String city,
        String district,
        Double latitude,
        Double longitude,
        BigDecimal nightlyFrom,
        BigDecimal cleaningFee,
        String currency,
        int maxGuests,
        Integer bedrooms,
        Integer beds,
        BigDecimal bathrooms,
        boolean instantBook,
        String cancellationPolicy,
        int minStayNights,
        String coverStorageKey,
        int photoCount,
        Integer roomTypeCount,
        /** Null until this listing has a published review. */
        BigDecimal ratingAverage,
        int ratingCount) {
}
