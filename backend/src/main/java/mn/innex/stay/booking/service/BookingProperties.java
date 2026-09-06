package mn.innex.stay.booking.service;

import java.math.BigDecimal;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Booking policy knobs.
 *
 * @param paymentHold        how long a PENDING_PAYMENT booking holds its dates
 *                           before the expiry job releases them. Long enough to
 *                           finish a bank app, short enough not to strand
 *                           inventory.
 * @param hostResponseWindow how long a host has to answer a request-to-book
 * @param maxNightsAhead     furthest future check-in accepted, so a typo cannot
 *                           block a calendar for years
 * @param taxPercent         applied to the accommodation total. Zero until the
 *                           VAT treatment of short-term rentals is confirmed by
 *                           someone qualified — see build-spec section 7.
 */
@ConfigurationProperties(prefix = "app.booking")
public record BookingProperties(
        Duration paymentHold,
        Duration hostResponseWindow,
        int maxNightsAhead,
        BigDecimal taxPercent) {
}
