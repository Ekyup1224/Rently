package mn.innex.stay.listing.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import mn.innex.stay.listing.domain.PropertyStatus;

/**
 * Review decision on a listing.
 *
 * @param reason required when rejecting or suspending — an owner cannot fix a
 *               listing without being told what is wrong
 */
public record AdminPropertyStatusRequest(
        @NotNull PropertyStatus status,
        @Size(max = 2000) String reason) {
}
