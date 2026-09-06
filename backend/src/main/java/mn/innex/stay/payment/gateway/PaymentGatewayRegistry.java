package mn.innex.stay.payment.gateway;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import mn.innex.stay.common.ApiException;
import mn.innex.stay.payment.domain.PaymentProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The gateways this deployment actually has credentials for.
 *
 * <p>Providers are wired by presence: a gateway bean that is not configured is not
 * registered, and asking for it returns a clear 400 rather than failing deep in a
 * checkout. That is what lets QPay, SocialPay and Stripe be added one at a time as
 * their merchant accounts come through.
 */
@Component
public class PaymentGatewayRegistry {

    private static final Logger log = LoggerFactory.getLogger(PaymentGatewayRegistry.class);

    private final Map<PaymentProvider, PaymentGateway> gateways =
            new EnumMap<>(PaymentProvider.class);

    public PaymentGatewayRegistry(List<PaymentGateway> discovered) {
        for (PaymentGateway gateway : discovered) {
            gateways.put(gateway.provider(), gateway);
        }
        log.info("Payment providers available: {}", gateways.keySet());
    }

    /** @throws ApiException 400 when the provider is not enabled here */
    public PaymentGateway require(PaymentProvider provider) {
        PaymentGateway gateway = gateways.get(provider);
        if (gateway == null) {
            throw ApiException.badRequest("payment_provider_unavailable",
                    "Payment provider " + provider + " is not available. Available: "
                            + gateways.keySet());
        }
        return gateway;
    }

    public Set<PaymentProvider> available() {
        return gateways.keySet();
    }

    public boolean isAvailable(PaymentProvider provider) {
        return gateways.containsKey(provider);
    }
}
