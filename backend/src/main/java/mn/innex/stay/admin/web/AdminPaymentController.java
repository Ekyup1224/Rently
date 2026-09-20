package mn.innex.stay.admin.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import mn.innex.stay.common.ApiException;
import mn.innex.stay.common.audit.AuditAction;
import mn.innex.stay.common.audit.AuditService;
import mn.innex.stay.common.web.ClientIp;
import mn.innex.stay.common.web.PageResponse;
import mn.innex.stay.payment.domain.Payment;
import mn.innex.stay.payment.domain.PaymentIntent;
import mn.innex.stay.payment.domain.PaymentProvider;
import mn.innex.stay.payment.domain.PaymentRecordStatus;
import mn.innex.stay.payment.repo.PaymentEventRepository;
import mn.innex.stay.payment.repo.PaymentRepository;
import mn.innex.stay.payment.service.PaymentService;
import mn.innex.stay.security.CurrentActor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Payment oversight: what has been charged, what failed, and putting money back.
 *
 * <p>Read-only apart from the refund. Everything a support case needs — the
 * transaction, its provider reference, and the raw callbacks the provider sent —
 * without anyone opening a database console, which is how payment data gets
 * changed by accident.
 */
@RestController
@RequestMapping("/api/v1/admin/payments")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminPaymentController {

    private static final ZoneId ULAANBAATAR = ZoneId.of("Asia/Ulaanbaatar");

    private final PaymentRepository payments;
    private final PaymentEventRepository events;
    private final PaymentService paymentService;
    private final AuditService auditService;

    public AdminPaymentController(PaymentRepository payments, PaymentEventRepository events,
                                  PaymentService paymentService, AuditService auditService) {
        this.payments = payments;
        this.events = events;
        this.paymentService = paymentService;
        this.auditService = auditService;
    }

    /**
     * @param query a booking reference or a provider reference
     * @param from  inclusive date, Ulaanbaatar time; {@code to} is inclusive too
     */
    @GetMapping
    @Transactional(readOnly = true)
    public PageResponse<PaymentView> search(
            @RequestParam(required = false) PaymentRecordStatus status,
            @RequestParam(required = false) PaymentProvider provider,
            @RequestParam(required = false) PaymentIntent intent,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) String query,
            @PageableDefault(size = 25, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        Instant start = from == null ? null : from.atStartOfDay(ULAANBAATAR).toInstant();
        Instant end = to == null ? null : to.plusDays(1).atStartOfDay(ULAANBAATAR).toInstant();
        String text = query == null || query.isBlank() ? null : query.trim();

        return PageResponse.of(
                payments.search(status, provider, intent, start, end, text, pageable),
                PaymentView::from);
    }

    /** One payment with the provider's own account of what happened to it. */
    @GetMapping("/{paymentId}")
    @Transactional(readOnly = true)
    public PaymentDetail detail(@PathVariable UUID paymentId) {
        Payment payment = payments.findByIdWithBooking(paymentId)
                .orElseThrow(() -> ApiException.notFound("payment_not_found", "No such payment"));
        List<EventView> trail = events.findByPaymentIdOrderByReceivedAtAsc(paymentId).stream()
                .map(event -> new EventView(event.getId(), event.getEventType(),
                        event.isSignatureVerified(), event.getReceivedAt(),
                        event.getProcessedAt(), event.getProcessingError()))
                .toList();
        return new PaymentDetail(PaymentView.from(payment), trail);
    }

    /**
     * Returns money to a guest outside the cancellation flow — a goodwill refund,
     * a duplicate charge, a stay that went wrong.
     *
     * <p>Goes through the same service the cancellation path uses, so the refund
     * is a real provider reversal with its own payment row rather than a number
     * edited in a table.
     */
    @PostMapping("/{paymentId}/refund")
    public Map<String, Object> refund(@PathVariable UUID paymentId,
                                      @Valid @RequestBody RefundRequest request,
                                      HttpServletRequest httpRequest) {
        Payment payment = payments.findByIdWithBooking(paymentId)
                .orElseThrow(() -> ApiException.notFound("payment_not_found", "No such payment"));
        if (payment.getIntent() != PaymentIntent.CHARGE
                || payment.getStatus() != PaymentRecordStatus.SUCCEEDED) {
            throw ApiException.conflict("not_refundable",
                    "Only a settled charge can be refunded");
        }
        if (request.amount().compareTo(payment.getAmount()) > 0) {
            throw ApiException.badRequest("refund_exceeds_charge",
                    "A refund cannot exceed what was charged");
        }

        UUID adminId = CurrentActor.requireUserId();
        paymentService.refundForBooking(payment.getBooking().getId(), request.amount(),
                request.reason());
        auditService.record(adminId, AuditAction.REFUND_ISSUED, "Payment", paymentId,
                Map.of("amount", request.amount().toPlainString(), "reason", request.reason(),
                        "by", "ADMIN"),
                ClientIp.of(httpRequest));
        return Map.of("refunded", request.amount(), "paymentId", paymentId);
    }

    public record RefundRequest(
            @NotNull @DecimalMin("1") BigDecimal amount,
            @NotBlank @Size(max = 500) String reason) {
    }

    /** One transaction, with enough of its booking to be recognisable. */
    public record PaymentView(
            UUID id,
            UUID bookingId,
            String bookingReference,
            String provider,
            String intent,
            String status,
            BigDecimal amount,
            String currency,
            String providerRef,
            String failureCode,
            String failureMessage,
            Instant paidAt,
            Instant createdAt) {

        static PaymentView from(Payment payment) {
            return new PaymentView(payment.getId(), payment.getBooking().getId(),
                    payment.getBooking().getReference(), payment.getProvider().name(),
                    payment.getIntent().name(), payment.getStatus().name(), payment.getAmount(),
                    payment.getCurrency(), payment.getProviderRef(), payment.getFailureCode(),
                    payment.getFailureMessage(), payment.getPaidAt(), payment.getCreatedAt());
        }
    }

    /**
     * @param signatureVerified false means the callback arrived unsigned or with a
     *                          bad signature, which is worth seeing while
     *                          investigating
     */
    public record EventView(UUID id, String eventType, boolean signatureVerified,
                            Instant receivedAt, Instant processedAt, String processingError) {
    }

    public record PaymentDetail(PaymentView payment, List<EventView> events) {
    }
}
