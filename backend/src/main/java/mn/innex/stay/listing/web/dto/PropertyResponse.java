package mn.innex.stay.listing.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import mn.innex.stay.listing.domain.CancellationPolicy;
import mn.innex.stay.listing.domain.Property;
import mn.innex.stay.listing.domain.PropertyStatus;
import mn.innex.stay.listing.domain.PropertyType;
import mn.innex.stay.listing.storage.ObjectStorage;

/**
 * A listing as its owner or an admin sees it: everything, including review state.
 * The guest-facing view is {@link ListingDetailResponse}.
 *
 * @param readinessProblems what still blocks submitting for review, so the owner's
 *                          form can show a checklist instead of a rejection
 */
public record PropertyResponse(
        UUID id,
        UUID ownerId,
        String title,
        String description,
        PropertyType propertyType,
        int maxGuests,
        int bedrooms,
        int beds,
        BigDecimal bathrooms,
        String addressLine,
        String district,
        String city,
        String country,
        Double latitude,
        Double longitude,
        List<String> amenities,
        String houseRules,
        LocalTime checkInFrom,
        LocalTime checkOutBy,
        BigDecimal basePrice,
        BigDecimal cleaningFee,
        String currency,
        int minStayNights,
        Integer maxStayNights,
        CancellationPolicy cancellationPolicy,
        boolean instantBook,
        PropertyStatus status,
        String rejectionReason,
        List<String> readinessProblems,
        List<PhotoResponse> photos,
        Instant publishedAt,
        Instant createdAt,
        Instant updatedAt) {

    public static PropertyResponse from(Property property, ObjectStorage storage) {
        List<PhotoResponse> photos = property.getPhotos().stream()
                .map(photo -> new PhotoResponse(photo.getId(), storage.publicUrl(photo.getStorageKey()),
                        photo.getAltText(), photo.getSortOrder(), photo.isCover(),
                        photo.getWidth(), photo.getHeight()))
                .toList();
        return new PropertyResponse(
                property.getId(), property.getOwner().getId(), property.getTitle(),
                property.getDescription(), property.getPropertyType(), property.getMaxGuests(),
                property.getBedrooms(), property.getBeds(), property.getBathrooms(),
                property.getAddressLine(), property.getDistrict(), property.getCity(),
                property.getCountry(), property.getLatitude(), property.getLongitude(),
                property.getAmenities(), property.getHouseRules(), property.getCheckInFrom(),
                property.getCheckOutBy(), property.getBasePrice(), property.getCleaningFee(),
                property.getCurrency(), property.getMinStayNights(), property.getMaxStayNights(),
                property.getCancellationPolicy(), property.isInstantBook(), property.getStatus(),
                property.getRejectionReason(), property.reviewReadinessProblems(), photos,
                property.getPublishedAt(), property.getCreatedAt(), property.getUpdatedAt());
    }
}
