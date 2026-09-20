package mn.innex.stay.hotel.service;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import mn.innex.stay.common.ApiException;
import mn.innex.stay.common.audit.AuditAction;
import mn.innex.stay.common.audit.AuditService;
import mn.innex.stay.common.supply.PhotoReviewPort;
import mn.innex.stay.common.supply.PhotoUploadSupport;
import mn.innex.stay.common.supply.SupplyKind;
import mn.innex.stay.hotel.domain.Hotel;
import mn.innex.stay.hotel.domain.HotelPhoto;
import mn.innex.stay.hotel.domain.RoomType;
import mn.innex.stay.hotel.domain.RoomTypePhoto;
import mn.innex.stay.hotel.repo.HotelPhotoRepository;
import mn.innex.stay.hotel.repo.HotelRepository;
import mn.innex.stay.hotel.repo.RoomTypePhotoRepository;
import mn.innex.stay.hotel.repo.RoomTypeRepository;
import mn.innex.stay.hotel.web.dto.HotelRequests;
import mn.innex.stay.listing.storage.ObjectStorage;
import mn.innex.stay.listing.storage.S3Properties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Photos for hotels and for their room types.
 *
 * <p>Both galleries share the storage work through {@link PhotoUploadSupport} —
 * presigning, size and type limits, and refusing a key that storage does not hold
 * or that belongs to something else. Only the row differs, so both live here
 * rather than in two near-identical services.
 */
@Service
public class HotelPhotoService {

    // Shared with the bucket policy, so a gallery cannot be opened for uploads
    // without also being readable.
    private static final String HOTEL_PREFIX = S3Properties.HOTEL_PHOTO_PREFIX;
    private static final String ROOM_TYPE_PREFIX = S3Properties.ROOM_TYPE_PHOTO_PREFIX;
    private static final int MAX_HOTEL_PHOTOS = 40;
    private static final int MAX_ROOM_TYPE_PHOTOS = 20;

    private final HotelAccessService access;
    private final RoomTypeService roomTypeService;
    private final HotelRepository hotelRepository;
    private final RoomTypeRepository roomTypeRepository;
    private final HotelPhotoRepository hotelPhotoRepository;
    private final RoomTypePhotoRepository roomTypePhotoRepository;
    private final PhotoUploadSupport uploads;
    private final AuditService auditService;
    private final ObjectProvider<PhotoReviewPort> photoReview;

    public HotelPhotoService(HotelAccessService access, RoomTypeService roomTypeService,
                             HotelRepository hotelRepository, RoomTypeRepository roomTypeRepository,
                             HotelPhotoRepository hotelPhotoRepository,
                             RoomTypePhotoRepository roomTypePhotoRepository,
                             PhotoUploadSupport uploads, AuditService auditService,
                             ObjectProvider<PhotoReviewPort> photoReview) {
        this.access = access;
        this.roomTypeService = roomTypeService;
        this.hotelRepository = hotelRepository;
        this.roomTypeRepository = roomTypeRepository;
        this.hotelPhotoRepository = hotelPhotoRepository;
        this.roomTypePhotoRepository = roomTypePhotoRepository;
        this.photoReview = photoReview;
        this.uploads = uploads;
        this.auditService = auditService;
    }

    // --- hotel photos -------------------------------------------------------

    @Transactional(readOnly = true)
    public PhotoUploadSupport.PresignedUpload presignHotelPhoto(
            UUID actorId, UUID hotelId, HotelRequests.PhotoUploadUrl request) {
        access.requireManagerForChange(actorId, hotelId);
        return uploads.presign(HOTEL_PREFIX, hotelId, request.contentType(), request.sizeBytes(),
                hotelPhotoRepository.countByHotelId(hotelId), MAX_HOTEL_PHOTOS);
    }

