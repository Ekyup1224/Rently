package mn.innex.stay.payment.gateway;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for the stand-in gateway.
 *
 * @param enabled        must be false anywhere real: the simulator can mark a
 *                       payment settled without money moving
 * @param callbackSecret HMAC key its callbacks are signed with, so even the
 *                       simulator's endpoint is not an open door
 */
@ConfigurationProperties(prefix = "app.payments.simulated")
public record SimulatedPaymentProperties(
        boolean enabled,
        String callbackSecret,
        Duration invoiceTtl) {

    public Duration invoiceTtlOrDefault() {
        return invoiceTtl == null ? Duration.ofMinutes(30) : invoiceTtl;
    }
}
