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
 * @param exposeCodeInResponse dev only — returns the code to the caller so a local
 *                          sign-in needs no log tailing. Ignored unless delivery is
 *                          {@code log}, so switching on real SMS switches this off
 *                          with it, and it must be turned on explicitly besides.
 */
@ConfigurationProperties(prefix = "app.otp")
public record OtpProperties(
        int codeLength,
        Duration ttl,
        int maxVerifyAttempts,
        Duration resendCooldown,
        int maxPerHour,
        Duration lockout,
        String delivery,
        boolean exposeCodeInResponse) {

    /** Whether a freshly issued code may be handed back to the client. */
    public boolean devCodeVisible() {
        return exposeCodeInResponse && "log".equalsIgnoreCase(delivery);
    }
}
