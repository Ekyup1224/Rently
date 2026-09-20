package mn.innex.stay.booking.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import mn.innex.stay.booking.domain.BookingType;
import mn.innex.stay.booking.domain.CommissionRule;
import mn.innex.stay.common.ApiException;
import mn.innex.stay.common.Money;
import mn.innex.stay.common.PlatformTime;
import mn.innex.stay.common.supply.SupplyStatus;
import mn.innex.stay.hotel.domain.Hotel;
import mn.innex.stay.hotel.domain.RoomInventoryDay;
import mn.innex.stay.hotel.domain.RoomType;
import mn.innex.stay.hotel.service.InventoryService;
import mn.innex.stay.user.domain.OrganizationStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Prices a hotel stay and decides whether it can be booked.
 *
 * <p>The hotel counterpart to {@link PricingService}, and the same contract: the
 * quote endpoint and the booking path both run this, so what a guest is shown and
 * what they are charged come from one place.
 *
 * <p>The availability check here is advisory. It gives an ordinary guest a clear
 * message instead of a constraint violation, but the authority is the
 * {@code ck_room_inventory_not_oversold} CHECK on the inventory row — the count it
 * guards is maintained by a database trigger, so it holds even when two guests
 * take the last room at the same instant.
 */
@Service
public class HotelPricingService {

    private final InventoryService inventoryService;
    private final CommissionRuleService commissionRuleService;
    private final RefundCalculator refundCalculator;
    private final BookingProperties bookingProperties;

    public HotelPricingService(InventoryService inventoryService,
                               CommissionRuleService commissionRuleService,
                               RefundCalculator refundCalculator,
                               BookingProperties bookingProperties) {
        this.inventoryService = inventoryService;
        this.commissionRuleService = commissionRuleService;
        this.refundCalculator = refundCalculator;
        this.bookingProperties = bookingProperties;
    }

    /**
     * Validates and prices a hotel stay.
     *
     * @param rooms rooms of this type; guests are spread across them
     * @throws ApiException 400 for a stay that breaks the hotel's rules,
     *                      409 {@code rooms_unavailable} when the nights are full
     */
    @Transactional(readOnly = true)
    public Quote quote(RoomType roomType, LocalDate checkIn, LocalDate checkOut, int guests,
                       int rooms) {
        Hotel hotel = roomType.getHotel();
        assertBookable(hotel, roomType);
        int nights = assertValidStay(roomType, checkIn, checkOut, guests, rooms);

        Map<LocalDate, RoomInventoryDay> inventory =
                inventoryService.forStay(roomType.getId(), checkIn, checkOut);
        assertAvailable(roomType, checkIn, checkOut, nights, rooms, inventory);

        List<Quote.NightlyRate> nightlyRates = nightlyRates(roomType, checkIn, nights, rooms, inventory);
        BigDecimal nightlySubtotal = nightlyRates.stream()
                .map(Quote.NightlyRate::amount)
                .reduce(Money.ZERO, Money::add);

        // Hotels have no cleaning fee: housekeeping is in the room rate.
        BigDecimal accommodation = nightlySubtotal;

        CommissionRule rule = commissionRuleService.resolveForHotel(hotel.getId(), Instant.now());
        BigDecimal guestServiceFee = Money.percentOf(accommodation, rule.getGuestFeePercent());
        BigDecimal tax = Money.percentOf(accommodation, bookingProperties.taxPercent());
        BigDecimal total = Money.add(accommodation, guestServiceFee, tax);

        BigDecimal hostCommission = Money.percentOf(accommodation, rule.getHostFeePercent());
        BigDecimal hostPayout = Money.subtract(accommodation, hostCommission);

        BigDecimal firstNight = nightlyRates.get(0).amount();
        List<Quote.RefundWindow> refundSchedule = refundCalculator.scheduleFor(
                hotel.getCancellationPolicy(), checkIn, accommodation, guestServiceFee, firstNight);

        return new Quote(
                BookingType.HOTEL, roomType.getId(), rooms, checkIn, checkOut, nights, guests,
                hotel.getCurrency(), nightlyRates, nightlySubtotal, Money.ZERO, guestServiceFee,
                tax, total, hostCommission, hostPayout, rule.getId(),
                hotel.getCancellationPolicy(), refundSchedule,
                // Hotels are always instant-book: a front desk does not vet guests
                // the way an individual host vets who sleeps in their home.
                true);
    }

    /**
     * How many rooms are still sellable across a whole stay, without pricing it.
     *
     * @return the smallest remaining count across the nights, or zero if any night
     *         is closed — a stay needs every night, so the tightest night decides
     */
    @Transactional(readOnly = true)
    public int roomsLeftForStay(RoomType roomType, LocalDate checkIn, LocalDate checkOut) {
        Map<LocalDate, RoomInventoryDay> inventory =
                inventoryService.forStay(roomType.getId(), checkIn, checkOut);

        int fewest = Integer.MAX_VALUE;
        for (LocalDate day = checkIn; day.isBefore(checkOut); day = day.plusDays(1)) {
            RoomInventoryDay row = inventory.get(day);
            if (row == null) {
                fewest = Math.min(fewest, roomType.getTotalRooms());
            } else if (row.isStopSell()) {
                return 0;
            } else {
                fewest = Math.min(fewest, row.remainingCount());
            }
        }
        return fewest == Integer.MAX_VALUE ? 0 : fewest;
    }

