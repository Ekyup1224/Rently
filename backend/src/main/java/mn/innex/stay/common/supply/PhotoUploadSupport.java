package mn.innex.stay.common.supply;

import java.util.Map;
import java.util.UUID;

import mn.innex.stay.common.ApiException;
import mn.innex.stay.listing.storage.ObjectStorage;
import mn.innex.stay.listing.storage.S3Properties;
import org.springframework.stereotype.Component;

/**
 * The storage half of a photo upload, shared by property, hotel and room-type
 * galleries.
 *
 * <p>All three follow the same two-step flow — presign, PUT straight to storage,
 * then confirm — and the interesting parts are identical: the size and type
 * limits, and refusing to register a key that storage does not actually hold or
 * that belongs to a different owner. Only the row each one writes differs, so
 * that stays with the owning service.
 */
@Component
public class PhotoUploadSupport {

    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp");

    private final ObjectStorage storage;
    private final S3Properties properties;

    public PhotoUploadSupport(ObjectStorage storage, S3Properties properties) {
        this.storage = storage;
        this.properties = properties;
    }

    /**
     * Issues a short-lived upload URL under {@code prefix/ownerId/}.
     *
     * <p>The declared size is checked here so an oversized file is refused before
     * it is transferred, and again on confirmation against what storage really
     * received — a declaration is a courtesy, not a control.
     *
     * @param prefix       storage prefix for this kind of gallery, e.g. {@code hotels/}
     * @param currentCount photos already registered, against {@code maxPhotos}
     */
    public PresignedUpload presign(String prefix, UUID ownerId, String contentType,
                                   long declaredSizeBytes, long currentCount, int maxPhotos) {
        if (declaredSizeBytes > properties.maxPhotoBytes()) {
            throw ApiException.badRequest("photo_too_large",
                    "Photos must be " + (properties.maxPhotoBytes() / 1_048_576) + " MB or smaller");
        }
        if (currentCount >= maxPhotos) {
            throw ApiException.conflict("photo_limit_reached",
                    "At most " + maxPhotos + " photos are allowed here");
        }

        String extension = EXTENSIONS.get(contentType);
        if (extension == null) {
            throw ApiException.badRequest("unsupported_image_type",
                    "Supported image types: " + EXTENSIONS.keySet());
        }

        String key = "%s%s/%s.%s".formatted(prefix, ownerId, UUID.randomUUID(), extension);
        ObjectStorage.PresignedUpload upload =
                storage.presignUpload(key, contentType, properties.presignTtl());
        return new PresignedUpload(upload.url(), upload.key(), upload.expiresAt());
    }

    /**
     * Confirms storage really holds the object, and that the key belongs to this
     * owner.
     *
     * <p>The prefix check is what stops someone registering another gallery's
     * object simply by knowing its key. An object that turns out to be oversized
     * or the wrong type is deleted rather than left orphaned in the bucket.
     *
     * @throws ApiException 400 when the object is missing, oversized or not an image
     */
    public ObjectStorage.StoredObject verifyUploaded(String prefix, UUID ownerId, String storageKey) {
        String expectedPrefix = prefix + ownerId + "/";
        if (storageKey == null || !storageKey.startsWith(expectedPrefix)) {
            throw ApiException.badRequest("storage_key_mismatch",
                    "That storage key does not belong here");
        }

        ObjectStorage.StoredObject stored = storage.describe(storageKey).orElseThrow(
                () -> ApiException.badRequest("upload_not_found",
                        "No uploaded file was found at that key. Upload it before confirming."));

        if (stored.sizeBytes() > properties.maxPhotoBytes()) {
            storage.delete(storageKey);
            throw ApiException.badRequest("photo_too_large",
                    "The uploaded file exceeds the size limit and has been discarded");
        }
        if (stored.contentType() != null && !EXTENSIONS.containsKey(stored.contentType())) {
            storage.delete(storageKey);
            throw ApiException.badRequest("unsupported_image_type",
                    "The uploaded file is not a supported image type");
        }
        return stored;
    }

    /** Removes a stored object. Failures are logged by the storage layer, not thrown. */
    public void delete(String storageKey) {
        storage.delete(storageKey);
    }

    public String publicUrl(String storageKey) {
        return storage.publicUrl(storageKey);
    }

    /**
     * Where to PUT the file.
     *
     * <p>Field names match the house gallery's response exactly, so a client has
     * one shape to handle rather than one per kind of photo.
     *
     * @param storageKey echo this back to the confirm endpoint once the PUT succeeds
     */
    public record PresignedUpload(String uploadUrl, String storageKey,
                                  java.time.Instant expiresAt) {
    }
}
