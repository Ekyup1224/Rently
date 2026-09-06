package mn.innex.stay.security;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Access/refresh token settings.
 *
 * @param issuer   {@code iss} claim, also verified on decode
 * @param secret   HS256 signing key; must be at least 32 bytes
 * @param accessTokenTtl  short by design — revocation of an access token is not possible
 * @param refreshTokenTtl how long a device stays signed in without re-authenticating
 */
@ConfigurationProperties(prefix = "app.security.jwt")
public record JwtProperties(
        String issuer,
        String secret,
        Duration accessTokenTtl,
        Duration refreshTokenTtl) {

    /** HS256 needs a 256-bit key; a shorter secret makes Nimbus throw at startup. */
    public static final int MIN_SECRET_BYTES = 32;
}
