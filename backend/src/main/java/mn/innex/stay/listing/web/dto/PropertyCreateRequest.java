package mn.innex.stay.listing.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import mn.innex.stay.listing.domain.PropertyType;

/**
 * The minimum needed to start a listing. Everything else is filled in before
 * submitting for review, so an owner can save a draft after one screen.
 */
public record PropertyCreateRequest(
        @NotBlank @Size(max = 150) String title,
        @NotNull PropertyType propertyType,
        @NotBlank @Size(max = 120) String city,
        @Positive int maxGuests) {
}
