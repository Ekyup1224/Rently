package mn.innex.stay.hotel.web;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import mn.innex.stay.common.supply.SupplyStatus;
import mn.innex.stay.common.web.ClientIp;
import mn.innex.stay.common.web.PageResponse;
import mn.innex.stay.hotel.service.HotelPhotoService;
import mn.innex.stay.hotel.service.HotelService;
import mn.innex.stay.hotel.service.HotelStaffService;
import mn.innex.stay.hotel.service.InventoryService;
import mn.innex.stay.hotel.service.RoomTypeService;
import mn.innex.stay.hotel.web.dto.HotelRequests;
import mn.innex.stay.hotel.web.dto.HotelResponses;
import mn.innex.stay.security.CurrentActor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The hotel side of the portal: hotels, room types, inventory and staff.
 *
 * <p>Either hotel role can reach this controller; the services decide what each
 * may actually do. Staff can read and work the front desk, while rates, inventory,
 * room types and staff changes are manager-only — enforced in
 * {@code HotelAccessService} rather than by URL, so the rule lives in one place.
 */
@RestController
@RequestMapping("/api/v1/hotel")
@PreAuthorize("hasAnyRole('HOTEL_MANAGER', 'HOTEL_STAFF')")
public class HotelManagementController {

    private final HotelService hotelService;
    private final RoomTypeService roomTypeService;
    private final InventoryService inventoryService;
    private final HotelPhotoService photoService;
    private final HotelStaffService staffService;

    public HotelManagementController(HotelService hotelService, RoomTypeService roomTypeService,
                                     InventoryService inventoryService,
                                     HotelPhotoService photoService,
                                     HotelStaffService staffService) {
        this.hotelService = hotelService;
        this.roomTypeService = roomTypeService;
        this.inventoryService = inventoryService;
        this.photoService = photoService;
        this.staffService = staffService;
    }

    // --- hotels -------------------------------------------------------------

    @PostMapping("/hotels")
    @ResponseStatus(HttpStatus.CREATED)
    public HotelResponses.ManagedHotel create(@Valid @RequestBody HotelRequests.CreateHotel request,
                                              HttpServletRequest httpRequest) {
        return HotelResponses.ManagedHotel.from(hotelService.create(
                CurrentActor.requireUserId(), request, ClientIp.of(httpRequest)), photoService);
    }

    @GetMapping("/hotels")
    public PageResponse<HotelResponses.ManagedHotel> list(
            @RequestParam(required = false) SupplyStatus status,
            @PageableDefault(size = 20, sort = "updatedAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return PageResponse.of(
                hotelService.listForActor(CurrentActor.requireUserId(), status, pageable),
                hotel -> HotelResponses.ManagedHotel.from(hotel, photoService));
    }

    @GetMapping("/hotels/{hotelId}")
    public HotelResponses.ManagedHotel get(@PathVariable UUID hotelId) {
        return HotelResponses.ManagedHotel.from(
                hotelService.requireForActor(CurrentActor.requireUserId(), hotelId), photoService);
    }

    /** Changing the address or coordinates of a live hotel returns it to review. */
    @PatchMapping("/hotels/{hotelId}")
    public HotelResponses.ManagedHotel update(@PathVariable UUID hotelId,
                                              @Valid @RequestBody HotelRequests.UpdateHotel request,
                                              HttpServletRequest httpRequest) {
        return HotelResponses.ManagedHotel.from(hotelService.update(
                CurrentActor.requireUserId(), hotelId, request, ClientIp.of(httpRequest)),
                photoService);
    }

    /** Refuses an incomplete hotel, including one with no sellable room type. */
    @PostMapping("/hotels/{hotelId}/submit")
    public HotelResponses.ManagedHotel submit(@PathVariable UUID hotelId,
                                              HttpServletRequest httpRequest) {
        return HotelResponses.ManagedHotel.from(hotelService.submitForReview(
                CurrentActor.requireUserId(), hotelId, ClientIp.of(httpRequest)), photoService);
    }

    @PostMapping("/hotels/{hotelId}/pause")
    public HotelResponses.ManagedHotel pause(@PathVariable UUID hotelId,
                                             HttpServletRequest httpRequest) {
        return HotelResponses.ManagedHotel.from(hotelService.pause(
                CurrentActor.requireUserId(), hotelId, ClientIp.of(httpRequest)), photoService);
    }

