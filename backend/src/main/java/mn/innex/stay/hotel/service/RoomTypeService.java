package mn.innex.stay.hotel.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import mn.innex.stay.common.ApiException;
import mn.innex.stay.common.audit.AuditAction;
import mn.innex.stay.common.audit.AuditService;
import mn.innex.stay.common.supply.Amenity;
import mn.innex.stay.hotel.domain.Hotel;
import mn.innex.stay.hotel.domain.RoomType;
import mn.innex.stay.hotel.domain.RoomTypeStatus;
import mn.innex.stay.hotel.repo.HotelRepository;
import mn.innex.stay.hotel.repo.RoomTypeRepository;
import mn.innex.stay.hotel.web.dto.HotelRequests;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Room type management.
 *
 * <p>Two operations need care because they can collide with rooms already sold:
 * reducing {@code totalRooms} and deleting a room type. Neither is allowed to
 * cancel a stay implicitly, so both refuse rather than doing something surprising.
 */
@Service
public class RoomTypeService {

    private static final Logger log = LoggerFactory.getLogger(RoomTypeService.class);

    /** The CHECK that stops availability being cut below what is sold. */
    private static final String OVERSOLD_CONSTRAINT = "ck_room_inventory_not_oversold";

    private final RoomTypeRepository roomTypeRepository;
    private final HotelRepository hotelRepository;
    private final HotelAccessService access;
    private final InventoryService inventoryService;
    private final AuditService auditService;

    public RoomTypeService(RoomTypeRepository roomTypeRepository, HotelRepository hotelRepository,
                           HotelAccessService access, InventoryService inventoryService,
                           AuditService auditService) {
        this.roomTypeRepository = roomTypeRepository;
        this.hotelRepository = hotelRepository;
        this.access = access;
        this.inventoryService = inventoryService;
        this.auditService = auditService;
    }

    @Transactional
    public RoomType create(UUID actorId, UUID hotelId, HotelRequests.CreateRoomType request,
                           String ip) {
        Hotel hotel = access.requireManagerForChange(actorId, hotelId);

        RoomType roomType = new RoomType(hotel, request.name().trim(), request.capacity(),
                request.totalRooms(), request.basePrice());
        roomType.setSortOrder((int) roomTypeRepository.countByHotelId(hotelId));
        // Persisted directly rather than by cascading from the hotel, so the
        // generated id is assigned to this instance and reaches the response.
        roomTypeRepository.saveAndFlush(roomType);
        hotel.addRoomType(roomType);

        auditService.record(actorId, AuditAction.ROOM_TYPE_CREATED, "RoomType", roomType.getId(),
                Map.of("hotelId", hotelId.toString(), "name", roomType.getName(),
                        "totalRooms", roomType.getTotalRooms()), ip);
        return roomType;
    }

    @Transactional(readOnly = true)
    public List<RoomType> listForHotel(UUID actorId, UUID hotelId) {
        access.requireManagedOrStaffed(actorId, hotelId);
        return roomTypeRepository.findByHotelIdOrderBySortOrderAscNameAsc(hotelId);
    }

    @Transactional(readOnly = true)
    public RoomType requireManaged(UUID actorId, UUID roomTypeId) {
        RoomType roomType = roomTypeRepository.findByIdWithDetails(roomTypeId)
                .orElseThrow(() -> ApiException.notFound("room_type_not_found", "Room type not found"));
        // Delegating the check keeps one definition of who may change a hotel.
        access.requireManagerForChange(actorId, roomType.getHotel().getId());
        return roomType;
    }

