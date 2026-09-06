package mn.innex.stay.listing.web;

import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import mn.innex.stay.common.web.ClientIp;
import mn.innex.stay.listing.domain.PropertyPhoto;
import mn.innex.stay.listing.service.PhotoService;
import mn.innex.stay.listing.storage.ObjectStorage;
import mn.innex.stay.listing.web.dto.PhotoConfirmRequest;
import mn.innex.stay.listing.web.dto.PhotoOrderRequest;
import mn.innex.stay.listing.web.dto.PhotoResponse;
import mn.innex.stay.listing.web.dto.PhotoUploadUrlRequest;
import mn.innex.stay.listing.web.dto.PhotoUploadUrlResponse;
import mn.innex.stay.security.CurrentActor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Listing photos.
 *
 * <p>Two-step upload: ask for a presigned URL, PUT the file straight to storage,
 * then confirm. The API never carries image bytes, and confirmation verifies with
 * storage that the object is really there before creating a row.
 */
@RestController
@RequestMapping("/api/v1/owner/properties/{propertyId}/photos")
@PreAuthorize("hasRole('HOUSE_OWNER')")
public class OwnerPhotoController {

    private final PhotoService photoService;
    private final ObjectStorage storage;

    public OwnerPhotoController(PhotoService photoService, ObjectStorage storage) {
        this.photoService = photoService;
        this.storage = storage;
    }

    /** Step one: a short-lived URL to PUT the file to. */
    @PostMapping("/upload-url")
    public PhotoUploadUrlResponse uploadUrl(@PathVariable UUID propertyId,
                                            @Valid @RequestBody PhotoUploadUrlRequest request) {
        return photoService.presignUpload(CurrentActor.requireUserId(), propertyId, request);
    }

    /** Step two, after the PUT succeeds: register it against the listing. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PhotoResponse confirm(@PathVariable UUID propertyId,
                                 @Valid @RequestBody PhotoConfirmRequest request,
                                 HttpServletRequest httpRequest) {
        return toResponse(photoService.confirmUpload(
                CurrentActor.requireUserId(), propertyId, request, ClientIp.of(httpRequest)));
    }

    /** Reorders the gallery and optionally picks the cover. */
    @PatchMapping("/order")
    public List<PhotoResponse> reorder(@PathVariable UUID propertyId,
                                       @Valid @RequestBody PhotoOrderRequest request,
                                       HttpServletRequest httpRequest) {
        return photoService.reorder(CurrentActor.requireUserId(), propertyId, request,
                        ClientIp.of(httpRequest)).stream()
                .map(this::toResponse)
                .toList();
    }

    @DeleteMapping("/{photoId}")
    public ResponseEntity<Void> delete(@PathVariable UUID propertyId, @PathVariable UUID photoId,
                                       HttpServletRequest httpRequest) {
        photoService.delete(CurrentActor.requireUserId(), propertyId, photoId,
                ClientIp.of(httpRequest));
        return ResponseEntity.noContent().build();
    }

    private PhotoResponse toResponse(PropertyPhoto photo) {
        return new PhotoResponse(photo.getId(), storage.publicUrl(photo.getStorageKey()),
                photo.getAltText(), photo.getSortOrder(), photo.isCover(),
                photo.getWidth(), photo.getHeight());
    }
}
