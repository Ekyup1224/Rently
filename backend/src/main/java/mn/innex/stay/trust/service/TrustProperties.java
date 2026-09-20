package mn.innex.stay.trust.service;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Trust and safety policy.
 *
 * @param payoutHold how long after check-in day begins before a host's money may
 *                   be released. The guest needs time to arrive and say something
 *                   if the listing is not there; the host should not be made to
 *                   wait for the whole stay, or the pressure to take payment
 *                   off-platform — which is where guests actually get robbed —
 *                   becomes the bigger risk.
 */
@ConfigurationProperties(prefix = "app.trust")
public record TrustProperties(Duration payoutHold) {
}