    private void assertBookable(Hotel hotel, RoomType roomType) {
        boolean sellable = hotel.getStatus() == SupplyStatus.APPROVED
                && hotel.getOrganization().getStatus() == OrganizationStatus.ACTIVE
                && roomType.isSellable();
        if (!sellable) {
            throw ApiException.conflict("room_type_not_bookable",
                    "This room is not currently available to book");
        }
    }

    /** @return the number of nights */
    private int assertValidStay(RoomType roomType, LocalDate checkIn, LocalDate checkOut,
                                int guests, int rooms) {
        if (checkIn == null || checkOut == null) {
            throw ApiException.badRequest("dates_required", "Check-in and check-out are required");
        }
        if (!checkOut.isAfter(checkIn)) {
            throw ApiException.badRequest("invalid_date_range", "Check-out must be after check-in");
        }

        LocalDate today = PlatformTime.today();
        if (checkIn.isBefore(today)) {
            throw ApiException.badRequest("check_in_in_past", "Check-in cannot be in the past");
        }
        if (ChronoUnit.DAYS.between(today, checkIn) > bookingProperties.maxNightsAhead()) {
            throw ApiException.badRequest("check_in_too_far_ahead",
                    "Check-in cannot be more than " + bookingProperties.maxNightsAhead()
                            + " days from today");
        }

        if (rooms < 1) {
            throw ApiException.badRequest("rooms_required", "At least one room is required");
        }
        if (rooms > roomType.getTotalRooms()) {
            throw ApiException.badRequest("too_many_rooms",
                    "This hotel has only " + roomType.getTotalRooms() + " of this room type");
        }

        int nights = (int) ChronoUnit.DAYS.between(checkIn, checkOut);
        if (nights < roomType.getMinStayNights()) {
            throw ApiException.badRequest("min_stay_not_met",
                    "This room requires at least " + roomType.getMinStayNights() + " night(s)");
        }
        if (roomType.getMaxStayNights() != null && nights > roomType.getMaxStayNights()) {
            throw ApiException.badRequest("max_stay_exceeded",
                    "This room allows at most " + roomType.getMaxStayNights() + " night(s)");
        }
        if (guests < 1) {
            throw ApiException.badRequest("guests_required", "At least one guest is required");
        }
        if (guests > roomType.capacityFor(rooms)) {
            throw ApiException.badRequest("too_many_guests",
                    rooms + " room(s) of this type sleep at most " + roomType.capacityFor(rooms)
                            + " guest(s). Book another room for the rest.");
        }
        return nights;
    }

    private void assertAvailable(RoomType roomType, LocalDate checkIn, LocalDate checkOut,
                                 int nights, int rooms,
                                 Map<LocalDate, RoomInventoryDay> inventory) {
        for (LocalDate day = checkIn; day.isBefore(checkOut); day = day.plusDays(1)) {
            RoomInventoryDay row = inventory.get(day);
            // No row means the night is open at the room type's full capacity.
            int remaining = row == null ? roomType.getTotalRooms() : row.remainingCount();

            if (row != null && row.isStopSell()) {
                throw ApiException.conflict("rooms_unavailable",
                        "This room is closed for " + day);
            }
            if (remaining < rooms) {
                throw ApiException.conflict("rooms_unavailable", remaining == 0
                        ? "This room is fully booked on " + day
                        : "Only " + remaining + " of this room left on " + day);
            }
        }

        // A per-night minimum on the arrival date overrides the room type default,
        // which is how a hotel enforces a two-night minimum over a holiday.
        RoomInventoryDay arrival = inventory.get(checkIn);
        if (arrival != null && arrival.getMinStayNights() != null
                && nights < arrival.getMinStayNights()) {
            throw ApiException.badRequest("min_stay_not_met",
                    "Stays starting " + checkIn + " require at least "
                            + arrival.getMinStayNights() + " night(s)");
        }
    }

    /** Each amount covers every room booked, so the nightly lines sum to the subtotal. */
    private List<Quote.NightlyRate> nightlyRates(RoomType roomType, LocalDate checkIn, int nights,
                                                 int rooms,
                                                 Map<LocalDate, RoomInventoryDay> inventory) {
        List<Quote.NightlyRate> rates = new ArrayList<>(nights);
        BigDecimal basePrice = Money.of(roomType.getBasePrice());

        for (int night = 0; night < nights; night++) {
            LocalDate day = checkIn.plusDays(night);
            RoomInventoryDay row = inventory.get(day);
            BigDecimal override = row == null ? null : row.getRateOverride();
            BigDecimal perRoom = override != null ? Money.of(override) : basePrice;
            rates.add(new Quote.NightlyRate(day, Money.multiply(perRoom, rooms), override != null));
        }
        return rates;
    }
}
