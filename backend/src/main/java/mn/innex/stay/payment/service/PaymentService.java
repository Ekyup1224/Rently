package mn.innex.stay.payment.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import mn.innex.stay.booking.domain.Booking;
import mn.innex.stay.booking.domain.BookingStatus;
import mn.innex.stay.booking.repo.BookingRepository;
import mn.innex.stay.booking.service.BookingService;
import mn.innex.stay.booking.service.RefundPort;
import mn.innex.stay.common.ApiException;
import mn.innex.stay.common.Money;
import mn.innex.stay.common.audit.AuditAction;
import mn.innex.stay.common.audit.AuditService;
import mn.innex.stay.payment.domain.Payment;
import mn.innex.stay.payment.domain.PaymentEvent;
import mn.innex.stay.payment.domain.PaymentIntent;
import mn.innex.stay.payment.domain.PaymentProvider;
import mn.innex.stay.payment.domain.PaymentRecordStatus;
import mn.innex.stay.payment.gateway.CallbackNotification;
import mn.innex.stay.payment.gateway.ChargeRequest;
import mn.innex.stay.payment.gateway.ChargeSession;
import mn.innex.stay.payment.gateway.PaymentGateway;
import mn.innex.stay.payment.gateway.PaymentGatewayRegistry;
import mn.innex.stay.payment.gateway.RefundOutcome;
import mn.innex.stay.payment.repo.PaymentEventRepository;
import mn.innex.stay.payment.repo.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Charges, refunds and provider callbacks.
 *
 * <p>Three properties matter more than anything else here, because payment bugs
 * cost real money:
 *
 * <ul>
 *   <li><b>Idempotency.</b> A charge is keyed by an idempotency key, and a repeat
 *       call returns the existing payment rather than opening a second invoice.
 *   <li><b>Callback authenticity.</b> A callback endpoint is public. Nothing is
 *       acted on until the gateway has verified the provider's signature over the
 *       exact raw body.
 *   <li><b>Replay safety.</b> Callbacks arrive more than once by design. Every
 *       delivery is logged, deduped, and applied only if it changes state.
 * </ul>
 */
@Service
public class PaymentService implements RefundPort {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository paymentRepository;
    private final PaymentEventRepository paymentEventRepository;
    private final BookingRepository bookingRepository;
    private final BookingService bookingService;
    private final PaymentGatewayRegistry gateways;
    private final PaymentProperties properties;
    private final AuditService auditService;
    /**
     * Callback logging commits independently of processing, so a payload that
     * blows up on the way through is still on disk to replay after a fix.
     */
    private final TransactionTemplate eventLogTransaction;

