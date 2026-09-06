package mn.innex.stay.payment.gateway;

import java.util.Map;

/**
 * A provider callback, normalized across rails.
 *
 * @param providerEventId the provider's id for this delivery, used to dedupe
 *                        redeliveries. Null when a provider does not supply one,
 *                        in which case dedupe falls back to payment state.
 * @param providerRef     the transaction this concerns
 * @param outcome         what the provider is telling us happened
 */
public record CallbackNotification(
        String providerEventId,
        String eventType,
        String providerRef,
        Outcome outcome,
        Map<String, Object> payload) {

    public enum Outcome {
        SETTLED, FAILED, EXPIRED, /** Informational; no state change. */ PENDING
    }
}
