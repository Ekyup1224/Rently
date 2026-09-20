package mn.innex.stay.booking.web;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import mn.innex.stay.booking.domain.Booking;
import mn.innex.stay.booking.domain.BookingStatus;
import mn.innex.stay.booking.repo.BookingRepository;
import mn.innex.stay.booking.service.BookingService;
import mn.innex.stay.booking.service.HotelPerformanceService;
import mn.innex.stay.booking.web.dto.BookingResponse;
import mn.innex.stay.booking.web.dto.CancelBookingRequest;
import mn.innex.stay.common.ApiException;
import mn.innex.stay.common.web.ClientIp;
import mn.innex.stay.common.web.PageResponse;
import mn.innex.stay.hotel.service.HotelAccessService;
import mn.innex.stay.hotel.web.dto.HotelResponses;
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
 * The hotel front desk: today's arrivals, check-in, check-out, and the
 * performance report.
 *
 * <p>Reachable by both hotel roles. Every reservation is resolved through the
 * hotel it belongs to, so staff assigned to one property cannot read another's
 * bookings even inside the same business.
 */
@RestController
@RequestMapping("/api/v1/hotel")
@PreAuthorize("hasAnyRole('HOTEL_MANAGER', 'HOTEL_STAFF')")
public class HotelReservationController {

    private static final List<BookingStatus> ARRIVING = List.of(BookingStatus.CONFIRMED);
    private static final List<BookingStatus> IN_HOUSE = List.of(BookingStatus.CHECKED_IN);
    private static final List<BookingStatus> UPCOMING = List.of(
            BookingStatus.PENDING_PAYMENT, BookingStatus.CONFIRMED, BookingStatus.CHECKED_IN);
    private static final List<BookingStatus> PAST = List.of(
            BookingStatus.CHECKED_OUT, BookingStatus.COMPLETED);
    private static final List<BookingStatus> CANCELLED = List.of(
            BookingStatus.CANCELLED_BY_GUEST, BookingStatus.CANCELLED_BY_HOST,
            BookingStatus.EXPIRED, BookingStatus.DECLINED);

    private final BookingRepository bookingRepository;
    private final BookingService bookingService;
    private final HotelAccessService access;
    private final HotelPerformanceService performanceService;
    private final ObjectStorage storage;

    public HotelReservationController(BookingRepository bookingRepository,
                                      BookingService bookingService, HotelAccessService access,
                                      HotelPerformanceService performanceService,
                                      ObjectStorage storage) {
        this.bookingRepository = bookingRepository;
        this.bookingService = bookingService;
        this.access = access;
        this.performanceService = performanceService;
        this.storage = storage;
    }

    /**
     * Reservations at a hotel.
     *
     * @param scope all, arriving, in-house, upcoming, past or cancelled
     */
    @GetMapping("/bookings")
    public PageResponse<BookingResponse> list(
            @RequestParam UUID hotelId,
            @RequestParam(required = false, defaultValue = "upcoming") String scope,
            @PageableDefault(size = 25, sort = "checkIn", direction = Sort.Direction.ASC)
            Pageable pageable) {
        access.requireManagedOrStaffed(CurrentActor.requireUserId(), hotelId);
        return PageResponse.of(
                bookingService.listForHotel(hotelId, statusesFor(scope), pageable),
                booking -> BookingResponse.forHost(booking, storage));
    }

    @GetMapping("/bookings/{bookingId}")
    public BookingResponse get(@PathVariable UUID bookingId) {
        return BookingResponse.forHost(requireAtAccessibleHotel(bookingId), storage);
    }

    /** Marks a guest arrived. Only a confirmed reservation can be checked in. */
    @PostMapping("/bookings/{bookingId}/check-in")
    public BookingResponse checkIn(@PathVariable UUID bookingId, HttpServletRequest httpRequest) {
        requireAtAccessibleHotel(bookingId);
        return BookingResponse.forHost(bookingService.checkIn(
                CurrentActor.requireUserId(), bookingId, ClientIp.of(httpRequest)), storage);
    }

    @PostMapping("/bookings/{bookingId}/check-out")
    public BookingResponse checkOut(@PathVariable UUID bookingId, HttpServletRequest httpRequest) {
        requireAtAccessibleHotel(bookingId);
        return BookingResponse.forHost(bookingService.checkOut(
                CurrentActor.requireUserId(), bookingId, ClientIp.of(httpRequest)), storage);
    }

    /**
     * Cancels a reservation from the hotel's side. The guest is refunded in full,
     * as with a host cancellation on a house: they did not choose this.
     */
    @PostMapping("/bookings/{bookingId}/cancel")
    public BookingResponse cancel(@PathVariable UUID bookingId,
                                  @Valid @RequestBody(required = false) CancelBookingRequest request,
                                  HttpServletRequest httpRequest) {
        Booking booking = requireAtAccessibleHotel(bookingId);
        // Only a manager may cancel; the desk can check people in, not turn them away.
        access.requireManagerForChange(CurrentActor.requireUserId(),
                booking.getRoomType().getHotel().getId());
        return BookingResponse.forHost(bookingService.cancelByHost(
                booking.getHost().getId(), bookingId,
                request == null ? null : request.reason(), ClientIp.of(httpRequest)), storage);
    }

    /** Occupancy, room revenue and ADR over a period. */
    @GetMapping("/reports/occupancy")
    public HotelResponses.OccupancyReport occupancy(
            @RequestParam UUID hotelId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return performanceService.report(CurrentActor.requireUserId(), hotelId, from, to);
    }

    /**
     * Resolves a reservation through the hotel it belongs to, so access is decided
     * by the hotel rather than by holding a booking id.
     */
    private Booking requireAtAccessibleHotel(UUID bookingId) {
        Booking booking = bookingService.requireAnyBooking(bookingId);
        if (!booking.isHotelStay() || booking.getRoomType() == null) {
            throw ApiException.notFound("booking_not_found", "Booking not found");
        }
        access.requireManagedOrStaffed(CurrentActor.requireUserId(),
                booking.getRoomType().getHotel().getId());
        return booking;
    }

    /** @return the statuses for a desk filter, or null for everything */
    private List<BookingStatus> statusesFor(String scope) {
        if (scope == null || scope.isBlank() || "all".equalsIgnoreCase(scope)) {
            return null;
        }
        return switch (scope.toLowerCase()) {
            case "arriving" -> ARRIVING;
            case "in-house", "inhouse" -> IN_HOUSE;
            case "upcoming" -> UPCOMING;
            case "past" -> PAST;
            case "cancelled" -> CANCELLED;
            default -> throw ApiException.badRequest("invalid_scope",
                    "scope must be one of: all, arriving, in-house, upcoming, past, cancelled");
        };
    }
}