    public PaymentService(PaymentRepository paymentRepository,
                          PaymentEventRepository paymentEventRepository,
                          BookingRepository bookingRepository,
                          BookingService bookingService,
                          PaymentGatewayRegistry gateways,
                          PaymentProperties properties,
                          AuditService auditService,
                          PlatformTransactionManager transactionManager) {
        this.paymentRepository = paymentRepository;
        this.paymentEventRepository = paymentEventRepository;
        this.bookingRepository = bookingRepository;
        this.bookingService = bookingService;
        this.gateways = gateways;
        this.properties = properties;
        this.auditService = auditService;
        this.eventLogTransaction = new TransactionTemplate(transactionManager);
        this.eventLogTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Opens a charge for a booking awaiting payment.
     *
     * @param idempotencyKey supplied by the client; repeating it returns the same
     *                       payment instead of charging twice
     * @throws ApiException 409 when the booking is not awaiting payment
     */
    @Transactional
    public Payment startCharge(UUID guestId, UUID bookingId, PaymentProvider requestedProvider,
                               String idempotencyKey, String ip) {
        Booking booking = bookingService.requireForGuest(guestId, bookingId);

        if (booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
            throw ApiException.conflict("booking_not_awaiting_payment",
                    "This booking is not awaiting payment");
        }

        // Same key, same answer: this is what makes a double-tapped Pay button safe.
        Optional<Payment> existingByKey = paymentRepository.findByIdempotencyKey(idempotencyKey);
        if (existingByKey.isPresent()) {
            Payment existing = existingByKey.get();
            if (!existing.getBooking().getId().equals(bookingId)) {
                throw ApiException.conflict("idempotency_key_reused",
                        "That idempotency key was already used for a different booking");
            }
            return existing;
        }

        // An invoice already open for this booking is reused rather than duplicated,
        // so a guest who reloads checkout sees the same QR code.
        Optional<Payment> openCharge = paymentRepository
                .findByBookingIdOrderByCreatedAtDesc(bookingId).stream()
                .filter(payment -> payment.getIntent() == PaymentIntent.CHARGE)
                .filter(payment -> payment.getStatus() == PaymentRecordStatus.PENDING)
                .findFirst();
        if (openCharge.isPresent()) {
            return openCharge.get();
        }

        PaymentProvider provider = requestedProvider == null
                ? properties.defaultProviderOrSimulated()
                : requestedProvider;
        PaymentGateway gateway = gateways.require(provider);

        Payment payment = paymentRepository.saveAndFlush(
                Payment.charge(booking, provider, booking.getTotal(), idempotencyKey));

        ChargeSession session = gateway.createCharge(new ChargeRequest(
                payment.getId(), booking.getReference(), booking.getTotal(),
                booking.getCurrency(),
                "Stay " + booking.getReference() + " · " + booking.nightCount() + " night(s)"));

        payment.markPending(session.providerRef(), session.checkoutPayload(), session.expiresAt());
        paymentRepository.save(payment);
        bookingService.markPaymentProcessing(bookingId);

        auditService.record(guestId, AuditAction.PAYMENT_STARTED, "Payment", payment.getId(),
                Map.of("bookingRef", booking.getReference(), "provider", provider.name(),
                        "amount", booking.getTotal().toPlainString()), ip);
        return payment;
    }

    @Transactional(readOnly = true)
    public List<Payment> listForBooking(UUID guestId, UUID bookingId) {
        bookingService.requireForGuest(guestId, bookingId);
        return paymentRepository.findByBookingIdOrderByCreatedAtDesc(bookingId);
    }

    @Transactional(readOnly = true)
    public Payment requireForGuest(UUID guestId, UUID bookingId, UUID paymentId) {
        bookingService.requireForGuest(guestId, bookingId);
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> ApiException.notFound("payment_not_found", "Payment not found"));
        if (!payment.getBooking().getId().equals(bookingId)) {
            throw ApiException.notFound("payment_not_found", "Payment not found");
        }
        return payment;
    }

    /**
     * Applies a provider callback.
     *
     * @return a short description of what was done, for the provider's benefit and
     *         for the logs
     * @throws ApiException 401 when the signature does not verify
     */
    @Transactional
    public String handleCallback(PaymentProvider provider, String rawBody,
                                 Map<String, String> headers) {
        PaymentGateway gateway = gateways.require(provider);
        Optional<CallbackNotification> verified = gateway.parseAndVerify(rawBody, headers);

        if (verified.isEmpty()) {
            logEvent(new PaymentEvent(provider, null, "SIGNATURE_REJECTED",
                    Map.of("bodyLength", rawBody == null ? 0 : rawBody.length()), false));
            auditService.record(null, AuditAction.PAYMENT_CALLBACK_REJECTED, "Payment", (String) null,
                    Map.of("provider", provider.name(), "reason", "signature"), null);
            throw ApiException.unauthorized("callback_signature_invalid",
                    "Callback signature could not be verified");
        }

        CallbackNotification notification = verified.get();

        // Redelivery is normal; a second attempt at the same event is a no-op.
        if (notification.providerEventId() != null) {
            Optional<PaymentEvent> alreadySeen = paymentEventRepository
                    .findByProviderAndProviderEventId(provider, notification.providerEventId());
            if (alreadySeen.isPresent()) {
                log.debug("Ignoring duplicate {} callback {}", provider, notification.providerEventId());
                return "duplicate";
            }
        }

        PaymentEvent event = logEvent(new PaymentEvent(provider, notification.providerEventId(),
                notification.eventType(), notification.payload(), true));

        Optional<Payment> found = paymentRepository
                .findByProviderAndProviderRef(provider, notification.providerRef());
        if (found.isEmpty()) {
            // Retrying will not help, so this is recorded rather than failed: an
            // unknown reference needs a human, not a redelivery.
            log.error("{} callback for unknown reference {}", provider, notification.providerRef());
            event.markFailed("No payment matches provider reference " + notification.providerRef());
            logEvent(event);
            return "unmatched";
        }

        Payment payment = found.get();
        String result = applyOutcome(payment, notification);
        event.markProcessed(payment.getId());
        logEvent(event);
        return result;
    }

