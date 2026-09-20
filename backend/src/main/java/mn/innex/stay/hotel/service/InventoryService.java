package mn.innex.stay.hotel.service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import mn.innex.stay.common.ApiException;
import mn.innex.stay.common.Money;
import mn.innex.stay.common.PlatformTime;
import mn.innex.stay.common.audit.AuditAction;
import mn.innex.stay.common.audit.AuditService;
import mn.innex.stay.hotel.domain.Hotel;
import mn.innex.stay.hotel.domain.RoomInventoryDay;
import mn.innex.stay.hotel.domain.RoomType;
import mn.innex.stay.hotel.repo.RoomInventoryDayRepository;
import mn.innex.stay.hotel.repo.RoomTypeRepository;
import mn.innex.stay.hotel.web.dto.HotelRequests;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rates and availability, night by night.
 *
 * <p>This is the hotel equivalent of the house calendar, with one crucial
 * difference: what is stored is a <em>count</em>, and the rooms already sold on
 * each night are maintained by a database trigger. Nothing here writes
 * {@code bookedCount} — it is read to show what is left, and the
 * {@code ck_room_inventory_not_oversold} CHECK is what refuses any edit that would
 * cut availability below it.
 *
 * <p>A night with no row is sellable at the room type's {@code totalRooms} and base
 * price, so a new hotel can sell immediately and rows exist only where the hotel
 * has said something.
 */
