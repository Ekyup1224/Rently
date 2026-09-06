package mn.innex.stay.payment.gateway;

import java.time.Instant;
import java.util.Map;

/**
 * An open charge at the provider.
 *
 * @param providerRef      the provider's transaction id, unique per payment
 * @param checkoutPayload  whatever the client needs to complete payment: QR text,
 *                         bank deeplinks, a hosted checkout URL. Deliberately
 *                         untyped, because each rail offers something different
 *                         and the frontend renders what it finds.
 * @param expiresAt        when the provider's invoice lapses
 */
public record ChargeSession(
        String providerRef,
        Map<String, Object> checkoutPayload,
        Instant expiresAt) {
}
