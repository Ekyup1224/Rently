package mn.innex.stay.user.auth;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * One-time-code policy.
 *
 * @param codeLength        digits in the code
 * @param ttl               how long a code stays valid
 * @param maxVerifyAttempts wrong guesses allowed before the code is burned
 * @param resendCooldown    minimum gap between two sends to the same destination
 * @param maxPerHour        codes per destination per rolling hour
 * @param lockout           how long a destination is frozen after exhausting attempts
 * @param delivery          {@code log} writes codes to the log; {@code sms} uses the carrier gateway
 */
@ConfigurationProperties(prefix = "app.otp")
public record OtpProperties(
        int codeLength,
        Duration ttl,
        int maxVerifyAttempts,
        Duration resendCooldown,
        int maxPerHour,
        Duration lockout,
        String delivery) {
}
