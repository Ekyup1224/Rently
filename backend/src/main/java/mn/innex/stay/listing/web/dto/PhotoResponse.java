package mn.innex.stay.listing.web.dto;

import java.util.UUID;

/**
 * @param url absolute, publicly readable URL. Photos are public content, so they
 *            are served from storage or a CDN rather than through the API.
 */
public record PhotoResponse(
        UUID id,
        String url,
        String altText,
        int sortOrder,
        boolean cover,
        Integer width,
        Integer height) {
}
