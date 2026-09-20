package mn.innex.stay.booking.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import mn.innex.stay.booking.domain.BookingStatus;
import mn.innex.stay.booking.domain.CommissionRule;
import mn.innex.stay.booking.repo.BookingRepository;
import mn.innex.stay.common.ApiException;
import mn.innex.stay.common.Money;
import mn.innex.stay.common.PlatformTime;
import mn.innex.stay.listing.domain.AvailabilityException;
import mn.innex.stay.listing.domain.Property;
import mn.innex.stay.common.supply.SupplyStatus;
import mn.innex.stay.listing.repo.AvailabilityExceptionRepository;
import mn.innex.stay.user.domain.UserStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Prices a stay and decides whether it can be booked at all.
 *
 * <p>The single source of truth for what a booking costs. Both {@code POST
 * /listings/{id}/quote} and {@code POST /bookings} run this, so the total a guest
 * is shown and the total they are charged come from the same code path — the
 * client's numbers are never trusted or even read.
 *
 * <p>Validation and pricing are deliberately one operation: a price for dates
 * that cannot be booked is worse than an error, because it would be displayed.
 */
@Service
public class PricingService {

    private final AvailabilityExceptionRepository availabilityRepository;
    private final BookingRepository bookingRepository;
    private final CommissionRuleService commissionRuleService;
    private final RefundCalculator refundCalculator;
    private final BookingProperties bookingProperties;

    public PricingService(AvailabilityExceptionRepository availabilityRepository,
                          BookingRepository bookingRepository,
                          CommissionRuleService commissionRuleService,
                          RefundCalculator refundCalculator,
                          BookingProperties bookingProperties) {
        this.availabilityRepository = availabilityRepository;
        this.bookingRepository = bookingRepository;
        this.commissionRuleService = commissionRuleService;
        this.refundCalculator = refundCalculator;
        this.bookingProperties = bookingProperties;
    }

    /**
     * Validates and prices a stay.
     *
     * @throws ApiException 400 for a stay that breaks the listing's rules, 409
     *                      {@code dates_unavailable} when the dates are taken
     */
    @Transactional(readOnly = true)
    public Quote quote(Property property, LocalDate checkIn, LocalDate checkOut, int guests) {
        assertBookable(property);
        int nights = assertValidStay(property, checkIn, checkOut, guests);

        Map<LocalDate, AvailabilityException> exceptions = exceptionsByDay(property, checkIn, checkOut);
        assertAvailable(property, checkIn, checkOut, nights, exceptions);

        List<Quote.NightlyRate> nightlyRates = nightlyRates(property, checkIn, nights, exceptions);
        BigDecimal nightlySubtotal = nightlyRates.stream()
                .map(Quote.NightlyRate::amount)
                .reduce(Money.ZERO, Money::add);

        BigDecimal cleaningFee = Money.of(property.getCleaningFee());
        // Fees and commission are both taken on the accommodation total: nights
        // plus cleaning. Taking commission on the platform's own fee would be
        // double-counting.
        BigDecimal accommodation = Money.add(nightlySubtotal, cleaningFee);

        CommissionRule rule = commissionRuleService.resolveForProperty(
                property.getPropertyType(), Instant.now());
        BigDecimal guestServiceFee = Money.percentOf(accommodation, rule.getGuestFeePercent());
        BigDecimal tax = Money.percentOf(accommodation, bookingProperties.taxPercent());
        BigDecimal total = Money.add(accommodation, guestServiceFee, tax);

        BigDecimal hostCommission = Money.percentOf(accommodation, rule.getHostFeePercent());
        // Derived by subtraction, never rounded independently, so commission plus
        // payout always equals the accommodation total to the tögrög.
        BigDecimal hostPayout = Money.subtract(accommodation, hostCommission);

        BigDecimal firstNight = nightlyRates.get(0).amount();
        List<Quote.RefundWindow> refundSchedule = refundCalculator.scheduleFor(
                property.getCancellationPolicy(), checkIn, accommodation, guestServiceFee, firstNight);

        return new Quote(
                mn.innex.stay.booking.domain.BookingType.PROPERTY, property.getId(), 1,
                checkIn, checkOut, nights, guests, property.getCurrency(),
                nightlyRates, nightlySubtotal, cleaningFee, guestServiceFee, tax, total,
                hostCommission, hostPayout, rule.getId(),
                property.getCancellationPolicy(), refundSchedule, property.isInstantBook());
    }

