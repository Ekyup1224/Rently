package mn.innex.stay.search.web;

import java.math.BigDecimal;
import java.util.UUID;

import mn.innex.stay.listing.storage.ObjectStorage;
import mn.innex.stay.search.repo.SupplySearchRow;

/**
 * One card in the unified result list.
 *
 * <p>{@code supplyType} tells the client what it is looking at and which link to
 * follow — {@code /listings/{id}} for a house, {@code /hotels/{id}} for a hotel.
 * Fields that only apply to one type are null for the other rather than faked.
 *
 * @param nightlyFrom for a hotel, the cheapest sellable room; the exact total for
 *                    a stay comes from the quote or availability endpoint, because
 *                    per-night overrides make list-level arithmetic wrong
 */
public record SupplySummaryResponse(
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
        String coverPhotoUrl,
        int photoCount,
        Integer roomTypeCount,
        BigDecimal ratingAverage,
        int ratingCount) {

    public static SupplySummaryResponse from(SupplySearchRow row, ObjectStorage storage) {
        return new SupplySummaryResponse(
                row.supplyType(), row.id(), row.title(), row.propertyType(), row.starRating(),
                row.city(), row.district(), row.latitude(), row.longitude(), row.nightlyFrom(),
                row.cleaningFee(), row.currency(), row.maxGuests(), row.bedrooms(), row.beds(),
                row.bathrooms(), row.instantBook(), row.cancellationPolicy(), row.minStayNights(),
                row.coverStorageKey() == null ? null : storage.publicUrl(row.coverStorageKey()),
                row.photoCount(), row.roomTypeCount(), row.ratingAverage(), row.ratingCount());
    }
}
