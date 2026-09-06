package mn.innex.stay.payment.gateway;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import mn.innex.stay.payment.domain.PaymentProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Stand-in for QPay until merchant credentials exist.
 *
 * <p>Mimics the shape of a QR invoice rail — an invoice id, QR text, bank
 * deeplinks, and an asynchronous signed callback — so the whole book → pay →
 * confirm loop is real code exercising real state transitions. Only the money is
 * missing. When QPay credentials arrive, {@code QpayPaymentGateway} implements the
 * same interface and this bean switches off.
 *
 * <p>Its callbacks are HMAC-signed like a real provider's, so the public callback
 * endpoint has no special case for it and the signature-verification path is
 * genuinely tested.
 */
@Component
@EnableConfigurationProperties(SimulatedPaymentProperties.class)
@ConditionalOnProperty(name = "app.payments.simulated.enabled", havingValue = "true")
public class SimulatedPaymentGateway implements PaymentGateway {

    /** Header the signature travels in, mirroring how QPay and Stripe do it. */
    public static final String SIGNATURE_HEADER = "x-simulated-signature";

    private static final Logger log = LoggerFactory.getLogger(SimulatedPaymentGateway.class);
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final SimulatedPaymentProperties properties;
    private final ObjectMapper objectMapper;

    public SimulatedPaymentGateway(SimulatedPaymentProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        log.warn("Simulated payment gateway is ENABLED. Payments can be settled without money "
                + "moving. Set app.payments.simulated.enabled=false outside development.");
    }

    @Override
    public PaymentProvider provider() {
        return PaymentProvider.SIMULATED;
    }

    @Override
    public ChargeSession createCharge(ChargeRequest request) {
        // Derived from our payment id rather than random, so a retried call after a
        // timeout produces the same invoice instead of a duplicate.
        String invoiceId = "SIM-" + request.paymentId();
        Instant expiresAt = Instant.now().plus(properties.invoiceTtlOrDefault());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("invoiceId", invoiceId);
        payload.put("amount", request.amount().toPlainString());
        payload.put("currency", request.currency());
        payload.put("description", request.description());
        // Shaped like a QPay response so the frontend that renders this will not
        // need reworking when the real gateway replaces it.
        payload.put("qrText", "SIMULATED|" + invoiceId + "|" + request.amount().toPlainString());
        payload.put("deeplinks", java.util.List.of(
                Map.of("name", "Simulated Bank", "link", "simulated://pay/" + invoiceId)));
        payload.put("simulated", true);

        log.info("Simulated invoice {} opened for booking {} ({} {})",
                invoiceId, request.bookingRef(), request.amount().toPlainString(), request.currency());
        return new ChargeSession(invoiceId, payload, expiresAt);
    }

    @Override
    public RefundOutcome refund(String originalProviderRef, BigDecimal amount, String currency,
                                String reason) {
        String refundRef = "SIMREF-" + UUID.randomUUID();
        log.info("Simulated refund {} of {} {} against {} ({})",
                refundRef, amount.toPlainString(), currency, originalProviderRef, reason);
        // A real rail would settle asynchronously; the simulator settles at once so
        // cancellation flows can be verified without a second callback.
        return RefundOutcome.settled(refundRef);
    }

    @Override
    public Optional<CallbackNotification> parseAndVerify(String rawBody, Map<String, String> headers) {
        String presented = headers.get(SIGNATURE_HEADER);
        if (presented == null || presented.isBlank()) {
            log.warn("Simulated callback rejected: no signature header");
            return Optional.empty();
        }
        if (!signaturesMatch(presented, sign(rawBody))) {
            log.warn("Simulated callback rejected: signature mismatch");
            return Optional.empty();
        }

        try {
            Map<String, Object> payload = objectMapper.readValue(rawBody, Map.class);
            String providerRef = asString(payload.get("invoiceId"));
            if (providerRef == null) {
                log.warn("Simulated callback rejected: no invoiceId in payload");
                return Optional.empty();
            }
            String status = asString(payload.get("status"));
            CallbackNotification.Outcome outcome = switch (status == null ? "" : status.toUpperCase()) {
                case "PAID", "SETTLED" -> CallbackNotification.Outcome.SETTLED;
                case "FAILED" -> CallbackNotification.Outcome.FAILED;
                case "EXPIRED" -> CallbackNotification.Outcome.EXPIRED;
                default -> CallbackNotification.Outcome.PENDING;
            };
            return Optional.of(new CallbackNotification(
                    asString(payload.get("eventId")), status, providerRef, outcome, payload));
        } catch (RuntimeException ex) {
            log.warn("Simulated callback rejected: unparseable body", ex);
            return Optional.empty();
        }
    }

    /** Signs a body the way the simulator's own callbacks are signed. */
    public String sign(String rawBody) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(
                    properties.callbackSecret().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException ex) {
            throw new IllegalStateException("Could not sign simulated callback", ex);
        }
    }

    private boolean signaturesMatch(String presented, String expected) {
        return java.security.MessageDigest.isEqual(
                presented.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8));
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