    @Transactional
    public HotelPhoto confirmHotelPhoto(UUID actorId, UUID hotelId,
                                        HotelRequests.ConfirmPhoto request, String ip) {
        Hotel hotel = access.requireManagerForChange(actorId, hotelId);
        if (hotelPhotoRepository.findByStorageKey(request.storageKey()).isPresent()) {
            throw ApiException.conflict("photo_already_registered",
                    "That upload has already been registered");
        }

        ObjectStorage.StoredObject stored =
                uploads.verifyUploaded(HOTEL_PREFIX, hotelId, request.storageKey());

        HotelPhoto photo = new HotelPhoto(hotel, request.storageKey(),
                stored.contentType() == null ? "image/jpeg" : stored.contentType(),
                stored.sizeBytes(), request.altText(),
                (int) hotelPhotoRepository.countByHotelId(hotelId));
        hotel.addPhoto(photo);
        // Saved directly so the id is assigned here and not to a merged copy.
        hotelPhotoRepository.saveAndFlush(photo);

        fingerprint(photo.getId(), SupplyKind.HOTEL, hotelId,
                hotel.getOrganization().getOwnerUser().getId(), request.storageKey());

        auditService.record(actorId, AuditAction.HOTEL_PHOTO_ADDED, "Hotel", hotelId,
                Map.of("storageKey", request.storageKey(), "sizeBytes", stored.sizeBytes()), ip);
        return photo;
    }

    /** Hands a freshly published photo to the trust module, if it is present. */
    private void fingerprint(UUID photoId, SupplyKind kind, UUID supplyId, UUID ownerUserId,
                             String storageKey) {
        PhotoReviewPort review = photoReview.getIfAvailable();
        if (review != null) {
            review.photoPublished(photoId, kind, supplyId, ownerUserId, storageKey);
        }
    }

    @Transactional
    public List<HotelPhoto> reorderHotelPhotos(UUID actorId, UUID hotelId,
                                               HotelRequests.ReorderPhotos request, String ip) {
        access.requireManagerForChange(actorId, hotelId);
        List<HotelPhoto> photos =
                hotelPhotoRepository.findByHotelIdOrderBySortOrderAscUploadedAtAsc(hotelId);

        Map<UUID, HotelPhoto> byId = new HashMap<>();
        photos.forEach(photo -> byId.put(photo.getId(), photo));
        assertCompleteOrder(request.photoIdsInOrder(), byId.keySet());

        for (int index = 0; index < request.photoIdsInOrder().size(); index++) {
            byId.get(request.photoIdsInOrder().get(index)).setSortOrder(index);
        }
        if (request.coverPhotoId() != null) {
            HotelPhoto cover = byId.get(request.coverPhotoId());
            if (cover == null) {
                throw ApiException.badRequest("photo_not_found", "That photo is not on this hotel");
            }
            hotelPhotoRepository.clearCover(hotelId);
            photos.forEach(photo -> photo.markAsCover(false));
            cover.markAsCover(true);
        }

        hotelPhotoRepository.saveAll(photos);
        auditService.record(actorId, AuditAction.HOTEL_UPDATED, "Hotel", hotelId,
                Map.of("photosReordered", photos.size()), ip);
        return photos;
    }

    @Transactional
    public void deleteHotelPhoto(UUID actorId, UUID hotelId, UUID photoId, String ip) {
        Hotel hotel = access.requireManagerForChange(actorId, hotelId);
        HotelPhoto photo = hotel.getPhotos().stream()
                .filter(candidate -> candidate.getId().equals(photoId))
                .findFirst()
                .orElseThrow(() -> ApiException.notFound("photo_not_found", "Photo not found"));

        boolean wasCover = photo.isCover();
        String key = photo.getStorageKey();

        hotel.getPhotos().remove(photo);
        hotelPhotoRepository.delete(photo);
        hotelPhotoRepository.flush();

        if (wasCover) {
            hotel.getPhotos().stream()
                    .min(Comparator.comparingInt(HotelPhoto::getSortOrder))
                    .ifPresent(next -> next.markAsCover(true));
        }
        hotelRepository.save(hotel);
        uploads.delete(key);

        auditService.record(actorId, AuditAction.HOTEL_PHOTO_REMOVED, "Hotel", hotelId,
                Map.of("photoId", photoId.toString(), "wasCover", wasCover), ip);
    }

