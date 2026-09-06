package mn.innex.stay.listing.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import mn.innex.stay.common.ApiException;
import mn.innex.stay.common.audit.AuditAction;
import mn.innex.stay.common.audit.AuditService;
import mn.innex.stay.listing.domain.Property;
import mn.innex.stay.listing.domain.PropertyPhoto;
import mn.innex.stay.listing.repo.PropertyPhotoRepository;
import mn.innex.stay.listing.repo.PropertyRepository;
import mn.innex.stay.listing.storage.ObjectStorage;
import mn.innex.stay.listing.storage.S3Properties;
import mn.innex.stay.listing.web.dto.PhotoConfirmRequest;
import mn.innex.stay.listing.web.dto.PhotoOrderRequest;
import mn.innex.stay.listing.web.dto.PhotoUploadUrlRequest;
import mn.innex.stay.listing.web.dto.PhotoUploadUrlResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Listing photos, uploaded browser-to-bucket.
 *
 * <p>The flow is: ask for a presigned URL, PUT the file straight to storage, then
 * confirm. Image bytes never pass through the API, which keeps large uploads off
 * the application's heap and its bandwidth.
 *
 * <p>Confirmation is not taken on trust. Storage is asked what it actually holds
 * at that key, and the key must sit under the listing's own prefix — otherwise an
 * owner could confirm someone else's object, or a row could point at nothing.
 */
@Service
public class PhotoService {

    private static final Logger log = LoggerFactory.getLogger(PhotoService.class);

    /** Enough for a generous gallery; beyond this a listing page is unusable anyway. */
    private static final int MAX_PHOTOS_PER_LISTING = 30;

    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp");

    private final PropertyService propertyService;
    private final PropertyRepository propertyRepository;
    private final PropertyPhotoRepository photoRepository;
    private final ObjectStorage storage;
    private final S3Properties storageProperties;
    private final AuditService auditService;

    public PhotoService(PropertyService propertyService, PropertyRepository propertyRepository,
                        PropertyPhotoRepository photoRepository, ObjectStorage storage,
                        S3Properties storageProperties, AuditService auditService) {
        this.propertyService = propertyService;
        this.propertyRepository = propertyRepository;
        this.photoRepository = photoRepository;
        this.storage = storage;
        this.storageProperties = storageProperties;
        this.auditService = auditService;
    }

    /**
     * Issues a short-lived upload URL.
     *
     * <p>The declared size is checked here so an oversized file is refused before
     * it is transferred, and again on confirmation against what storage really
     * received — the declaration is a courtesy, not a control.
     */
    @Transactional(readOnly = true)
    public PhotoUploadUrlResponse presignUpload(UUID ownerId, UUID propertyId,
                                                PhotoUploadUrlRequest request) {
        propertyService.requireOwned(ownerId, propertyId);

        if (request.sizeBytes() > storageProperties.maxPhotoBytes()) {
            throw ApiException.badRequest("photo_too_large",
                    "Photos must be " + (storageProperties.maxPhotoBytes() / 1_048_576)
                            + " MB or smaller");
        }
        if (photoRepository.countByPropertyId(propertyId) >= MAX_PHOTOS_PER_LISTING) {
            throw ApiException.conflict("photo_limit_reached",
                    "A listing can have at most " + MAX_PHOTOS_PER_LISTING + " photos");
        }

        String extension = EXTENSIONS.get(request.contentType());
        if (extension == null) {
            throw ApiException.badRequest("unsupported_image_type",
                    "Supported image types: " + EXTENSIONS.keySet());
        }

        String key = "%s%s/%s.%s".formatted(
                S3Properties.PHOTO_PREFIX, propertyId, UUID.randomUUID(), extension);
        ObjectStorage.PresignedUpload upload =
                storage.presignUpload(key, request.contentType(), storageProperties.presignTtl());

        return new PhotoUploadUrlResponse(upload.url(), upload.key(), upload.expiresAt());
    }

