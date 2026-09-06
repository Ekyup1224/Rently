package mn.innex.stay.payment.gateway;

import java.math.BigDecimal;
import java.util.Map;

import mn.innex.stay.payment.domain.PaymentProvider;

/**
 * One payment rail.
 *
 * <p>The abstraction exists so the booking flow can be built and verified before
 * merchant credentials arrive: {@code SimulatedPaymentGateway} mimics QPay's
 * invoice/QR/callback shape today, and swapping in the real client is a
 * configuration change rather than a rewrite.
 *
 * <p>Implementations must be safe to call twice with the same
 * {@link ChargeRequest#paymentId()}: the caller's idempotency guard is the first
 * line of defence, but a retry after a timeout must not create a second invoice.
 */
public interface PaymentGateway {

    PaymentProvider provider();

    /** Opens a charge and returns what the payer needs to complete it. */
    ChargeSession createCharge(ChargeRequest request);

    /**
     * Returns money against a settled charge.
     *
     * @param originalProviderRef the charge being reversed
     */
    RefundOutcome refund(String originalProviderRef, BigDecimal amount, String currency,
                         String reason);

    /**
     * Authenticates and normalizes an inbound callback.
     *
     * <p>A callback endpoint is public, so this is the only thing standing between
     * the internet and a booking marked paid. Implementations must verify the
     * provider's signature over the exact raw body and return empty on failure —
     * never trust a parsed field to decide authenticity.
     *
     * @param rawBody the body exactly as received, before any parsing
     * @return empty when the callback cannot be authenticated
     */
    java.util.Optional<CallbackNotification> parseAndVerify(String rawBody,
                                                            Map<String, String> headers);
}
