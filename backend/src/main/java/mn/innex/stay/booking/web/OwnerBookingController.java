package mn.innex.stay.booking.web;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import mn.innex.stay.booking.service.BookingService;
import mn.innex.stay.booking.service.EarningsService;
import mn.innex.stay.booking.web.dto.BookingResponse;
import mn.innex.stay.booking.web.dto.CancelBookingRequest;
import mn.innex.stay.booking.web.dto.EarningsSummaryResponse;
import mn.innex.stay.booking.web.dto.HostDecisionRequest;
import mn.innex.stay.common.web.ClientIp;
import mn.innex.stay.common.web.PageResponse;
import mn.innex.stay.listing.storage.ObjectStorage;
import mn.innex.stay.security.CurrentActor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The host's reservation inbox and earnings.
 *
 * <p>A host sees their payout and the commission withheld; the guest's service fee
 * and total are omitted, because the host's economics stop at what they receive.
 */
@RestController
@RequestMapping("/api/v1/owner")
@PreAuthorize("hasRole('HOUSE_OWNER')")
public class OwnerBookingController {

    private final BookingService bookingService;
    private final EarningsService earningsService;
    private final ObjectStorage storage;

    public OwnerBookingController(BookingService bookingService, EarningsService earningsService,
                                  ObjectStorage storage) {
        this.bookingService = bookingService;
        this.earningsService = earningsService;
        this.storage = storage;
    }

    /** @param scope all, upcoming, past, cancelled or pending */
    @GetMapping("/bookings")
    public PageResponse<BookingResponse> list(
            @RequestParam(required = false, defaultValue = "all") String scope,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return PageResponse.of(
                bookingService.listForHost(CurrentActor.requireUserId(), scope, pageable),
                booking -> BookingResponse.forHost(booking, storage));
    }

    @GetMapping("/bookings/{bookingId}")
    public BookingResponse get(@PathVariable UUID bookingId) {
        return BookingResponse.forHost(
                bookingService.requireForHost(CurrentActor.requireUserId(), bookingId), storage);
    }

    /** Accepts a request. The guest then has the payment window to pay. */
    @PostMapping("/bookings/{bookingId}/approve")
    public BookingResponse approve(@PathVariable UUID bookingId,
                                   @Valid @RequestBody(required = false) HostDecisionRequest request,
                                   HttpServletRequest httpRequest) {
        return BookingResponse.forHost(bookingService.approve(
                CurrentActor.requireUserId(), bookingId,
                request == null ? null : request.note(), ClientIp.of(httpRequest)), storage);
    }

    @PostMapping("/bookings/{bookingId}/decline")
    public BookingResponse decline(@PathVariable UUID bookingId,
                                   @Valid @RequestBody(required = false) HostDecisionRequest request,
                                   HttpServletRequest httpRequest) {
        return BookingResponse.forHost(bookingService.decline(
                CurrentActor.requireUserId(), bookingId,
                request == null ? null : request.note(), ClientIp.of(httpRequest)), storage);
    }

    /**
     * Marks a guest arrived.
     *
     * <p>A house has no front desk, so this is the host saying the guest turned
     * up. It is not bookkeeping: no payout is released until a stay has actually
     * started, so a host who never confirms an arrival is never paid — which is
     * exactly what should happen to a listing nobody could arrive at.
     */
    @PostMapping("/bookings/{bookingId}/check-in")
    public BookingResponse checkIn(@PathVariable UUID bookingId,
                                   HttpServletRequest httpRequest) {
        bookingService.requireForHost(CurrentActor.requireUserId(), bookingId);
        return BookingResponse.forHost(bookingService.checkIn(
                CurrentActor.requireUserId(), bookingId, ClientIp.of(httpRequest)), storage);
    }

    /** Marks the stay finished, which is what opens the review window. */
    @PostMapping("/bookings/{bookingId}/check-out")
    public BookingResponse checkOut(@PathVariable UUID bookingId,
                                    HttpServletRequest httpRequest) {
        bookingService.requireForHost(CurrentActor.requireUserId(), bookingId);
        return BookingResponse.forHost(bookingService.checkOut(
                CurrentActor.requireUserId(), bookingId, ClientIp.of(httpRequest)), storage);
    }

    /**
     * Host-initiated cancellation. The guest is refunded in full regardless of the
     * listing's policy — they did not choose this.
     */
    @PostMapping("/bookings/{bookingId}/cancel")
    public BookingResponse cancel(@PathVariable UUID bookingId,
                                  @Valid @RequestBody(required = false) CancelBookingRequest request,
                                  HttpServletRequest httpRequest) {
        return BookingResponse.forHost(bookingService.cancelByHost(
                CurrentActor.requireUserId(), bookingId,
                request == null ? null : request.reason(), ClientIp.of(httpRequest)), storage);
    }

    /** Earnings for a period, attributed to when each stay ended. */
    @GetMapping("/earnings/summary")
    public EarningsSummaryResponse earnings(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return earningsService.summarize(CurrentActor.requireUserId(), from, to);
    }
}
