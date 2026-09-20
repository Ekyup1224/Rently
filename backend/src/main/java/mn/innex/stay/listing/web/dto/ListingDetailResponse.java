package mn.innex.stay.listing.web.dto;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import mn.innex.stay.common.supply.CancellationPolicy;
import mn.innex.stay.listing.domain.Property;
import mn.innex.stay.listing.domain.PropertyType;
import mn.innex.stay.listing.storage.ObjectStorage;

/**
 * The guest-facing listing page.
 *
 * <p>Distinct from {@link PropertyResponse} because it must not leak owner-only
 * facts: no review state, no rejection reason, and no exact street address — the
 * precise location is disclosed after booking, which is the norm for whole-home
 * rentals and a safety expectation for hosts.
 *
 * @param host minimal host identity: a first name and how long they have hosted.
 *             Contact details arrive with messaging in Step 5.
 */
public record ListingDetailResponse(
        UUID id,
        String title,
        String description,
        PropertyType propertyType,
        int maxGuests,
        int bedrooms,
        int beds,
        BigDecimal bathrooms,
        String district,
        String city,
        String country,
        Double latitude,
        Double longitude,
        List<String> amenities,
        String houseRules,
        LocalTime checkInFrom,
        LocalTime checkOutBy,
        BigDecimal nightlyFrom,
        BigDecimal cleaningFee,
        String currency,
        int minStayNights,
        Integer maxStayNights,
        CancellationPolicy cancellationPolicy,
        boolean instantBook,
        List<PhotoResponse> photos,
        /** Null until the first review of this listing is published. */
        BigDecimal ratingAverage,
        int ratingCount,
        HostSummary host) {

    /** @param since year the host joined, e.g. "2026" */
    public record HostSummary(String displayName, String since, boolean identityVerified) {
    }

    public static ListingDetailResponse from(Property property, ObjectStorage storage) {
        List<PhotoResponse> photos = property.getPhotos().stream()
                .map(photo -> new PhotoResponse(photo.getId(), storage.publicUrl(photo.getStorageKey()),
                        photo.getAltText(), photo.getSortOrder(), photo.isCover(),
                        photo.getWidth(), photo.getHeight()))
                .toList();

        String fullName = property.getOwner().getFullName();
        // First name only: a listing page should not publish a host's full identity.
        String displayName = fullName == null || fullName.isBlank()
                ? "Host"
                : fullName.trim().split("\\s+")[0];

        return new ListingDetailResponse(
                property.getId(), property.getTitle(), property.getDescription(),
                property.getPropertyType(), property.getMaxGuests(), property.getBedrooms(),
                property.getBeds(), property.getBathrooms(), property.getDistrict(),
                property.getCity(), property.getCountry(), property.getLatitude(),
                property.getLongitude(), property.getAmenities(), property.getHouseRules(),
                property.getCheckInFrom(), property.getCheckOutBy(), property.getBasePrice(),
                property.getCleaningFee(), property.getCurrency(), property.getMinStayNights(),
                property.getMaxStayNights(), property.getCancellationPolicy(),
                property.isInstantBook(), photos,
                property.getRatingAverage(), property.getRatingCount(),
                new HostSummary(displayName,
                        String.valueOf(property.getOwner().getCreatedAt()
                                .atZone(mn.innex.stay.common.PlatformTime.ZONE).getYear()),
                        property.getOwner().getKycStatus()
                                == mn.innex.stay.user.domain.KycStatus.VERIFIED));
    }
}
