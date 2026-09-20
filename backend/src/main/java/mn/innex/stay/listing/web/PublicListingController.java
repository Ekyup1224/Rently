package mn.innex.stay.listing.web;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import mn.innex.stay.booking.service.BookingService;
import mn.innex.stay.booking.web.dto.QuoteResponse;
import mn.innex.stay.common.web.PageResponse;
import mn.innex.stay.common.supply.Amenity;
import mn.innex.stay.listing.domain.PropertyType;
import mn.innex.stay.listing.service.AvailabilityService;
import mn.innex.stay.listing.service.PropertyService;
import mn.innex.stay.listing.storage.ObjectStorage;
import mn.innex.stay.listing.web.dto.CalendarDayResponse;
import mn.innex.stay.listing.web.dto.ListingDetailResponse;
import mn.innex.stay.listing.web.dto.QuoteRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The guest-facing catalogue: search, listing pages, availability and pricing.
 *
 * <p>All of it is unauthenticated. Guests browse and price a stay before signing
 * in, and only committing to a booking requires an account — which is why the
 * quote endpoint lives here rather than behind auth.
 *
 * <p>Only approved listings from active owners are reachable, so suspending a host
 * removes their inventory from the catalogue immediately.
 */
@RestController
@RequestMapping("/api/v1/listings")
public class PublicListingController {

    private final PropertyService propertyService;
    private final AvailabilityService availabilityService;
    private final BookingService bookingService;
    private final ObjectStorage storage;

    public PublicListingController(PropertyService propertyService,
                                   AvailabilityService availabilityService,
                                   BookingService bookingService,
                                   ObjectStorage storage) {
        this.propertyService = propertyService;
        this.availabilityService = availabilityService;
        this.bookingService = bookingService;
        this.storage = storage;
    }

    @GetMapping("/{propertyId}")
    public ListingDetailResponse detail(@PathVariable UUID propertyId) {
        return ListingDetailResponse.from(
                propertyService.requirePubliclyVisible(propertyId), storage);
    }

    /**
     * The listing's public calendar.
     *
     * <p>Booked nights are reported as BLOCKED rather than BOOKED: a guest needs to
     * know a night is unavailable, not how well the host is doing.
     */
    @GetMapping("/{propertyId}/availability")
    public List<CalendarDayResponse> availability(
            @PathVariable UUID propertyId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return availabilityService.calendar(
                propertyService.requirePubliclyVisible(propertyId), from, to, false);
    }

    /**
     * Prices a stay without creating anything.
     *
     * <p>This is the number the guest is shown, produced by the same code that
     * prices the booking itself, so the two cannot disagree. It also validates the
     * stay, so an impossible date range fails here rather than at checkout.
     */
    @PostMapping("/{propertyId}/quote")
    public QuoteResponse quote(@PathVariable UUID propertyId,
                               @Valid @RequestBody QuoteRequest request) {
        return QuoteResponse.from(bookingService.quote(
                propertyId, request.checkIn(), request.checkOut(), request.guests()));
    }
}