    @PostMapping("/hotels/{hotelId}/resume")
    public HotelResponses.ManagedHotel resume(@PathVariable UUID hotelId,
                                              HttpServletRequest httpRequest) {
        return HotelResponses.ManagedHotel.from(hotelService.resume(
                CurrentActor.requireUserId(), hotelId, ClientIp.of(httpRequest)), photoService);
    }

    // --- room types ---------------------------------------------------------

    @PostMapping("/hotels/{hotelId}/room-types")
    @ResponseStatus(HttpStatus.CREATED)
    public HotelResponses.ManagedRoomType createRoomType(
            @PathVariable UUID hotelId,
            @Valid @RequestBody HotelRequests.CreateRoomType request,
            HttpServletRequest httpRequest) {
        return HotelResponses.ManagedRoomType.from(roomTypeService.create(
                CurrentActor.requireUserId(), hotelId, request, ClientIp.of(httpRequest)),
                photoService);
    }

    @GetMapping("/hotels/{hotelId}/room-types")
    public List<HotelResponses.ManagedRoomType> listRoomTypes(@PathVariable UUID hotelId) {
        return roomTypeService.listForHotel(CurrentActor.requireUserId(), hotelId).stream()
                .map(roomType -> HotelResponses.ManagedRoomType.from(roomType, photoService))
                .toList();
    }

    /**
     * Changing {@code totalRooms} realigns future nights that were still at the old
     * default, and is refused if any night already has more rooms sold.
     */
    @PatchMapping("/room-types/{roomTypeId}")
    public HotelResponses.ManagedRoomType updateRoomType(
            @PathVariable UUID roomTypeId,
            @Valid @RequestBody HotelRequests.UpdateRoomType request,
            HttpServletRequest httpRequest) {
        return HotelResponses.ManagedRoomType.from(roomTypeService.update(
                CurrentActor.requireUserId(), roomTypeId, request, ClientIp.of(httpRequest)),
                photoService);
    }

    /** Only a room type that has never been sold. Otherwise set it INACTIVE. */
    @DeleteMapping("/room-types/{roomTypeId}")
    public ResponseEntity<Void> deleteRoomType(@PathVariable UUID roomTypeId,
                                               HttpServletRequest httpRequest) {
        roomTypeService.delete(CurrentActor.requireUserId(), roomTypeId, ClientIp.of(httpRequest));
        return ResponseEntity.noContent().build();
    }

    // --- inventory ----------------------------------------------------------

    /** The date-by-room-type matrix, dense over the requested range. */
    @GetMapping("/hotels/{hotelId}/inventory")
    public List<InventoryService.RoomTypeInventory> inventory(
            @PathVariable UUID hotelId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return inventoryService.matrix(CurrentActor.requireUserId(), hotelId, from, to);
    }

    /** Bulk edit across a range, optionally limited to certain weekdays. */
    @PutMapping("/room-types/{roomTypeId}/inventory")
    public Map<String, Object> updateInventory(
            @PathVariable UUID roomTypeId,
            @Valid @RequestBody HotelRequests.UpdateInventory request,
            HttpServletRequest httpRequest) {
        int nights = inventoryService.updateRange(
                CurrentActor.requireUserId(), roomTypeId, request, ClientIp.of(httpRequest));
        return Map.of("nightsUpdated", nights);
    }

    @DeleteMapping("/room-types/{roomTypeId}/inventory")
    public Map<String, Object> clearInventory(
            @PathVariable UUID roomTypeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            HttpServletRequest httpRequest) {
        int cleared = inventoryService.clearRange(
                CurrentActor.requireUserId(), roomTypeId, from, to, ClientIp.of(httpRequest));
        return Map.of("nightsCleared", cleared);
    }

    // --- photos -------------------------------------------------------------

    @PostMapping("/hotels/{hotelId}/photos/upload-url")
    public mn.innex.stay.common.supply.PhotoUploadSupport.PresignedUpload hotelPhotoUploadUrl(
            @PathVariable UUID hotelId,
            @Valid @RequestBody HotelRequests.PhotoUploadUrl request) {
        return photoService.presignHotelPhoto(CurrentActor.requireUserId(), hotelId, request);
    }