    @Transactional
    public RoomType update(UUID actorId, UUID roomTypeId, HotelRequests.UpdateRoomType request,
                           String ip) {
        RoomType roomType = requireManaged(actorId, roomTypeId);
        Map<String, Object> changes = new LinkedHashMap<>();

        if (request.name() != null) {
            roomType.setName(request.name().trim());
            changes.put("name", roomType.getName());
        }
        if (request.description() != null) {
            roomType.setDescription(blankToNull(request.description()));
        }
        if (request.capacity() != null) {
            roomType.setCapacity(request.capacity());
            changes.put("capacity", request.capacity());
        }
        if (request.bedConfig() != null) {
            roomType.setBedConfig(blankToNull(request.bedConfig()));
        }
        if (request.sizeSqm() != null) {
            roomType.setSizeSqm(request.sizeSqm());
        }
        if (request.basePrice() != null) {
            roomType.setBasePrice(request.basePrice());
            changes.put("basePrice", roomType.getBasePrice().toPlainString());
        }
        if (request.amenities() != null) {
            roomType.setAmenities(parseAmenities(request.amenities()));
        }
        if (request.minStayNights() != null) {
            roomType.setMinStayNights(request.minStayNights());
        }
        if (request.maxStayNights() != null) {
            roomType.setMaxStayNights(request.maxStayNights());
        }
        if (request.status() != null) {
            roomType.setStatus(request.status());
            changes.put("status", request.status().name());
        }
        if (request.sortOrder() != null) {
            roomType.setSortOrder(request.sortOrder());
        }

        if (roomType.getMaxStayNights() != null
                && roomType.getMaxStayNights() < roomType.getMinStayNights()) {
            throw ApiException.badRequest("invalid_stay_range",
                    "Maximum stay cannot be shorter than minimum stay");
        }

        int previousTotal = roomType.getTotalRooms();
        if (request.totalRooms() != null && request.totalRooms() != previousTotal) {
            roomType.setTotalRooms(request.totalRooms());
            changes.put("totalRooms", request.totalRooms());
        }

        try {
            roomTypeRepository.save(roomType);
            roomTypeRepository.flush();

            // Existing inventory rows keep their own availableCount, so raising or
            // lowering the physical room count has to be pushed onto the nights
            // that are still open. The CHECK refuses any night where that would cut
            // availability below what is already sold.
            if (request.totalRooms() != null && request.totalRooms() != previousTotal) {
                int adjusted = inventoryService.realignFutureAvailability(
                        roomType, previousTotal, request.totalRooms());
                changes.put("inventoryDaysRealigned", adjusted);
            }
        } catch (DataIntegrityViolationException ex) {
            if (mentionsOversold(ex)) {
                throw ApiException.conflict("rooms_already_sold",
                        "Some nights already have more rooms booked than that. "
                                + "Close individual nights instead, or wait for those stays to end.");
            }
            throw ex;
        }

        auditService.record(actorId, AuditAction.ROOM_TYPE_UPDATED, "RoomType", roomTypeId, changes, ip);
        return roomType;
    }

    /**
     * Deletes a room type that was never sold. Anything with inventory history is
     * deactivated instead, because bookings and payouts reference it.
     */
    @Transactional
    public void delete(UUID actorId, UUID roomTypeId, String ip) {
        RoomType roomType = requireManaged(actorId, roomTypeId);

        if (inventoryService.hasSoldNights(roomTypeId)) {
            throw ApiException.conflict("room_type_has_bookings",
                    "This room type has bookings. Set it to INACTIVE instead of deleting it.");
        }

        Hotel hotel = roomType.getHotel();
        hotel.getRoomTypes().remove(roomType);
        roomTypeRepository.delete(roomType);

        auditService.record(actorId, AuditAction.ROOM_TYPE_DELETED, "RoomType", roomTypeId,
                Map.of("hotelId", hotel.getId().toString(), "name", roomType.getName()), ip);
        log.debug("Deleted unsold room type {} from hotel {}", roomTypeId, hotel.getId());
    }

    /** The public view: a hotel's sellable room types. */
    @Transactional(readOnly = true)
    public List<RoomType> listSellable(UUID hotelId) {
        return roomTypeRepository.findByHotelIdOrderBySortOrderAscNameAsc(hotelId).stream()
                .filter(RoomType::isSellable)
                .toList();
    }

    @Transactional(readOnly = true)
    public RoomType requireSellable(UUID roomTypeId) {
        RoomType roomType = roomTypeRepository.findByIdWithDetails(roomTypeId)
                .orElseThrow(() -> ApiException.notFound("room_type_not_found", "Room type not found"));
        if (!roomType.isSellable()) {
            throw ApiException.conflict("room_type_not_bookable",
                    "This room is not currently available to book");
        }
        return roomType;
    }

    private Set<Amenity> parseAmenities(List<String> names) {
        try {
            return Amenity.parseAll(names);
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("unknown_amenity", ex.getMessage());
        }
    }

    private boolean mentionsOversold(DataIntegrityViolationException ex) {
        String message = ex.getMostSpecificCause().getMessage();
        return message != null && message.contains(OVERSOLD_CONSTRAINT);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
