package mn.innex.stay.booking.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import mn.innex.stay.booking.domain.BookingStatus;
import mn.innex.stay.booking.repo.BookingRepository;
import mn.innex.stay.common.ApiException;
import mn.innex.stay.common.Money;
import mn.innex.stay.hotel.repo.RoomInventoryDayRepository;
import mn.innex.stay.hotel.repo.RoomTypeRepository;
import mn.innex.stay.hotel.service.HotelAccessService;
import mn.innex.stay.hotel.web.dto.HotelResponses;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Occupancy, revenue and ADR for a hotel.
 *
 * <p>Lives in the booking module because revenue comes from reservations, and the
 * hotel module has no dependency on booking. Occupancy itself comes from the
 * inventory counters the database already maintains, so it needs no counting of
 * bookings at all.
 *
 * <p>The three figures answer different questions and are reported separately
 * rather than blended: how full the hotel was, what the rooms earned, and what the
 * average room-night sold for.
 */
@Service
public class HotelPerformanceService {

    /** Stays that count as sold revenue: confirmed onwards, cancellations excluded. */
    private static final List<BookingStatus> REVENUE_STATUSES = List.of(
            BookingStatus.CONFIRMED, BookingStatus.CHECKED_IN, BookingStatus.CHECKED_OUT,
            BookingStatus.COMPLETED);

    private static final int MAX_RANGE_DAYS = 400;

    private final RoomInventoryDayRepository inventoryRepository;
    private final RoomTypeRepository roomTypeRepository;
    private final BookingRepository bookingRepository;
    private final HotelAccessService access;

    public HotelPerformanceService(RoomInventoryDayRepository inventoryRepository,
                                   RoomTypeRepository roomTypeRepository,
                                   BookingRepository bookingRepository,
                                   HotelAccessService access) {
        this.inventoryRepository = inventoryRepository;
        this.roomTypeRepository = roomTypeRepository;
        this.bookingRepository = bookingRepository;
        this.access = access;
    }

    @Transactional(readOnly = true)
    public HotelResponses.OccupancyReport report(UUID actorId, UUID hotelId, LocalDate from,
                                                 LocalDate to) {
        var hotel = access.requireManagedOrStaffed(actorId, hotelId);
        if (from == null || to == null) {
            throw ApiException.badRequest("dates_required", "Both from and to are required");
        }
        if (to.isBefore(from)) {
            throw ApiException.badRequest("invalid_date_range", "to must not be before from");
        }
        if (ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS) {
            throw ApiException.badRequest("range_too_wide",
                    "A report range cannot exceed " + MAX_RANGE_DAYS + " days");
        }

        LocalDate toExclusive = to.plusDays(1);

        // Capacity is every room on sale for every night in the range, adjusted by
        // whatever the stored rows say. It cannot come from those rows alone: a
        // night with no row is still sellable at total_rooms, so summing
        // available_count would count only the nights somebody edited or booked
        // and report a hotel as far fuller than it was.
        long nights = ChronoUnit.DAYS.between(from, toExclusive);
        int roomsOnSale = roomTypeRepository.sumRoomsOnSale(hotelId);

        // Sold comes straight from the maintained counters: no booking arithmetic.
        Object[] counters = inventoryRepository
                .sumCapacityDeltaAndSold(hotelId, from, toExclusive).get(0);
        long capacityDelta = ((Number) counters[0]).longValue();
        int sold = ((Number) counters[1]).intValue();

        long available = Math.max(0, nights * roomsOnSale + capacityDelta);

        Object[] revenueRow = bookingRepository
                .sumHotelRoomRevenue(hotelId, REVENUE_STATUSES, from, toExclusive).get(0);
        BigDecimal roomRevenue = Money.of((BigDecimal) revenueRow[0]);
        int reservations = ((Number) revenueRow[1]).intValue();

        BigDecimal occupancy = available == 0
                ? Money.ZERO
                : BigDecimal.valueOf(sold)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(available), 2, RoundingMode.HALF_UP);

        // ADR is revenue per room-night sold, which is why it is not simply the
        // average of the rates on offer.
        BigDecimal averageDailyRate = sold == 0
                ? Money.ZERO
                : roomRevenue.divide(BigDecimal.valueOf(sold), 2, RoundingMode.HALF_UP);

        return new HotelResponses.OccupancyReport(from, to, Math.toIntExact(available), sold,
                occupancy,
                roomRevenue, averageDailyRate, hotel.getCurrency(), reservations);
    }
}
