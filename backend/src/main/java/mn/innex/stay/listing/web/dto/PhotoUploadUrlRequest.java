package mn.innex.stay.listing.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/**
 * Asks for a presigned upload URL.
 *
 * @param contentType must be sent as the PUT's Content-Type header too, or
 *                    storage rejects the signature
 * @param sizeBytes   declared up front so an oversized file is refused before it
 *                    is uploaded rather than after
 */
public record PhotoUploadUrlRequest(
        @NotBlank @Pattern(regexp = "image/(jpeg|png|webp)",
                message = "contentType must be image/jpeg, image/png or image/webp")
        String contentType,
        @Positive long sizeBytes) {
}
