package mn.innex.stay.listing.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Registers an upload that storage has confirmed holding. */
public record PhotoConfirmRequest(
        @NotBlank @Size(max = 512) String storageKey,
        @Size(max = 255) String altText) {
}
