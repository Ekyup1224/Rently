package mn.innex.stay.listing.web.dto;

import java.time.Instant;

/**
 * Where to PUT the file. The browser uploads straight to storage, so image bytes
 * never pass through the API.
 *
 * @param storageKey echo this back to the confirm endpoint once the PUT succeeds
 */
public record PhotoUploadUrlResponse(String uploadUrl, String storageKey, Instant expiresAt) {
}
