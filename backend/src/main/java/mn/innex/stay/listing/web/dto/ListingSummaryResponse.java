package mn.innex.stay.listing.web.dto;

import java.math.BigDecimal;
import java.util.UUID;

import mn.innex.stay.listing.domain.PropertyType;
import mn.innex.stay.listing.repo.PropertySearchRepository;
import mn.innex.stay.listing.storage.ObjectStorage;

/**
 * One search result card. Deliberately narrow: a results page renders dozens of
 * these, so it carries what a card shows and nothing more.
 *
 * @param nightlyFrom the base nightly price. When the search had dates, the exact
 *                    total comes from the quote endpoint on the detail page —
 *                    per-day overrides make a list-level total unreliable.
 */
public record ListingSummaryResponse(
        UUID id,
        String title,
        PropertyType propertyType,
        String city,
        String district,
        Double latitude,
        Double longitude,
        BigDecimal nightlyFrom,
        BigDecimal cleaningFee,
        String currency,
        int maxGuests,
        int bedrooms,
        int beds,
        BigDecimal bathrooms,
        boolean instantBook,
        String cancellationPolicy,
        int minStayNights,
        String coverPhotoUrl,
        int photoCount) {

    public static ListingSummaryResponse from(PropertySearchRepository.PropertySearchRow row,
                                              ObjectStorage storage) {
        return new ListingSummaryResponse(
                row.id(), row.title(), row.propertyType(), row.city(), row.district(),
                row.latitude(), row.longitude(), row.basePrice(), row.cleaningFee(), row.currency(),
                row.maxGuests(), row.bedrooms(), row.beds(), row.bathrooms(), row.instantBook(),
                row.cancellationPolicy(), row.minStayNights(),
                row.coverStorageKey() == null ? null : storage.publicUrl(row.coverStorageKey()),
                row.photoCount());
    }
}