    private void assertBookable(Property property) {
        if (property.getStatus() != SupplyStatus.APPROVED) {
            throw ApiException.conflict("listing_not_bookable",
                    "This listing is not currently accepting bookings");
        }
        if (property.getOwner().getStatus() != UserStatus.ACTIVE) {
            throw ApiException.conflict("listing_not_bookable",
                    "This listing is not currently accepting bookings");
        }
    }

    /** @return the number of nights */
    private int assertValidStay(Property property, LocalDate checkIn, LocalDate checkOut, int guests) {
        if (checkIn == null || checkOut == null) {
            throw ApiException.badRequest("dates_required", "Check-in and check-out are required");
        }
        if (!checkOut.isAfter(checkIn)) {
            throw ApiException.badRequest("invalid_date_range",
                    "Check-out must be after check-in");
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

        int nights = (int) ChronoUnit.DAYS.between(checkIn, checkOut);
        if (nights < property.getMinStayNights()) {
            throw ApiException.badRequest("min_stay_not_met",
                    "This listing requires at least " + property.getMinStayNights() + " night(s)");
        }
        if (property.getMaxStayNights() != null && nights > property.getMaxStayNights()) {
            throw ApiException.badRequest("max_stay_exceeded",
                    "This listing allows at most " + property.getMaxStayNights() + " night(s)");
        }
        if (guests < 1) {
            throw ApiException.badRequest("guests_required", "At least one guest is required");
        }
        if (guests > property.getMaxGuests()) {
            throw ApiException.badRequest("too_many_guests",
                    "This listing sleeps at most " + property.getMaxGuests() + " guest(s)");
        }
        return nights;
    }

    private Map<LocalDate, AvailabilityException> exceptionsByDay(Property property,
                                                                  LocalDate checkIn,
                                                                  LocalDate checkOut) {
        Map<LocalDate, AvailabilityException> byDay = new HashMap<>();
        for (AvailabilityException exception :
                availabilityRepository.findInRange(property.getId(), checkIn, checkOut)) {
            byDay.put(exception.getDay(), exception);
        }
        return byDay;
    }

    private void assertAvailable(Property property, LocalDate checkIn, LocalDate checkOut,
                                 int nights, Map<LocalDate, AvailabilityException> exceptions) {
        for (LocalDate day = checkIn; day.isBefore(checkOut); day = day.plusDays(1)) {
            AvailabilityException exception = exceptions.get(day);
            if (exception != null && exception.isBlocked()) {
                throw ApiException.conflict("dates_unavailable",
                        "The listing is not available on " + day);
            }
        }

        // A per-day minimum on the arrival night, e.g. a three-night minimum over a
        // holiday weekend, overrides the listing default.
        AvailabilityException arrival = exceptions.get(checkIn);
        if (arrival != null && arrival.getMinStayNights() != null
                && nights < arrival.getMinStayNights()) {
            throw ApiException.badRequest("min_stay_not_met",
                    "Stays starting " + checkIn + " require at least "
                            + arrival.getMinStayNights() + " night(s)");
        }

        // Advisory: the exclusion constraint is what actually prevents a race. This
        // exists so the ordinary case gets a readable error.
        if (bookingRepository.hasOverlap(property.getId(), checkIn, checkOut,
                BookingStatus.occupyingStates(), null)) {
            throw ApiException.conflict("dates_unavailable",
                    "Those dates have just been taken");
        }
    }

    private List<Quote.NightlyRate> nightlyRates(Property property, LocalDate checkIn, int nights,
                                                 Map<LocalDate, AvailabilityException> exceptions) {
        List<Quote.NightlyRate> rates = new ArrayList<>(nights);
        BigDecimal basePrice = Money.of(property.getBasePrice());
        for (int night = 0; night < nights; night++) {
            LocalDate day = checkIn.plusDays(night);
            AvailabilityException exception = exceptions.get(day);
            BigDecimal override = exception == null ? null : exception.getPriceOverride();
            rates.add(new Quote.NightlyRate(
                    day, override != null ? Money.of(override) : basePrice, override != null));
        }
        return rates;
    }
}