    private String applyOutcome(Payment payment, CallbackNotification notification) {
        UUID bookingId = payment.getBooking().getId();

        switch (notification.outcome()) {
            case SETTLED -> {
                if (payment.getStatus() == PaymentRecordStatus.SUCCEEDED) {
                    return "already-settled";
                }
                payment.markSucceeded();
                paymentRepository.save(payment);

                if (payment.getIntent() == PaymentIntent.CHARGE) {
                    bookingService.confirmPaid(bookingId);
                }
                auditService.record(null, AuditAction.PAYMENT_SUCCEEDED, "Payment", payment.getId(),
                        Map.of("bookingId", bookingId.toString(),
                                "amount", payment.getAmount().toPlainString(),
                                "intent", payment.getIntent().name()), null);
                return "settled";
            }
            case FAILED -> {
                payment.markFailed("provider_reported_failure",
                        String.valueOf(notification.payload().get("message")));
                paymentRepository.save(payment);
                bookingService.markPaymentFailed(bookingId);
                auditService.record(null, AuditAction.PAYMENT_FAILED, "Payment", payment.getId(),
                        Map.of("bookingId", bookingId.toString()), null);
                return "failed";
            }
            case EXPIRED -> {
                payment.markExpired();
                paymentRepository.save(payment);
                return "expired";
            }
            default -> {
                return "pending";
            }
        }
    }

    /**
     * Returns money for a cancelled booking. Implements the booking module's
     * {@link RefundPort}, which is what keeps booking free of any dependency on
     * this module.
     */
    @Override
    @Transactional
    public void refundForBooking(UUID bookingId, BigDecimal amount, String reason) {
        if (!Money.isPositive(amount)) {
            return;
        }

        Optional<Payment> settledCharge = paymentRepository
                .findFirstByBookingIdAndIntentAndStatusOrderByPaidAtDesc(
                        bookingId, PaymentIntent.CHARGE, PaymentRecordStatus.SUCCEEDED);
        if (settledCharge.isEmpty()) {
            // Nothing was ever collected, so there is nothing to return. Not an
            // error: an unpaid booking can be cancelled.
            log.info("No settled charge on booking {}; refund of {} not needed", bookingId, amount);
            return;
        }

        Payment original = settledCharge.get();
        if (Money.of(amount).compareTo(original.getAmount()) > 0) {
            log.error("Refund of {} exceeds the {} charged on booking {}; refusing",
                    amount, original.getAmount(), bookingId);
            throw ApiException.conflict("refund_exceeds_charge",
                    "A refund cannot exceed the amount charged");
        }

        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> ApiException.notFound("booking_not_found", "Booking not found"));

        // Derived from the booking and charge rather than random, so a retried
        // cancellation cannot issue two refunds for the same money.
        String idempotencyKey = "refund:" + bookingId + ":" + original.getId();
        Optional<Payment> alreadyRefunded = paymentRepository.findByIdempotencyKey(idempotencyKey);
        if (alreadyRefunded.isPresent()) {
            log.info("Refund already recorded for booking {}", bookingId);
            return;
        }

        Payment refund = paymentRepository.saveAndFlush(
                Payment.refund(booking, original, amount, idempotencyKey));

        RefundOutcome outcome = gateways.require(original.getProvider())
                .refund(original.getProviderRef(), Money.of(amount), booking.getCurrency(), reason);

        if (outcome.settled()) {
            refund.markPending(outcome.providerRef(), Map.of("reason", reason), null);
            refund.markSucceeded();
        } else {
            refund.markFailed("refund_not_settled", outcome.failureMessage());
        }
        paymentRepository.save(refund);

        auditService.record(null, AuditAction.REFUND_ISSUED, "Payment", refund.getId(),
                Map.of("bookingId", bookingId.toString(), "amount", Money.of(amount).toPlainString(),
                        "settled", outcome.settled(), "reason", reason), null);
    }

    /** Commits the event row on its own so it survives a failure further down. */
    private PaymentEvent logEvent(PaymentEvent event) {
        return eventLogTransaction.execute(status -> paymentEventRepository.save(event));
    }
}
