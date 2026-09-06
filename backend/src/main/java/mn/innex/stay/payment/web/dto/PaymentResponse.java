package mn.innex.stay.payment.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import mn.innex.stay.payment.domain.Payment;
import mn.innex.stay.payment.domain.PaymentIntent;
import mn.innex.stay.payment.domain.PaymentProvider;
import mn.innex.stay.payment.domain.PaymentRecordStatus;

/**
 * A payment attempt and whatever the payer needs to complete it.
 *
 * @param checkout provider-specific material — QR text, bank deeplinks, a hosted
 *                 URL. Untyped on purpose: each rail offers something different
 *                 and the client renders what it finds rather than the API
 *                 pretending they are all the same.
 */
public record PaymentResponse(
        UUID id,
        UUID bookingId,
        PaymentProvider provider,
        PaymentIntent intent,
        PaymentRecordStatus status,
        BigDecimal amount,
        String currency,
        Map<String, Object> checkout,
        Instant expiresAt,
        Instant paidAt,
        String failureMessage,
        Instant createdAt) {

    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getId(), payment.getBooking().getId(), payment.getProvider(),
                payment.getIntent(), payment.getStatus(), payment.getAmount(),
                payment.getCurrency(), payment.getCheckoutPayload(), payment.getExpiresAt(),
                payment.getPaidAt(), payment.getFailureMessage(), payment.getCreatedAt());
    }
}
