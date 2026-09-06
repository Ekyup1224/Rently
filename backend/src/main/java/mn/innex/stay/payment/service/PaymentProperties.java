package mn.innex.stay.payment.service;

import mn.innex.stay.payment.domain.PaymentProvider;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param defaultProvider used when the client does not name one. Set to QPAY once
 *                        merchant credentials are in place.
 */
@ConfigurationProperties(prefix = "app.payments")
public record PaymentProperties(PaymentProvider defaultProvider) {

    public PaymentProvider defaultProviderOrSimulated() {
        return defaultProvider == null ? PaymentProvider.SIMULATED : defaultProvider;
    }
}
