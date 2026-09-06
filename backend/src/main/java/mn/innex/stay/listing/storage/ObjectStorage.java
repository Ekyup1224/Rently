package mn.innex.stay.listing.storage;

import java.time.Duration;

/**
 * The storage operations the listing module needs, kept behind an interface so
 * photo handling can be tested without a bucket and so the S3 SDK does not leak
 * into services.
 */
public interface ObjectStorage {

    /**
     * Issues a short-lived URL the browser can PUT a file to directly, so image
     * bytes never transit the API.
     *
     * @param contentType must match the header the browser sends, or S3 rejects the PUT
     */
    PresignedUpload presignUpload(String key, String contentType, Duration ttl);

    /**
     * Reads back what storage actually holds for a key.
     *
     * @return empty when no object exists at that key
     */
    java.util.Optional<StoredObject> describe(String key);

    void delete(String key);

    /** Public URL for a stored object. */
    String publicUrl(String key);

    record PresignedUpload(String url, String key, java.time.Instant expiresAt) {
    }

    record StoredObject(String key, String contentType, long sizeBytes) {
    }
}