    /**
     * Registers an uploaded file after verifying storage holds it.
     *
     * @throws ApiException 400 when the object is missing, oversized, or the key
     *                      does not belong to this listing
     */
    @Transactional
    public PropertyPhoto confirmUpload(UUID ownerId, UUID propertyId, PhotoConfirmRequest request,
                                       String ip) {
        Property property = propertyService.requireOwned(ownerId, propertyId);
        String key = request.storageKey();

        // Without this, an owner could confirm an object belonging to another
        // listing simply by knowing its key.
        String expectedPrefix = S3Properties.PHOTO_PREFIX + propertyId + "/";
        if (!key.startsWith(expectedPrefix)) {
            throw ApiException.badRequest("storage_key_mismatch",
                    "That storage key does not belong to this listing");
        }
        if (photoRepository.findByStorageKey(key).isPresent()) {
            throw ApiException.conflict("photo_already_registered",
                    "That upload has already been registered");
        }

        ObjectStorage.StoredObject stored = storage.describe(key).orElseThrow(
                () -> ApiException.badRequest("upload_not_found",
                        "No uploaded file was found at that key. Upload it before confirming."));

        if (stored.sizeBytes() > storageProperties.maxPhotoBytes()) {
            // The declared size was under the cap but the real file is not; remove
            // it rather than leaving an orphan in the bucket.
            storage.delete(key);
            throw ApiException.badRequest("photo_too_large",
                    "The uploaded file exceeds the size limit and has been discarded");
        }
        if (stored.contentType() != null && !EXTENSIONS.containsKey(stored.contentType())) {
            storage.delete(key);
            throw ApiException.badRequest("unsupported_image_type",
                    "The uploaded file is not a supported image type");
        }

        int nextSortOrder = (int) photoRepository.countByPropertyId(propertyId);
        PropertyPhoto photo = new PropertyPhoto(property, key,
                stored.contentType() == null ? "image/jpeg" : stored.contentType(),
                stored.sizeBytes(), request.altText(), nextSortOrder);
        property.addPhoto(photo);
        propertyRepository.save(property);

        auditService.record(ownerId, AuditAction.PROPERTY_PHOTO_ADDED, "Property", propertyId,
                Map.of("storageKey", key, "sizeBytes", stored.sizeBytes()), ip);
        return photo;
    }

    /** Reorders the gallery and optionally sets a new cover. */
    @Transactional
    public List<PropertyPhoto> reorder(UUID ownerId, UUID propertyId, PhotoOrderRequest request,
                                       String ip) {
        propertyService.requireOwned(ownerId, propertyId);
        List<PropertyPhoto> photos =
                photoRepository.findByPropertyIdOrderBySortOrderAscUploadedAtAsc(propertyId);

        Map<UUID, PropertyPhoto> byId = new java.util.HashMap<>();
        photos.forEach(photo -> byId.put(photo.getId(), photo));

        if (request.photoIdsInOrder().size() != photos.size()
                || !byId.keySet().containsAll(request.photoIdsInOrder())) {
            throw ApiException.badRequest("photo_order_mismatch",
                    "The order must list every photo on this listing exactly once");
        }

        for (int index = 0; index < request.photoIdsInOrder().size(); index++) {
            byId.get(request.photoIdsInOrder().get(index)).setSortOrder(index);
        }

        if (request.coverPhotoId() != null) {
            PropertyPhoto cover = byId.get(request.coverPhotoId());
            if (cover == null) {
                throw ApiException.badRequest("photo_not_found",
                        "The cover photo is not on this listing");
            }
            // The partial unique index allows only one cover, so clear first.
            photoRepository.clearCover(propertyId);
            photos.forEach(photo -> photo.markAsCover(false));
            cover.markAsCover(true);
        }

        photoRepository.saveAll(photos);
        auditService.record(ownerId, AuditAction.PROPERTY_UPDATED, "Property", propertyId,
                Map.of("photosReordered", photos.size(),
                        "coverChanged", request.coverPhotoId() != null), ip);
        return photos;
    }

    /**
     * Removes a photo and its stored object. If it was the cover, the next photo
     * takes over so a listing never ends up with a gallery and no cover.
     */
    @Transactional
    public void delete(UUID ownerId, UUID propertyId, UUID photoId, String ip) {
        Property property = propertyService.requireOwned(ownerId, propertyId);

        Optional<PropertyPhoto> found = property.getPhotos().stream()
                .filter(photo -> photo.getId().equals(photoId))
                .findFirst();
        PropertyPhoto photo = found.orElseThrow(
                () -> ApiException.notFound("photo_not_found", "Photo not found"));

        boolean wasCover = photo.isCover();
        String key = photo.getStorageKey();

        property.getPhotos().remove(photo);
        photoRepository.delete(photo);
        photoRepository.flush();

        if (wasCover) {
            property.getPhotos().stream()
                    .min(java.util.Comparator.comparingInt(PropertyPhoto::getSortOrder))
                    .ifPresent(next -> next.markAsCover(true));
        }
        propertyRepository.save(property);

        // After the row is gone: an orphaned object costs pennies, a row pointing at
        // nothing breaks the listing page.
        storage.delete(key);

        auditService.record(ownerId, AuditAction.PROPERTY_PHOTO_REMOVED, "Property", propertyId,
                Map.of("photoId", photoId.toString(), "wasCover", wasCover), ip);
        log.debug("Removed photo {} from listing {}", photoId, propertyId);
    }
}
