package mn.innex.stay.user.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;

import mn.innex.stay.common.ApiException;
import mn.innex.stay.common.PhoneNumbers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Issues and verifies one-time codes, with all state in Redis so codes expire on
 * their own and no cleanup job is needed.
 *
 * <p>Only the SHA-256 of a code is stored. Three independent limits apply per
 * destination: a resend cooldown, an hourly cap, and a per-code attempt cap whose
 * exhaustion freezes the destination for the lockout window.
 */
@Service
public class OtpService {

    private static final Logger log = LoggerFactory.getLogger(OtpService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redis;
    private final OtpProperties properties;
    private final SmsSender smsSender;

    public OtpService(StringRedisTemplate redis, OtpProperties properties, SmsSender smsSender) {
        this.redis = redis;
        this.properties = properties;
        this.smsSender = smsSender;
    }

    /**
     * Generates a code, stores its hash, and hands it to the SMS gateway.
     *
     * @return the cooldown, and in development the code itself
     * @throws ApiException 429 when locked out, on cooldown, or over the hourly cap
     */
    public Issued issue(String destination, OtpPurpose purpose) {
        assertNotLockedOut(destination);
        assertNotOnCooldown(destination, purpose);
        assertUnderHourlyCap(destination);

        String code = randomCode();
        redis.opsForValue().set(codeKey(destination, purpose), hash(destination, purpose, code), properties.ttl());
        redis.delete(attemptsKey(destination, purpose));
        redis.opsForValue().set(cooldownKey(destination, purpose), "1", properties.resendCooldown());

        smsSender.send(destination, "%s is your verification code. It expires in %d minutes."
                .formatted(code, Math.max(1, properties.ttl().toMinutes())));
        log.debug("Issued OTP purpose={} destination={}", purpose, PhoneNumbers.mask(destination));
        return new Issued(properties.resendCooldown(),
                properties.devCodeVisible() ? code : null);
    }

    /**
     * What issuing produced.
     *
     * @param devCode the plain code, only ever non-null in a development
     *                configuration; see {@link OtpProperties#devCodeVisible()}
     */
    public record Issued(Duration resendAfter, String devCode) {
    }

    /**
     * Consumes the code on success. On failure the attempt counter advances, and
     * exhausting it burns the code and locks the destination.
     *
     * @throws ApiException 400 {@code otp_invalid} / {@code otp_expired}, 429 {@code otp_locked}
     */
    public void verify(String destination, OtpPurpose purpose, String submittedCode) {
        assertNotLockedOut(destination);

        String key = codeKey(destination, purpose);
        String expected = redis.opsForValue().get(key);
        if (expected == null) {
            throw ApiException.badRequest("otp_expired", "The code has expired. Request a new one.");
        }

        if (!constantTimeEquals(expected, hash(destination, purpose, submittedCode))) {
            long attempts = increment(attemptsKey(destination, purpose), properties.ttl());
            if (attempts >= properties.maxVerifyAttempts()) {
                redis.delete(key);
                redis.opsForValue().set(lockKey(destination), "1", properties.lockout());
                throw ApiException.tooManyRequests("otp_locked",
                        "Too many incorrect codes. Try again in %d minutes."
                                .formatted(Math.max(1, properties.lockout().toMinutes())));
            }
            throw ApiException.badRequest("otp_invalid", "The code is incorrect.");
        }

        redis.delete(key);
        redis.delete(attemptsKey(destination, purpose));
    }

    private void assertNotLockedOut(String destination) {
        if (Boolean.TRUE.equals(redis.hasKey(lockKey(destination)))) {
            throw ApiException.tooManyRequests("otp_locked",
                    "This number is temporarily locked. Try again later.");
        }
    }

    private void assertNotOnCooldown(String destination, OtpPurpose purpose) {
        Long remaining = redis.getExpire(cooldownKey(destination, purpose));
        if (remaining != null && remaining > 0) {
            throw ApiException.tooManyRequests("otp_cooldown",
                    "Please wait %d seconds before requesting another code.".formatted(remaining));
        }
    }

    private void assertUnderHourlyCap(String destination) {
        long count = increment(hourlyKey(destination), Duration.ofHours(1));
        if (count > properties.maxPerHour()) {
            throw ApiException.tooManyRequests("otp_rate_limited",
                    "Too many codes requested for this number. Try again in an hour.");
        }
    }

    /** Increments a counter, setting the TTL only when the key is first created. */
    private long increment(String key, Duration ttl) {
        Long value = redis.opsForValue().increment(key);
        long count = value == null ? 1L : value;
        if (count == 1L) {
            redis.expire(key, ttl);
        }
        return count;
    }

    private String randomCode() {
        int bound = (int) Math.pow(10, properties.codeLength());
        int code = RANDOM.nextInt(bound);
        return String.format("%0" + properties.codeLength() + "d", code);
    }

    /** Destination and purpose are folded into the hash so a code is useless elsewhere. */
    private String hash(String destination, OtpPurpose purpose, String code) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((destination + '|' + purpose.name() + '|' + code)
                    .getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private boolean constantTimeEquals(String left, String right) {
        return MessageDigest.isEqual(left.getBytes(StandardCharsets.UTF_8),
                right.getBytes(StandardCharsets.UTF_8));
    }

    private String codeKey(String destination, OtpPurpose purpose) {
        return "otp:code:" + purpose.name() + ':' + destination;
    }

    private String attemptsKey(String destination, OtpPurpose purpose) {
        return "otp:attempts:" + purpose.name() + ':' + destination;
    }

    private String cooldownKey(String destination, OtpPurpose purpose) {
        return "otp:cooldown:" + purpose.name() + ':' + destination;
    }

    private String hourlyKey(String destination) {
        return "otp:hourly:" + destination;
    }

    private String lockKey(String destination) {
        return "otp:lock:" + destination;
    }
}
