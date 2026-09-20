package mn.innex.stay.booking.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import mn.innex.stay.booking.domain.Booking;
import mn.innex.stay.booking.domain.BookingPaymentStatus;
import mn.innex.stay.booking.domain.BookingStatus;
import mn.innex.stay.booking.repo.BookingRepository;
import mn.innex.stay.common.ApiException;
import mn.innex.stay.common.Money;
import mn.innex.stay.common.audit.AuditAction;
import mn.innex.stay.common.audit.AuditService;
import mn.innex.stay.hotel.domain.RoomType;
import mn.innex.stay.hotel.service.RoomTypeService;
import mn.innex.stay.listing.domain.Property;
import mn.innex.stay.listing.repo.PropertyRepository;
import mn.innex.stay.user.domain.User;
import mn.innex.stay.user.repo.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The booking lifecycle: request or instant-book, host response, cancellation,
 * and the transitions the payment module and the scheduler drive.
 *
 * <p>Prices are always recomputed through {@link PricingService} rather than taken
 * from the request, and every ownership check reads the actor from the security
 * context, so no path lets one user act on another's booking.
 */
@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    /** Name of the exclusion constraint in V4; used to recognize a lost date race. */
    private static final String OVERLAP_CONSTRAINT = "bookings_no_property_overlap";
    /** The CHECK in V5 that makes a hotel oversell impossible. */
    private static final String OVERSOLD_CONSTRAINT = "ck_room_inventory_not_oversold";

    private final BookingRepository bookingRepository;
    private final PropertyRepository propertyRepository;
    private final UserRepository userRepository;
    private final PricingService pricingService;
    private final HotelPricingService hotelPricingService;
    private final RoomTypeService roomTypeService;
    private final BookingReferenceGenerator referenceGenerator;
    private final RefundCalculator refundCalculator;
    private final BookingProperties bookingProperties;
    private final AuditService auditService;
    /**
     * Resolved on use rather than injected, because the implementation
     * ({@code PaymentService}) needs this service to confirm bookings — the two
     * genuinely refer to each other at runtime even though the package dependency
     * runs one way only. An {@link ObjectProvider} defers the lookup past
     * construction, which breaks the bean cycle while keeping the refund
     * synchronous with the cancellation that triggered it. Doing this with events
     * instead would make a failed refund a silent loss of money, needing a
     * reconciliation job to catch.
     */
    private final ObjectProvider<RefundPort> refundPort;
    private final ObjectProvider<PayoutPort> payoutPort;
    /**
     * Booking creation runs in an explicit transaction rather than a declarative
     * one, so a lock failure can be retried in a *new* transaction — a retry
     * inside the failed one is impossible, since it is already marked rollback-only.
     */
    private final TransactionTemplate bookingTransaction;

    public BookingService(BookingRepository bookingRepository,
                          PropertyRepository propertyRepository,
                          UserRepository userRepository,
                          PricingService pricingService,
                          HotelPricingService hotelPricingService,
                          RoomTypeService roomTypeService,
                          BookingReferenceGenerator referenceGenerator,
                          RefundCalculator refundCalculator,
                          BookingProperties bookingProperties,
                          AuditService auditService,
                          ObjectProvider<RefundPort> refundPort,
                          ObjectProvider<PayoutPort> payoutPort,
                          PlatformTransactionManager transactionManager) {
        this.bookingRepository = bookingRepository;
        this.propertyRepository = propertyRepository;
        this.userRepository = userRepository;
        this.pricingService = pricingService;
        this.hotelPricingService = hotelPricingService;
        this.roomTypeService = roomTypeService;
        this.referenceGenerator = referenceGenerator;
        this.refundCalculator = refundCalculator;
        this.bookingProperties = bookingProperties;
        this.auditService = auditService;
        this.refundPort = refundPort;
        this.payoutPort = payoutPort;
        this.bookingTransaction = new TransactionTemplate(transactionManager);
    }

    /**
     * Loads the photo collections a response card needs, while the session is open.
     *
     * <p>A booking can reach three collections — a property's photos, a room
     * type's photos and its hotel's photos — and Hibernate fetches at most one per
     * query. {@code @BatchSize} makes these a couple of extra round trips, and
     * doing it here means a controller can map the entity after the transaction
     * closes without tripping a lazy-load.
     */
    private Booking initialize(Booking booking) {
        if (booking.getProperty() != null) {
            booking.getProperty().getPhotos().size();
        }
        if (booking.getRoomType() != null) {
            booking.getRoomType().getPhotos().size();
            booking.getRoomType().getHotel().getPhotos().size();
        }
        // The counterparty's name appears on the card.
        if (booking.getHost() != null) {
            booking.getHost().getFullName();
        }
        if (booking.getGuest() != null) {
            booking.getGuest().getFullName();
        }
        return booking;
    }

    /** Reservations at one hotel, for the front desk. */
    @Transactional(readOnly = true)
    public Page<Booking> listForHotel(UUID hotelId, List<BookingStatus> statuses,
                                      Pageable pageable) {
        Page<Booking> page = bookingRepository.findByHotel(hotelId, statuses, pageable);
        page.forEach(this::initialize);
        return page;
    }

    /** Prices a stay without creating anything. Backs {@code POST /listings/{id}/quote}. */
    @Transactional(readOnly = true)
    public Quote quote(UUID propertyId, LocalDate checkIn, LocalDate checkOut, int guests) {
        return pricingService.quote(requireProperty(propertyId), checkIn, checkOut, guests);
    }

    /** Prices a hotel stay without creating anything. */
    @Transactional(readOnly = true)
    public Quote quoteHotelStay(UUID roomTypeId, LocalDate checkIn, LocalDate checkOut,
                                int guests, int rooms) {
        return hotelPricingService.quote(
                roomTypeService.requireSellable(roomTypeId), checkIn, checkOut, guests, rooms);
    }

    /**
     * Reserves rooms at a hotel.
     *
     * <p>Hotels are instant-book: there is no host to vet the guest, so the
     * reservation goes straight to {@code PENDING_PAYMENT} with the rooms held
     * until the payment window closes.
     *
     * <p>The availability check inside pricing is advisory. What actually prevents
     * an oversell is the CHECK on the inventory row, whose count a database trigger
     * maintains — so two guests taking the last room at the same instant end with
     * one reservation and one clean 409, not two reservations.
     *
     * @throws ApiException 409 {@code rooms_unavailable} when the rooms went while
     *                      this request was in flight
     */
    public Booking createHotelBooking(UUID guestId, UUID roomTypeId, LocalDate checkIn,
                                      LocalDate checkOut, int guests, int rooms,
                                      String guestMessage, String ip) {
        // Same retry as houses: contention on the inventory rows can surface as a
        // deadlock rather than a constraint violation, and a retry in a fresh
        // transaction tells the two apart.
        try {
            return bookingTransaction.execute(status -> doCreateHotelBooking(
                    guestId, roomTypeId, checkIn, checkOut, guests, rooms, guestMessage, ip));
        } catch (PessimisticLockingFailureException ex) {
            log.info("Lock contention booking room type {} for {}..{}; retrying once",
                    roomTypeId, checkIn, checkOut);
            try {
                return bookingTransaction.execute(status -> doCreateHotelBooking(
                        guestId, roomTypeId, checkIn, checkOut, guests, rooms, guestMessage, ip));
            } catch (PessimisticLockingFailureException retryFailed) {
                log.warn("Booking room type {} for {}..{} lost to contention twice",
                        roomTypeId, checkIn, checkOut, retryFailed);
                throw ApiException.conflict("rooms_unavailable",
                        "Those rooms have just been taken. Please try different dates.");
            }
        }
    }

    private Booking doCreateHotelBooking(UUID guestId, UUID roomTypeId, LocalDate checkIn,
                                         LocalDate checkOut, int guests, int rooms,
                                         String guestMessage, String ip) {
        RoomType roomType = roomTypeService.requireSellable(roomTypeId);
        User guest = userRepository.findByIdWithRoles(guestId)
                .orElseThrow(() -> ApiException.unauthorized("unauthenticated",
                        "Authentication is required"));

        Quote quote = hotelPricingService.quote(roomType, checkIn, checkOut, guests, rooms);

        Booking booking = Booking.forHotel(referenceGenerator.next(), roomType, guest,
                checkIn, checkOut, guests, rooms, BookingStatus.PENDING_PAYMENT, guestMessage);
        booking.applyPricing(quote.nightlySubtotal(), quote.cleaningFee(), quote.guestServiceFee(),
                quote.tax(), quote.total(), quote.hostCommission(), quote.hostPayout(),
                quote.commissionRuleId());
        booking.setExpiresAt(Instant.now().plus(bookingProperties.paymentHold()));

        try {
            bookingRepository.saveAndFlush(booking);
        } catch (DataIntegrityViolationException ex) {
            if (isOversoldViolation(ex)) {
                log.info("Lost a room race on room type {} for {}..{}",
                        roomTypeId, checkIn, checkOut);
                throw ApiException.conflict("rooms_unavailable",
                        "Those rooms have just been taken. Please pick different dates.");
            }
            throw ex;
        }

        auditService.record(guestId, AuditAction.BOOKING_CREATED, "Booking", booking.getId(),
                Map.of("reference", booking.getReference(),
                        "roomTypeId", roomTypeId.toString(),
                        "hotelId", roomType.getHotel().getId().toString(),
                        "rooms", rooms,
                        "nights", booking.nightCount(),
                        "total", booking.getTotal().toPlainString()), ip);

        // Reloaded through the full graph before the transaction closes. The host
        // was assigned from the organization's owner, which is a lazy proxy that
        // the response would otherwise try to initialize with no session open.
        return requireBooking(booking.getId());
    }

    /**
     * Front-desk arrival for a hotel stay.
     *
     * @throws ApiException 409 unless the reservation is confirmed
     */
    @Transactional
    public Booking checkIn(UUID actorId, UUID bookingId, String ip) {
        Booking booking = requireBooking(bookingId);
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw ApiException.conflict("booking_not_confirmed",
                    "Only a confirmed reservation can be checked in");
        }
        booking.checkIn();
        bookingRepository.save(booking);
        auditService.record(actorId, AuditAction.BOOKING_CHECKED_IN, "Booking", bookingId,
                Map.of("reference", booking.getReference()), ip);
        return booking;
    }

    @Transactional
    public Booking checkOut(UUID actorId, UUID bookingId, String ip) {
        Booking booking = requireBooking(bookingId);
        if (booking.getStatus() != BookingStatus.CHECKED_IN) {
            throw ApiException.conflict("booking_not_checked_in",
                    "Only a checked-in reservation can be checked out");
        }
        booking.checkOut();
        bookingRepository.save(booking);
        auditService.record(actorId, AuditAction.BOOKING_CHECKED_OUT, "Booking", bookingId,
                Map.of("reference", booking.getReference()), ip);
        return booking;
    }

    /**
     * Creates a booking, pricing it server-side.
     *
     * <p>An instant-book listing goes straight to {@code PENDING_PAYMENT}; anything
     * else waits for the host. Either way the dates are held from this moment, with
     * a deadline the expiry job enforces so inventory is never stranded.
     *
     * @throws ApiException 409 {@code dates_unavailable} if another guest won the race
     */
    public Booking create(UUID guestId, UUID propertyId, LocalDate checkIn, LocalDate checkOut,
                          int guests, String guestMessage, String ip) {
        // Concurrent inserts of overlapping date ranges contend on the exclusion
        // constraint's GiST index, and Postgres resolves some of that contention by
        // aborting a transaction with a deadlock rather than a constraint violation.
        // One retry in a fresh transaction separates the two cases: a spurious
        // deadlock succeeds on the second attempt, while genuinely taken dates come
        // back as a clean constraint violation and a 409.
        try {
            return bookingTransaction.execute(status -> doCreate(
                    guestId, propertyId, checkIn, checkOut, guests, guestMessage, ip));
        } catch (PessimisticLockingFailureException ex) {
            log.info("Lock contention creating a booking on property {} for {}..{}; retrying once",
                    propertyId, checkIn, checkOut);
            try {
                return bookingTransaction.execute(status -> doCreate(
                        guestId, propertyId, checkIn, checkOut, guests, guestMessage, ip));
            } catch (PessimisticLockingFailureException retryFailed) {
                log.warn("Booking on property {} for {}..{} lost to contention twice",
                        propertyId, checkIn, checkOut, retryFailed);
                throw ApiException.conflict("dates_unavailable",
                        "Those dates have just been taken. Please pick different dates.");
            }
        }
    }

    private Booking doCreate(UUID guestId, UUID propertyId, LocalDate checkIn, LocalDate checkOut,
                             int guests, String guestMessage, String ip) {
        Property property = requireProperty(propertyId);
        User guest = userRepository.findByIdWithRoles(guestId)
                .orElseThrow(() -> ApiException.unauthorized("unauthenticated",
                        "Authentication is required"));

        if (property.isOwnedBy(guestId)) {
            throw ApiException.badRequest("cannot_book_own_listing",
                    "You cannot book your own listing");
        }

        Quote quote = pricingService.quote(property, checkIn, checkOut, guests);

        BookingStatus initialStatus = property.isInstantBook()
                ? BookingStatus.PENDING_PAYMENT
                : BookingStatus.PENDING_HOST_APPROVAL;
        Instant deadline = Instant.now().plus(property.isInstantBook()
                ? bookingProperties.paymentHold()
                : bookingProperties.hostResponseWindow());

        Booking booking = new Booking(referenceGenerator.next(), property, guest,
                checkIn, checkOut, guests, initialStatus, guestMessage);
        booking.applyPricing(quote.nightlySubtotal(), quote.cleaningFee(), quote.guestServiceFee(),
                quote.tax(), quote.total(), quote.hostCommission(), quote.hostPayout(),
                quote.commissionRuleId());
        booking.setExpiresAt(deadline);

        try {
            bookingRepository.saveAndFlush(booking);
        } catch (DataIntegrityViolationException ex) {
            // The exclusion constraint rejected it: someone else took these dates
            // between the availability check and the insert. This is the race the
            // constraint exists to catch, and it is expected under load.
            if (isOverlapViolation(ex)) {
                log.info("Lost a date race on property {} for {}..{}", propertyId, checkIn, checkOut);
                throw ApiException.conflict("dates_unavailable",
                        "Those dates have just been taken. Please pick different dates.");
            }
            throw ex;
        }

        auditService.record(guestId, AuditAction.BOOKING_CREATED, "Booking", booking.getId(),
                Map.of("reference", booking.getReference(),
                        "propertyId", propertyId.toString(),
                        "nights", booking.nightCount(),
                        "total", booking.getTotal().toPlainString(),
                        "instantBook", property.isInstantBook()), ip);
        return booking;
    }

    /** Any booking, fully loaded. Callers do their own authorization. */
    @Transactional(readOnly = true)
    public Booking requireAnyBooking(UUID bookingId) {
        return requireBooking(bookingId);
    }

    @Transactional(readOnly = true)
    public Booking requireForGuest(UUID guestId, UUID bookingId) {
        Booking booking = requireBooking(bookingId);
        if (!booking.isGuest(guestId)) {
            // Reported as missing rather than forbidden: whether a booking exists is
            // itself information the caller is not entitled to.
            throw ApiException.notFound("booking_not_found", "Booking not found");
        }
        return booking;
    }

    @Transactional(readOnly = true)
    public Booking requireForHost(UUID hostId, UUID bookingId) {
        Booking booking = requireBooking(bookingId);
        if (!booking.isHost(hostId)) {
            throw ApiException.notFound("booking_not_found", "Booking not found");
        }
        return booking;
    }

    @Transactional(readOnly = true)
    public Page<Booking> listForGuest(UUID guestId, String scope, Pageable pageable) {
        List<BookingStatus> statuses = scopeStatuses(scope);
        Page<Booking> page = statuses == null
                ? bookingRepository.findByGuestId(guestId, pageable)
                : bookingRepository.findByGuestIdAndStatusIn(guestId, statuses, pageable);
        page.forEach(this::initialize);
        return page;
    }

    @Transactional(readOnly = true)
    public Page<Booking> listForHost(UUID hostId, String scope, Pageable pageable) {
        List<BookingStatus> statuses = scopeStatuses(scope);
        Page<Booking> page = statuses == null
                ? bookingRepository.findByHostId(hostId, pageable)
                : bookingRepository.findByHostIdAndStatusIn(hostId, statuses, pageable);
        page.forEach(this::initialize);
        return page;
    }

    /** Host accepts a request; the guest then has the payment window to pay. */
    @Transactional
    public Booking approve(UUID hostId, UUID bookingId, String note, String ip) {
        Booking booking = requireForHost(hostId, bookingId);
        if (booking.getStatus() != BookingStatus.PENDING_HOST_APPROVAL) {
            throw ApiException.conflict("booking_not_pending_approval",
                    "This booking is not waiting for your approval");
        }

        booking.approve(Instant.now().plus(bookingProperties.paymentHold()), note);
        bookingRepository.save(booking);

        auditService.record(hostId, AuditAction.BOOKING_APPROVED, "Booking", bookingId,
                Map.of("reference", booking.getReference()), ip);
        return booking;
    }

    @Transactional
    public Booking decline(UUID hostId, UUID bookingId, String note, String ip) {
        Booking booking = requireForHost(hostId, bookingId);
        if (booking.getStatus() != BookingStatus.PENDING_HOST_APPROVAL) {
            throw ApiException.conflict("booking_not_pending_approval",
                    "This booking is not waiting for your approval");
        }

        booking.decline(note);
        bookingRepository.save(booking);

        auditService.record(hostId, AuditAction.BOOKING_DECLINED, "Booking", bookingId,
                Map.of("reference", booking.getReference(), "note", note == null ? "" : note), ip);
        return booking;
    }

    /**
     * Guest cancellation. The refund follows the policy snapshotted on the booking,
     * so it is judged by the terms in force when they booked.
     */
    @Transactional
    public Booking cancelByGuest(UUID guestId, UUID bookingId, String reason, String ip) {
        Booking booking = requireForGuest(guestId, bookingId);
        assertCancellable(booking);

        BigDecimal refund = computeGuestRefund(booking);
        booking.cancel(BookingStatus.CANCELLED_BY_GUEST, guestId, reason, refund);
        bookingRepository.save(booking);

        issueRefundIfDue(booking, refund, "Guest cancellation");
        cancelPayout(bookingId, "Cancelled by guest");
        auditService.record(guestId, AuditAction.BOOKING_CANCELLED, "Booking", bookingId,
                Map.of("reference", booking.getReference(), "by", "GUEST",
                        "refund", refund.toPlainString(),
                        "policy", booking.getCancellationPolicy().name()), ip);
        return booking;
    }

    /**
     * Host cancellation. The guest is made whole regardless of the policy — they
     * did not choose this, and a host cancelling a paid stay is the platform's
     * problem, not the guest's.
     */
    @Transactional
    public Booking cancelByHost(UUID hostId, UUID bookingId, String reason, String ip) {
        Booking booking = requireForHost(hostId, bookingId);
        assertCancellable(booking);

        BigDecimal refund = booking.getPaymentStatus() == BookingPaymentStatus.PAID
                ? booking.getTotal()
                : Money.ZERO;
        booking.cancel(BookingStatus.CANCELLED_BY_HOST, hostId, reason, refund);
        bookingRepository.save(booking);

        issueRefundIfDue(booking, refund, "Host cancellation");
        cancelPayout(bookingId, "Cancelled by host");
        auditService.record(hostId, AuditAction.BOOKING_CANCELLED, "Booking", bookingId,
                Map.of("reference", booking.getReference(), "by", "HOST",
                        "refund", refund.toPlainString()), ip);
        return booking;
    }

    /**
     * Called by the payment module when a charge settles. Idempotent: a redelivered
     * callback for an already-confirmed booking is a no-op rather than an error.
     */
    @Transactional
    public Booking confirmPaid(UUID bookingId) {
        Booking booking = requireBooking(bookingId);
        if (booking.getStatus() == BookingStatus.CONFIRMED) {
            return booking;
        }
        if (booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
            // Payment landed for a booking that expired or was cancelled. Recording
            // it as paid would confirm a stay whose dates may already be resold, so
            // refuse and let support refund it.
            log.warn("Payment settled for booking {} in state {} — not confirming",
                    booking.getReference(), booking.getStatus());
            throw ApiException.conflict("booking_not_awaiting_payment",
                    "This booking is no longer awaiting payment");
        }

        booking.markPaidAndConfirm();
        bookingRepository.save(booking);
        // The host is now owed money, but does not get it yet: the payout is
        // scheduled behind a hold that outlasts the guest's arrival.
        schedulePayout(booking);
        auditService.record(booking.getGuest().getId(), AuditAction.BOOKING_CONFIRMED,
                "Booking", bookingId, Map.of("reference", booking.getReference()), null);
        return booking;
    }

    @Transactional
    public void markPaymentProcessing(UUID bookingId) {
        Booking booking = requireBooking(bookingId);
        booking.markPaymentProcessing();
        bookingRepository.save(booking);
    }

    @Transactional
    public void markPaymentFailed(UUID bookingId) {
        Booking booking = requireBooking(bookingId);
        booking.markPaymentFailed();
        bookingRepository.save(booking);
    }

    /** Expires one overdue booking. Used by the scheduler. */
    @Transactional
    public void expire(UUID bookingId) {
        Booking booking = requireBooking(bookingId);
        if (!booking.getStatus().hasDeadline()) {
            return;
        }
        BookingStatus previous = booking.getStatus();
        booking.expire();
        bookingRepository.save(booking);
        auditService.record(null, AuditAction.BOOKING_EXPIRED, "Booking", bookingId,
                Map.of("reference", booking.getReference(), "from", previous.name()), null);
    }

    @Transactional
    public void complete(UUID bookingId) {
        Booking booking = requireBooking(bookingId);
        if (booking.getStatus().isTerminal()) {
            return;
        }
        booking.complete();
        bookingRepository.save(booking);
        auditService.record(null, AuditAction.BOOKING_COMPLETED, "Booking", bookingId,
                Map.of("reference", booking.getReference(),
                        "hostPayout", booking.getHostPayout().toPlainString()), null);
    }

    private void assertCancellable(Booking booking) {
        if (!booking.getStatus().isGuestCancellable()) {
            throw ApiException.conflict("booking_not_cancellable",
                    "A " + booking.getStatus().name().toLowerCase().replace('_', ' ')
                            + " booking cannot be cancelled");
        }
    }

    /** Unpaid bookings refund nothing; paid ones follow the snapshotted policy. */
    private BigDecimal computeGuestRefund(Booking booking) {
        if (booking.getPaymentStatus() != BookingPaymentStatus.PAID) {
            return Money.ZERO;
        }
        BigDecimal accommodation = Money.add(booking.getNightlySubtotal(), booking.getCleaningFee());
        BigDecimal firstNight = booking.nightCount() > 0
                ? Money.of(booking.getNightlySubtotal()
                        .divide(BigDecimal.valueOf(booking.nightCount()), 2, java.math.RoundingMode.HALF_UP))
                : Money.ZERO;
        return refundCalculator.refundFor(booking.getCancellationPolicy(), booking.getCheckIn(),
                accommodation, booking.getGuestServiceFee(), firstNight);
    }

    /**
     * Schedules the host's money behind its hold. A failure here must not undo a
     * paid, confirmed booking — the guest has a stay either way — so it is logged
     * loudly rather than thrown; the payout sweep works from booking state and can
     * be reconciled.
     */
    private void schedulePayout(Booking booking) {
        PayoutPort port = payoutPort.getIfAvailable();
        if (port == null) {
            log.error("Booking {} confirmed but no payout port is available",
                    booking.getReference());
            return;
        }
        try {
            port.scheduleForBooking(booking);
        } catch (RuntimeException ex) {
            log.error("Could not schedule payout for booking {}", booking.getReference(), ex);
        }
    }

    /** Voids a payout for a stay that is no longer happening. */
    private void cancelPayout(UUID bookingId, String reason) {
        PayoutPort port = payoutPort.getIfAvailable();
        if (port == null) {
            return;
        }
        try {
            port.cancelForBooking(bookingId, reason);
        } catch (RuntimeException ex) {
            log.error("Could not cancel payout for booking {}", bookingId, ex);
        }
    }

    private void issueRefundIfDue(Booking booking, BigDecimal refund, String reason) {
        if (!Money.isPositive(refund)) {
            return;
        }
        RefundPort port = refundPort.getIfAvailable();
        if (port == null) {
            log.error("Refund of {} owed on booking {} but no refund port is available",
                    refund, booking.getReference());
            return;
        }
        port.refundForBooking(booking.getId(), refund, reason);
    }

    /** @return the statuses for a trips filter, or null for "everything" */
    private List<BookingStatus> scopeStatuses(String scope) {
        if (scope == null || scope.isBlank() || "all".equalsIgnoreCase(scope)) {
            return null;
        }
        return switch (scope.toLowerCase()) {
            case "upcoming" -> List.of(BookingStatus.PENDING_HOST_APPROVAL,
                    BookingStatus.PENDING_PAYMENT, BookingStatus.CONFIRMED, BookingStatus.CHECKED_IN);
            case "past" -> List.of(BookingStatus.CHECKED_OUT, BookingStatus.COMPLETED);
            case "cancelled" -> List.of(BookingStatus.CANCELLED_BY_GUEST,
                    BookingStatus.CANCELLED_BY_HOST, BookingStatus.DECLINED, BookingStatus.EXPIRED);
            case "pending" -> List.of(BookingStatus.PENDING_HOST_APPROVAL, BookingStatus.PENDING_PAYMENT);
            default -> throw ApiException.badRequest("invalid_scope",
                    "scope must be one of: all, upcoming, past, cancelled, pending");
        };
    }

    private Property requireProperty(UUID propertyId) {
        return propertyRepository.findByIdWithPhotos(propertyId)
                .orElseThrow(() -> ApiException.notFound("listing_not_found", "Listing not found"));
    }

    private Booking requireBooking(UUID bookingId) {
        return initialize(bookingRepository.findByIdWithDetails(bookingId)
                .orElseThrow(() -> ApiException.notFound("booking_not_found", "Booking not found")));
    }

    private boolean isOverlapViolation(DataIntegrityViolationException ex) {
        String message = ex.getMostSpecificCause().getMessage();
        return message != null && message.contains(OVERLAP_CONSTRAINT);
    }

    private boolean isOversoldViolation(DataIntegrityViolationException ex) {
        String message = ex.getMostSpecificCause().getMessage();
        return message != null && message.contains(OVERSOLD_CONSTRAINT);
    }
}
