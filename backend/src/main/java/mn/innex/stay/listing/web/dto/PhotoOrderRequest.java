package mn.innex.stay.listing.web.dto;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotEmpty;

/**
 * Reorders a listing's gallery and optionally sets the cover.
 *
 * @param photoIdsInOrder every photo on the listing, in the order to display them
 * @param coverPhotoId    which one leads the search results; null keeps the current
 */
public record PhotoOrderRequest(
        @NotEmpty List<UUID> photoIdsInOrder,
        UUID coverPhotoId) {
}
