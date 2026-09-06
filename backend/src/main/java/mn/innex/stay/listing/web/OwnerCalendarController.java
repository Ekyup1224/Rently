package mn.innex.stay.listing.web;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import mn.innex.stay.common.web.ClientIp;
import mn.innex.stay.listing.service.AvailabilityService;
import mn.innex.stay.listing.service.PropertyService;
import mn.innex.stay.listing.web.dto.CalendarDayResponse;
import mn.innex.stay.listing.web.dto.CalendarUpdateRequest;
import mn.innex.stay.security.CurrentActor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The owner's availability and pricing calendar.
 *
 * <p>Unlike the public calendar, this distinguishes BOOKED from BLOCKED: an owner
 * needs to see which closed nights are earning and which are simply shut.
 */
@RestController
@RequestMapping("/api/v1/owner/properties/{propertyId}/calendar")
@PreAuthorize("hasRole('HOUSE_OWNER')")
public class OwnerCalendarController {

    private final AvailabilityService availabilityService;
    private final PropertyService propertyService;

    public OwnerCalendarController(AvailabilityService availabilityService,
                                   PropertyService propertyService) {
        this.availabilityService = availabilityService;
        this.propertyService = propertyService;
    }

    /** @param to inclusive: the calendar is a set of days, not a stay */
    @GetMapping
    public List<CalendarDayResponse> calendar(
            @PathVariable UUID propertyId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return availabilityService.calendar(
                propertyService.requireOwned(CurrentActor.requireUserId(), propertyId),
                from, to, true);
    }

    /**
     * Bulk edit across a range, optionally limited to certain weekdays — how
     * seasonal and weekend pricing is actually set.
     */
    @PutMapping
    public Map<String, Object> update(@PathVariable UUID propertyId,
                                      @Valid @RequestBody CalendarUpdateRequest request,
                                      HttpServletRequest httpRequest) {
        int affected = availabilityService.updateRange(
                CurrentActor.requireUserId(), propertyId, request, ClientIp.of(httpRequest));
        return Map.of("daysUpdated", affected);
    }

    /** Returns a range to the listing's defaults. */
    @DeleteMapping
    public Map<String, Object> clear(
            @PathVariable UUID propertyId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            HttpServletRequest httpRequest) {
        int cleared = availabilityService.clearRange(
                CurrentActor.requireUserId(), propertyId, from, to, ClientIp.of(httpRequest));
        return Map.of("daysCleared", cleared);
    }
}
