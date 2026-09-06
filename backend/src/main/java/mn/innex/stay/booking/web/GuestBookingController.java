package mn.innex.stay.booking.web;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import mn.innex.stay.booking.service.BookingService;
import mn.innex.stay.booking.web.dto.BookingCreateRequest;
import mn.innex.stay.booking.web.dto.BookingResponse;
import mn.innex.stay.booking.web.dto.CancelBookingRequest;
import mn.innex.stay.common.web.ClientIp;
import mn.innex.stay.common.web.PageResponse;
import mn.innex.stay.listing.storage.ObjectStorage;
import mn.innex.stay.security.CurrentActor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * A guest's own bookings.
 *
 * <p>Requires only authentication, not a specific role: every account holds
 * CLIENT, and a house owner booking someone else's place is an ordinary guest
 * while doing it.
 */
@RestController
@RequestMapping("/api/v1/bookings")
public class GuestBookingController {

    private final BookingService bookingService;
    private final ObjectStorage storage;

    public GuestBookingController(BookingService bookingService, ObjectStorage storage) {
        this.bookingService = bookingService;
        this.storage = storage;
    }

    /**
     * Requests or instantly books a stay.
     *
     * <p>The response's {@code status} says which happened: {@code PENDING_PAYMENT}
     * for an instant-book listing, {@code PENDING_HOST_APPROVAL} when the host
     * vets requests. Either way the dates are held, with {@code expiresAt} as the
     * deadline.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BookingResponse create(@Valid @RequestBody BookingCreateRequest request,
                                  HttpServletRequest httpRequest) {
        return BookingResponse.forGuest(bookingService.create(
                CurrentActor.requireUserId(), request.propertyId(), request.checkIn(),
                request.checkOut(), request.guests(), request.message(),
                ClientIp.of(httpRequest)), storage);
    }

    /** @param scope all, upcoming, past, cancelled or pending */
    @GetMapping
    public PageResponse<BookingResponse> list(
            @RequestParam(required = false, defaultValue = "all") String scope,
            @PageableDefault(size = 20, sort = "checkIn", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return PageResponse.of(
                bookingService.listForGuest(CurrentActor.requireUserId(), scope, pageable),
                booking -> BookingResponse.forGuest(booking, storage));
    }

    @GetMapping("/{bookingId}")
    public BookingResponse get(@PathVariable UUID bookingId) {
        return BookingResponse.forGuest(
                bookingService.requireForGuest(CurrentActor.requireUserId(), bookingId), storage);
    }

    /**
     * Cancels a booking. The refund follows the policy snapshotted when it was
     * made, and is issued automatically if the stay was paid for.
     */
    @PostMapping("/{bookingId}/cancel")
    public BookingResponse cancel(@PathVariable UUID bookingId,
                                  @Valid @RequestBody(required = false) CancelBookingRequest request,
                                  HttpServletRequest httpRequest) {
        String reason = request == null ? null : request.reason();
        return BookingResponse.forGuest(bookingService.cancelByGuest(
                CurrentActor.requireUserId(), bookingId, reason, ClientIp.of(httpRequest)),
                storage);
    }
}
