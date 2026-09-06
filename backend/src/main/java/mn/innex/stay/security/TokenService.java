package mn.innex.stay.security;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import mn.innex.stay.user.domain.Role;
import mn.innex.stay.user.domain.User;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/**
 * Issues signed access tokens. Refresh tokens are opaque random strings handled
 * by {@code RefreshTokenService} — only access tokens are JWTs, so a stolen
 * refresh token is worthless without a database row to match it.
 */
@Service
public class TokenService {

    /** Claim holding role names without the {@code ROLE_} prefix. */
    public static final String CLAIM_ROLES = "roles";
    /** Claim holding organization ids the user holds a hotel-side role in. */
    public static final String CLAIM_ORGS = "orgs";

    private final JwtEncoder encoder;
    private final JwtProperties properties;

    public TokenService(JwtEncoder encoder, JwtProperties properties) {
        this.encoder = encoder;
        this.properties = properties;
    }

    public AccessToken issueAccessToken(User user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.accessTokenTtl());
        List<String> roles = user.roleNames().stream().map(Role::name).toList();
        List<String> orgs = user.organizationIds().stream().map(UUID::toString).toList();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .subject(user.getId().toString())
                .id(UUID.randomUUID().toString())
                .claim(CLAIM_ROLES, roles)
                .claim(CLAIM_ORGS, orgs)
                .build();

        String value = encoder.encode(
                JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        return new AccessToken(value, expiresAt, properties.accessTokenTtl().toSeconds());
    }

    public record AccessToken(String value, Instant expiresAt, long expiresInSeconds) {
    }
}
