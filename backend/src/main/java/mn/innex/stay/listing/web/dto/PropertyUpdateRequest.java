package mn.innex.stay.listing.web.dto;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import mn.innex.stay.listing.domain.CancellationPolicy;
import mn.innex.stay.listing.domain.PropertyType;

/**
 * Partial update: every field is optional and null means "leave it alone".
 *
 * <p>Changing the location or property type of a live listing sends it back for
 * review, because those are the facts the approval was based on. Price, text,
 * photos and calendar can be changed freely while live.
 *
 * @param amenities replaces the whole set when present, rather than merging, so
 *                  removing an amenity is expressible
 */
public record PropertyUpdateRequest(
        @Size(max = 150) String title,
        @Size(max = 8000) String description,
        PropertyType propertyType,
        @Min(1) @Max(50) Integer maxGuests,
        @Min(0) Integer bedrooms,
        @Min(1) Integer beds,
        @DecimalMin("0") BigDecimal bathrooms,
        @Size(max = 255) String addressLine,
        @Size(max = 120) String district,
        @Size(max = 120) String city,
        Double latitude,
        Double longitude,
        List<String> amenities,
        @Size(max = 4000) String houseRules,
        LocalTime checkInFrom,
        LocalTime checkOutBy,
        @DecimalMin("0") BigDecimal basePrice,
        @DecimalMin("0") BigDecimal cleaningFee,
        @Min(1) Integer minStayNights,
        @Min(1) Integer maxStayNights,
        CancellationPolicy cancellationPolicy,
        Boolean instantBook) {
}
