package mn.innex.stay.payment.web;

import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import mn.innex.stay.common.web.ClientIp;
import mn.innex.stay.payment.service.PaymentService;
import mn.innex.stay.payment.web.dto.PaymentResponse;
import mn.innex.stay.payment.web.dto.StartPaymentRequest;
import mn.innex.stay.security.CurrentActor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Paying for a booking.
 *
 * <p>Charges are idempotent. A client should send an {@code Idempotency-Key} and
 * reuse it on retries; a repeat call returns the existing payment rather than
 * opening a second invoice. Without the header a key is derived from the booking,
 * which still prevents the common double-tap but cannot distinguish a deliberate
 * retry from a fresh attempt.
 */
@RestController
@RequestMapping("/api/v1/bookings/{bookingId}/payments")
public class BookingPaymentController {

    private final PaymentService paymentService;

    public BookingPaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /**
     * Opens a charge and returns what the guest needs to pay: for a QR rail, the
     * QR text and bank deeplinks; for a card rail, a checkout URL.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse start(@PathVariable UUID bookingId,
                                 @Valid @RequestBody(required = false) StartPaymentRequest request,
                                 @RequestHeader(value = "Idempotency-Key", required = false)
                                 String idempotencyKey,
                                 HttpServletRequest httpRequest) {
        UUID guestId = CurrentActor.requireUserId();
        String key = idempotencyKey == null || idempotencyKey.isBlank()
                ? "charge:" + bookingId
                : idempotencyKey.trim();

        return PaymentResponse.from(paymentService.startCharge(guestId, bookingId,
                request == null ? null : request.provider(), key, ClientIp.of(httpRequest)));
    }

    /** Every payment attempt on the booking, including refunds. */
    @GetMapping
    public List<PaymentResponse> list(@PathVariable UUID bookingId) {
        return paymentService.listForBooking(CurrentActor.requireUserId(), bookingId).stream()
                .map(PaymentResponse::from)
                .toList();
    }

    /**
     * Polls one payment. QR rails settle asynchronously, so a checkout screen
     * watches this until the status changes.
     */
    @GetMapping("/{paymentId}")
    public PaymentResponse get(@PathVariable UUID bookingId, @PathVariable UUID paymentId) {
        return PaymentResponse.from(
                paymentService.requireForGuest(CurrentActor.requireUserId(), bookingId, paymentId));
    }
}