    @PostMapping("/hotels/{hotelId}/photos")
    @ResponseStatus(HttpStatus.CREATED)
    public HotelResponses.Photo confirmHotelPhoto(
            @PathVariable UUID hotelId,
            @Valid @RequestBody HotelRequests.ConfirmPhoto request,
            HttpServletRequest httpRequest) {
        var photo = photoService.confirmHotelPhoto(
                CurrentActor.requireUserId(), hotelId, request, ClientIp.of(httpRequest));
        return new HotelResponses.Photo(photo.getId(), photoService.publicUrl(photo.getStorageKey()),
                photo.getAltText(), photo.getSortOrder(), photo.isCover());
    }

    @PatchMapping("/hotels/{hotelId}/photos/order")
    public List<HotelResponses.Photo> reorderHotelPhotos(
            @PathVariable UUID hotelId,
            @Valid @RequestBody HotelRequests.ReorderPhotos request,
            HttpServletRequest httpRequest) {
        return photoService.reorderHotelPhotos(CurrentActor.requireUserId(), hotelId, request,
                        ClientIp.of(httpRequest)).stream()
                .map(photo -> new HotelResponses.Photo(photo.getId(),
                        photoService.publicUrl(photo.getStorageKey()), photo.getAltText(),
                        photo.getSortOrder(), photo.isCover()))
                .toList();
    }

    @DeleteMapping("/hotels/{hotelId}/photos/{photoId}")
    public ResponseEntity<Void> deleteHotelPhoto(@PathVariable UUID hotelId,
                                                 @PathVariable UUID photoId,
                                                 HttpServletRequest httpRequest) {
        photoService.deleteHotelPhoto(CurrentActor.requireUserId(), hotelId, photoId,
                ClientIp.of(httpRequest));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/room-types/{roomTypeId}/photos/upload-url")
    public mn.innex.stay.common.supply.PhotoUploadSupport.PresignedUpload roomTypePhotoUploadUrl(
            @PathVariable UUID roomTypeId,
            @Valid @RequestBody HotelRequests.PhotoUploadUrl request) {
        return photoService.presignRoomTypePhoto(CurrentActor.requireUserId(), roomTypeId, request);
    }

    @PostMapping("/room-types/{roomTypeId}/photos")
    @ResponseStatus(HttpStatus.CREATED)
    public HotelResponses.Photo confirmRoomTypePhoto(
            @PathVariable UUID roomTypeId,
            @Valid @RequestBody HotelRequests.ConfirmPhoto request,
            HttpServletRequest httpRequest) {
        var photo = photoService.confirmRoomTypePhoto(
                CurrentActor.requireUserId(), roomTypeId, request, ClientIp.of(httpRequest));
        return new HotelResponses.Photo(photo.getId(), photoService.publicUrl(photo.getStorageKey()),
                photo.getAltText(), photo.getSortOrder(), photo.isCover());
    }

    @DeleteMapping("/room-types/{roomTypeId}/photos/{photoId}")
    public ResponseEntity<Void> deleteRoomTypePhoto(@PathVariable UUID roomTypeId,
                                                    @PathVariable UUID photoId,
                                                    HttpServletRequest httpRequest) {
        photoService.deleteRoomTypePhoto(CurrentActor.requireUserId(), roomTypeId, photoId,
                ClientIp.of(httpRequest));
        return ResponseEntity.noContent().build();
    }

    // --- staff --------------------------------------------------------------

    @GetMapping("/hotels/{hotelId}/staff")
    public List<HotelResponses.StaffMember> listStaff(@PathVariable UUID hotelId) {
        return staffService.list(CurrentActor.requireUserId(), hotelId).stream()
                .map(HotelResponses.StaffMember::from)
                .toList();
    }

    /** Adds an existing account by phone. They must have signed up and verified first. */
    @PostMapping("/hotels/{hotelId}/staff")
    @ResponseStatus(HttpStatus.CREATED)
    public HotelResponses.StaffMember addStaff(@PathVariable UUID hotelId,
                                               @Valid @RequestBody HotelRequests.AddStaff request,
                                               HttpServletRequest httpRequest) {
        return HotelResponses.StaffMember.from(staffService.add(
                CurrentActor.requireUserId(), hotelId, request.phone(), ClientIp.of(httpRequest)));
    }

    @DeleteMapping("/hotels/{hotelId}/staff/{userId}")
    public ResponseEntity<Void> removeStaff(@PathVariable UUID hotelId, @PathVariable UUID userId,
                                            HttpServletRequest httpRequest) {
        staffService.remove(CurrentActor.requireUserId(), hotelId, userId,
                ClientIp.of(httpRequest));
        return ResponseEntity.noContent().build();
    }
}
