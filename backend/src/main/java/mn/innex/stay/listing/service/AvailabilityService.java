package mn.innex.stay.listing.service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import mn.innex.stay.common.ApiException;
import mn.innex.stay.common.Money;
import mn.innex.stay.common.audit.AuditAction;
import mn.innex.stay.common.audit.AuditService;
import mn.innex.stay.listing.domain.AvailabilityException;
import mn.innex.stay.listing.domain.Property;
import mn.innex.stay.listing.repo.AvailabilityExceptionRepository;
import mn.innex.stay.listing.web.dto.CalendarDayResponse;
import mn.innex.stay.listing.web.dto.CalendarUpdateRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The listing calendar: which nights are open, what they cost, and how long a
 * stay must be.
 *
 * <p>Only deviations from the listing's defaults are stored, so the calendar is
 * assembled by walking the requested range and overlaying exception rows and
 * booked ranges onto the base price. That keeps a year of "nothing special"
 * costing zero rows.
 *
 * <p>Booked nights come through {@link OccupancyPort} and are never written here:
 * bookings are the single source of truth for what is taken, so the two cannot
 * drift out of agreement.
 */
@Service
public class AvailabilityService {

    private static final Logger log = LoggerFactory.getLogger(AvailabilityService.class);

    /** A calendar request wider than this is a mistake or a scrape, not a UI. */
    private static final int MAX_RANGE_DAYS = 400;

    private final AvailabilityExceptionRepository availabilityRepository;
    private final PropertyService propertyService;
    private final OccupancyPort occupancyPort;
    private final AuditService auditService;

    public AvailabilityService(AvailabilityExceptionRepository availabilityRepository,
                               PropertyService propertyService, OccupancyPort occupancyPort,
                               AuditService auditService) {
        this.availabilityRepository = availabilityRepository;
        this.propertyService = propertyService;
        this.occupancyPort = occupancyPort;
        this.auditService = auditService;
    }

    /**
     * Builds a calendar.
     *
     * @param revealBookings owner calendars distinguish BOOKED from BLOCKED; public
     *                       ones report both as BLOCKED, because a guest has no
     *                       business knowing a host's occupancy rate
     */
    @Transactional(readOnly = true)
    public List<CalendarDayResponse> calendar(Property property, LocalDate from, LocalDate to,
                                              boolean revealBookings) {
        assertSaneRange(from, to);
        LocalDate toExclusive = to.plusDays(1);

        Map<LocalDate, AvailabilityException> exceptions = new HashMap<>();
        availabilityRepository.findInRange(property.getId(), from, toExclusive)
                .forEach(exception -> exceptions.put(exception.getDay(), exception));

        List<OccupancyPort.OccupiedRange> occupied =
                occupancyPort.occupiedRanges(property.getId(), from, toExclusive);

        BigDecimal basePrice = Money.of(property.getBasePrice());
        List<CalendarDayResponse> days = new ArrayList<>();

        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            AvailabilityException exception = exceptions.get(day);
            final LocalDate current = day;
            boolean booked = occupied.stream().anyMatch(range -> range.covers(current));

            CalendarDayResponse.DayStatus status;
            if (booked) {
                // Booked wins: unblocking a night cannot free one that is sold.
                status = revealBookings
                        ? CalendarDayResponse.DayStatus.BOOKED
                        : CalendarDayResponse.DayStatus.BLOCKED;
            } else if (exception != null && exception.isBlocked()) {
                status = CalendarDayResponse.DayStatus.BLOCKED;
            } else {
                status = CalendarDayResponse.DayStatus.AVAILABLE;
            }

            BigDecimal override = exception == null ? null : exception.getPriceOverride();
            days.add(new CalendarDayResponse(day, status,
                    override != null ? override : basePrice, override != null,
                    exception == null ? null : exception.getMinStayNights()));
        }
        return days;
    }

    /**
     * Applies a bulk edit across a date range, optionally only on certain weekdays.
     *
     * <p>Rows that end up saying nothing are deleted rather than stored, so
     * clearing an override leaves the calendar as clean as it was before.
     *
     * @return how many days were affected
     */
    @Transactional
    public int updateRange(UUID ownerId, UUID propertyId, CalendarUpdateRequest request, String ip) {
        Property property = propertyService.requireOwned(ownerId, propertyId);
        assertSaneRange(request.from(), request.to());

        if (request.blocked() == null && request.price() == null && !request.clearPriceRequested()
                && request.minStayNights() == null && !request.clearMinStayRequested()) {
            throw ApiException.badRequest("nothing_to_change",
                    "Specify at least one of: blocked, price, minStayNights");
        }
        if (request.price() != null && request.clearPriceRequested()) {
            throw ApiException.badRequest("conflicting_price_change",
                    "Set a price or clear it, not both");
        }

        Set<DayOfWeek> weekdays = request.weekdays() == null || request.weekdays().isEmpty()
                ? EnumSet.allOf(DayOfWeek.class)
                : EnumSet.copyOf(request.weekdays());

        Map<LocalDate, AvailabilityException> existing = new HashMap<>();
        availabilityRepository.findInRange(propertyId, request.from(), request.to().plusDays(1))
                .forEach(exception -> existing.put(exception.getDay(), exception));

        List<AvailabilityException> toSave = new ArrayList<>();
        List<AvailabilityException> toDelete = new ArrayList<>();
        int affected = 0;

        for (LocalDate day = request.from(); !day.isAfter(request.to()); day = day.plusDays(1)) {
            if (!weekdays.contains(day.getDayOfWeek())) {
                continue;
            }
            affected++;

            AvailabilityException exception = existing.get(day);
            if (exception == null) {
                exception = new AvailabilityException(property, day);
            }

            if (request.blocked() != null) {
                exception.setBlocked(request.blocked());
            }
            if (request.clearPriceRequested()) {
                exception.setPriceOverride(null);
            } else if (request.price() != null) {
                exception.setPriceOverride(request.price());
            }
            if (request.clearMinStayRequested()) {
                exception.setMinStayNights(null);
            } else if (request.minStayNights() != null) {
                exception.setMinStayNights(request.minStayNights());
            }

            if (exception.isEmpty()) {
                if (exception.getId() != null) {
                    toDelete.add(exception);
                }
            } else {
                toSave.add(exception);
            }
        }

        availabilityRepository.deleteAll(toDelete);
        availabilityRepository.saveAll(toSave);

        auditService.record(ownerId, AuditAction.PROPERTY_CALENDAR_UPDATED, "Property", propertyId,
                Map.of("from", request.from().toString(), "to", request.to().toString(),
                        "days", affected,
                        "blocked", String.valueOf(request.blocked()),
                        "price", request.price() == null ? "" : request.price().toPlainString()), ip);
        log.debug("Calendar for listing {}: {} day(s) updated, {} row(s) cleared",
                propertyId, affected, toDelete.size());
        return affected;
    }

    /** Returns a range to the listing's defaults by deleting its exception rows. */
    @Transactional
    public int clearRange(UUID ownerId, UUID propertyId, LocalDate from, LocalDate to, String ip) {
        propertyService.requireOwned(ownerId, propertyId);
        assertSaneRange(from, to);

        int removed = availabilityRepository.deleteInRange(propertyId, from, to.plusDays(1));
        auditService.record(ownerId, AuditAction.PROPERTY_CALENDAR_UPDATED, "Property", propertyId,
                Map.of("from", from.toString(), "to", to.toString(), "cleared", removed), ip);
        return removed;
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
                    "A calendar range cannot exceed " + MAX_RANGE_DAYS + " days");
        }
    }
}