    // --- room type photos ---------------------------------------------------

    @Transactional(readOnly = true)
    public PhotoUploadSupport.PresignedUpload presignRoomTypePhoto(
            UUID actorId, UUID roomTypeId, HotelRequests.PhotoUploadUrl request) {
        roomTypeService.requireManaged(actorId, roomTypeId);
        return uploads.presign(ROOM_TYPE_PREFIX, roomTypeId, request.contentType(),
                request.sizeBytes(), roomTypePhotoRepository.countByRoomTypeId(roomTypeId),
                MAX_ROOM_TYPE_PHOTOS);
    }

    @Transactional
    public RoomTypePhoto confirmRoomTypePhoto(UUID actorId, UUID roomTypeId,
                                              HotelRequests.ConfirmPhoto request, String ip) {
        RoomType roomType = roomTypeService.requireManaged(actorId, roomTypeId);
        if (roomTypePhotoRepository.findByStorageKey(request.storageKey()).isPresent()) {
            throw ApiException.conflict("photo_already_registered",
                    "That upload has already been registered");
        }

        ObjectStorage.StoredObject stored =
                uploads.verifyUploaded(ROOM_TYPE_PREFIX, roomTypeId, request.storageKey());

        RoomTypePhoto photo = new RoomTypePhoto(roomType, request.storageKey(),
                stored.contentType() == null ? "image/jpeg" : stored.contentType(),
                stored.sizeBytes(), request.altText(),
                (int) roomTypePhotoRepository.countByRoomTypeId(roomTypeId));
        roomType.addPhoto(photo);
        roomTypePhotoRepository.saveAndFlush(photo);

        fingerprint(photo.getId(), SupplyKind.ROOM_TYPE, roomTypeId,
                roomType.getHotel().getOrganization().getOwnerUser().getId(),
                request.storageKey());

        auditService.record(actorId, AuditAction.HOTEL_PHOTO_ADDED, "RoomType", roomTypeId,
                Map.of("storageKey", request.storageKey()), ip);
        return photo;
    }

    @Transactional
    public void deleteRoomTypePhoto(UUID actorId, UUID roomTypeId, UUID photoId, String ip) {
        RoomType roomType = roomTypeService.requireManaged(actorId, roomTypeId);
        RoomTypePhoto photo = roomType.getPhotos().stream()
                .filter(candidate -> candidate.getId().equals(photoId))
                .findFirst()
                .orElseThrow(() -> ApiException.notFound("photo_not_found", "Photo not found"));

        boolean wasCover = photo.isCover();
        String key = photo.getStorageKey();

        roomType.getPhotos().remove(photo);
        roomTypePhotoRepository.delete(photo);
        roomTypePhotoRepository.flush();

        if (wasCover) {
            roomType.getPhotos().stream()
                    .min(Comparator.comparingInt(RoomTypePhoto::getSortOrder))
                    .ifPresent(next -> next.markAsCover(true));
        }
        roomTypeRepository.save(roomType);
        uploads.delete(key);

        auditService.record(actorId, AuditAction.HOTEL_PHOTO_REMOVED, "RoomType", roomTypeId,
                Map.of("photoId", photoId.toString(), "wasCover", wasCover), ip);
    }

    public String publicUrl(String storageKey) {
        return uploads.publicUrl(storageKey);
    }

    private void assertCompleteOrder(List<UUID> requested, java.util.Set<UUID> known) {
        if (requested.size() != known.size() || !known.containsAll(requested)) {
            throw ApiException.badRequest("photo_order_mismatch",
                    "The order must list every photo here exactly once");
        }
    }
}