@Service
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    /** A request wider than this is a mistake or a scrape, not a screen. */
    private static final int MAX_RANGE_DAYS = 400;
    private static final String OVERSOLD_CONSTRAINT = "ck_room_inventory_not_oversold";

    private final RoomInventoryDayRepository inventoryRepository;
    private final RoomTypeRepository roomTypeRepository;
    private final HotelAccessService access;
    private final AuditService auditService;

    public InventoryService(RoomInventoryDayRepository inventoryRepository,
                            RoomTypeRepository roomTypeRepository, HotelAccessService access,
                            AuditService auditService) {
        this.inventoryRepository = inventoryRepository;
        this.roomTypeRepository = roomTypeRepository;
        this.access = access;
        this.auditService = auditService;
    }

    /**
     * The date-by-room-type matrix a hotel actually works in.
     *
     * <p>Returned as one row per room type with a dense list of days, so the grid
     * can render without filling gaps itself. Days with no stored row are
     * synthesized from the room type's defaults.
     */
    @Transactional(readOnly = true)
    public List<RoomTypeInventory> matrix(UUID actorId, UUID hotelId, LocalDate from, LocalDate to) {
        Hotel hotel = access.requireManagedOrStaffed(actorId, hotelId);
        assertSaneRange(from, to);

        List<RoomType> roomTypes = roomTypeRepository.findByHotelIdOrderBySortOrderAscNameAsc(hotelId);
        if (roomTypes.isEmpty()) {
            return List.of();
        }

        LocalDate toExclusive = to.plusDays(1);
        Map<UUID, Map<LocalDate, RoomInventoryDay>> stored = new HashMap<>();
        inventoryRepository.findInRangeForRoomTypes(
                        roomTypes.stream().map(RoomType::getId).toList(), from, toExclusive)
                .forEach(row -> stored
                        .computeIfAbsent(row.getRoomType().getId(), key -> new HashMap<>())
                        .put(row.getDay(), row));

        List<RoomTypeInventory> matrix = new ArrayList<>();
        for (RoomType roomType : roomTypes) {
            Map<LocalDate, RoomInventoryDay> byDay =
                    stored.getOrDefault(roomType.getId(), Map.of());
            List<InventoryNight> nights = new ArrayList<>();

            for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
                RoomInventoryDay row = byDay.get(day);
                if (row == null) {
                    nights.add(new InventoryNight(day, roomType.getTotalRooms(), 0,
                            roomType.getTotalRooms(), Money.of(roomType.getBasePrice()),
                            false, false, null));
                } else {
                    nights.add(new InventoryNight(day, row.getAvailableCount(), row.getBookedCount(),
                            row.remainingCount(),
                            row.getRateOverride() != null
                                    ? row.getRateOverride() : Money.of(roomType.getBasePrice()),
                            row.getRateOverride() != null, row.isStopSell(), row.getMinStayNights()));
                }
            }
            matrix.add(new RoomTypeInventory(roomType.getId(), roomType.getName(),
                    roomType.getTotalRooms(), Money.of(roomType.getBasePrice()),
                    roomType.getStatus().name(), nights));
        }

        log.debug("Inventory matrix for hotel {}: {} room type(s) x {} night(s)",
                hotel.getId(), matrix.size(), ChronoUnit.DAYS.between(from, toExclusive));
        return matrix;
    }

    /**
     * Bulk edit across a range, optionally limited to certain weekdays.
     *
     * @return how many nights were affected
     * @throws ApiException 409 when the edit would cut availability below rooms sold
     */
    @Transactional
    public int updateRange(UUID actorId, UUID roomTypeId, HotelRequests.UpdateInventory request,
                           String ip) {
        RoomType roomType = roomTypeRepository.findByIdWithDetails(roomTypeId)
                .orElseThrow(() -> ApiException.notFound("room_type_not_found", "Room type not found"));
        access.requireManagerForChange(actorId, roomType.getHotel().getId());
        assertSaneRange(request.from(), request.to());

        boolean nothingToDo = request.availableCount() == null && request.rate() == null
                && !request.clearRateRequested() && request.stopSell() == null
                && request.minStayNights() == null && !request.clearMinStayRequested();
        if (nothingToDo) {
            throw ApiException.badRequest("nothing_to_change",
                    "Specify at least one of: availableCount, rate, stopSell, minStayNights");
        }
        if (request.rate() != null && request.clearRateRequested()) {
            throw ApiException.badRequest("conflicting_rate_change",
                    "Set a rate or clear it, not both");
        }
        if (request.availableCount() != null && request.availableCount() > roomType.getTotalRooms()) {
            throw ApiException.badRequest("exceeds_total_rooms",
                    "This room type has only " + roomType.getTotalRooms() + " room(s)");
        }

        Set<DayOfWeek> weekdays = request.weekdays() == null || request.weekdays().isEmpty()
                ? EnumSet.allOf(DayOfWeek.class)
                : EnumSet.copyOf(request.weekdays());

        LocalDate toExclusive = request.to().plusDays(1);
        Map<LocalDate, RoomInventoryDay> existing = new HashMap<>();
        inventoryRepository.findInRange(roomTypeId, request.from(), toExclusive)
                .forEach(row -> existing.put(row.getDay(), row));

        List<RoomInventoryDay> toSave = new ArrayList<>();
        int affected = 0;

        for (LocalDate day = request.from(); !day.isAfter(request.to()); day = day.plusDays(1)) {
            if (!weekdays.contains(day.getDayOfWeek())) {
                continue;
            }
            affected++;

            RoomInventoryDay row = existing.get(day);
            if (row == null) {
                // Materialize at the room type's default before applying the edit.
                row = new RoomInventoryDay(roomType, day, roomType.getTotalRooms());
            }

            if (request.availableCount() != null) {
                row.setAvailableCount(request.availableCount());
            }
            if (request.clearRateRequested()) {
                row.setRateOverride(null);
            } else if (request.rate() != null) {
                row.setRateOverride(request.rate());
            }
            if (request.stopSell() != null) {
                row.setStopSell(request.stopSell());
            }
            if (request.clearMinStayRequested()) {
                row.setMinStayNights(null);
            } else if (request.minStayNights() != null) {
                row.setMinStayNights(request.minStayNights());
            }
            toSave.add(row);
        }

        try {
            inventoryRepository.saveAll(toSave);
            inventoryRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            if (mentionsOversold(ex)) {
                throw ApiException.conflict("rooms_already_sold",
                        "At least one of those nights already has more rooms booked than that. "
                                + "Reduce availability on the nights that are still open.");
            }
            throw ex;
        }

        auditService.record(actorId, AuditAction.ROOM_INVENTORY_UPDATED, "RoomType", roomTypeId,
                summarize(request, affected), ip);
        return affected;
    }

    /** Returns a range to the room type's defaults, keeping nights that are sold. */
    @Transactional
    public int clearRange(UUID actorId, UUID roomTypeId, LocalDate from, LocalDate to, String ip) {
        RoomType roomType = roomTypeRepository.findByIdWithDetails(roomTypeId)
                .orElseThrow(() -> ApiException.notFound("room_type_not_found", "Room type not found"));
        access.requireManagerForChange(actorId, roomType.getHotel().getId());
        assertSaneRange(from, to);

        int removed = inventoryRepository.deleteResettableInRange(roomTypeId, from, to.plusDays(1));
        auditService.record(actorId, AuditAction.ROOM_INVENTORY_UPDATED, "RoomType", roomTypeId,
                Map.of("from", from.toString(), "to", to.toString(), "cleared", removed), ip);
        return removed;
    }

    /**
     * Pushes a change in physical room count onto nights that already have rows.
     *
     * <p>Only nights from today forward are touched: history should keep saying
     * what the hotel actually had at the time. Nights whose availability was
     * deliberately set to something other than the old total are left alone,
     * because that was a decision rather than a default.
     *
     * @return the number of nights realigned
     */
    @Transactional
    public int realignFutureAvailability(RoomType roomType, int previousTotal, int newTotal) {
        LocalDate today = PlatformTime.today();
        List<RoomInventoryDay> rows = inventoryRepository.findInRange(
                roomType.getId(), today, today.plusDays(MAX_RANGE_DAYS));

        List<RoomInventoryDay> changed = new ArrayList<>();
        for (RoomInventoryDay row : rows) {
            if (row.getAvailableCount() == previousTotal) {
                row.setAvailableCount(newTotal);
                changed.add(row);
            }
        }
        inventoryRepository.saveAll(changed);
        inventoryRepository.flush();
        return changed.size();
    }

    /** Whether any night of this room type has rooms sold. */
    @Transactional(readOnly = true)
    public boolean hasSoldNights(UUID roomTypeId) {
        return inventoryRepository.findInRange(roomTypeId, LocalDate.of(2000, 1, 1),
                        LocalDate.of(2100, 1, 1)).stream()
                .anyMatch(row -> row.getBookedCount() > 0);
    }

    /** Stored rows for a stay, keyed by night, for the booking path. */
    @Transactional(readOnly = true)
    public Map<LocalDate, RoomInventoryDay> forStay(UUID roomTypeId, LocalDate checkIn,
                                                    LocalDate checkOut) {
        Map<LocalDate, RoomInventoryDay> byDay = new HashMap<>();
        inventoryRepository.findInRange(roomTypeId, checkIn, checkOut)
                .forEach(row -> byDay.put(row.getDay(), row));
        return byDay;
    }

    private Map<String, Object> summarize(HotelRequests.UpdateInventory request, int affected) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("from", request.from().toString());
        summary.put("to", request.to().toString());
        summary.put("nights", affected);
        if (request.availableCount() != null) {
            summary.put("availableCount", request.availableCount());
        }
        if (request.rate() != null) {
            summary.put("rate", request.rate().toPlainString());
        }
        if (request.stopSell() != null) {
            summary.put("stopSell", request.stopSell());
        }
        if (request.minStayNights() != null) {
            summary.put("minStayNights", request.minStayNights());
        }
        return summary;
    }

    private void assertSaneRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw ApiException.badRequest("dates_required", "Both from and to are required");
        }
        if (to.isBefore(from)) {
            throw ApiException.badRequest("invalid_date_range", "to must not be before from");
        }
        if (ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS) {
            throw ApiException.badRequest("range_too_wide",
                    "An inventory range cannot exceed " + MAX_RANGE_DAYS + " days");
        }
    }

    private boolean mentionsOversold(DataIntegrityViolationException ex) {
        String message = ex.getMostSpecificCause().getMessage();
        return message != null && message.contains(OVERSOLD_CONSTRAINT);
    }

    /**
     * One room type's row in the matrix.
     *
     * @param nights dense: every day of the requested range, defaults filled in
     */
    public record RoomTypeInventory(
            UUID roomTypeId,
            String name,
            int totalRooms,
            BigDecimal basePrice,
            String status,
            List<InventoryNight> nights) {
    }

    /**
     * @param booked    rooms sold, maintained by the database
     * @param remaining what is left to sell, ignoring {@code stopSell}
     * @param overridden whether {@code rate} came from a stored override
     */
    public record InventoryNight(
            LocalDate date,
            int available,
            int booked,
            int remaining,
            BigDecimal rate,
            boolean overridden,
            boolean stopSell,
            Integer minStay) {
    }
}
