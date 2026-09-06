package mn.innex.stay.user.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

import mn.innex.stay.common.ApiException;
import mn.innex.stay.common.audit.AuditAction;
import mn.innex.stay.common.audit.AuditService;
import mn.innex.stay.security.JwtProperties;
import mn.innex.stay.user.domain.RefreshToken;
import mn.innex.stay.user.domain.User;
import mn.innex.stay.user.repo.RefreshTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Manages long-lived sessions as opaque, rotating, single-use tokens.
 *
 * <p>Rotation is what makes theft survivable: each refresh revokes the presented
 * token and issues a successor. If a revoked token is ever presented again, either
 * the client or an attacker is replaying, so every session for that user is killed
 * rather than guessing which party is legitimate.
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository repository;
    private final JwtProperties jwtProperties;
    private final AuditService auditService;
    /**
     * Revocations that must survive the exception thrown right after them. Rejecting
     * a token means throwing, and a throw rolls the caller's transaction back — which
     * would undo the revocation we just performed. Committing it separately is the
     * whole point, so this is not interchangeable with a plain repository call.
     */
    private final TransactionTemplate revocationTransaction;

    public RefreshTokenService(RefreshTokenRepository repository, JwtProperties jwtProperties,
                               AuditService auditService, PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.jwtProperties = jwtProperties;
        this.auditService = auditService;
        this.revocationTransaction = new TransactionTemplate(transactionManager);
        this.revocationTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** @return the raw token, which is never persisted and never recoverable afterwards */
    @Transactional
    public String issue(User user, String deviceLabel, String ip) {
        String raw = randomToken();
        RefreshToken token = new RefreshToken(user, hash(raw),
                Instant.now().plus(jwtProperties.refreshTokenTtl()), deviceLabel, ip);
        repository.save(token);
        return raw;
    }

    /**
     * Validates and rotates a refresh token.
     *
     * @return the owning user and the replacement raw token
     * @throws ApiException 401 when the token is unknown, expired, or replayed
     */
    @Transactional
    public Rotation rotate(String rawToken, String ip) {
        RefreshToken existing = repository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> ApiException.unauthorized("refresh_token_invalid",
                        "Refresh token is not recognized"));

        if (existing.getRevokedAt() != null && existing.getReplacedBy() == null) {
            // Revoked by logout, suspension or a previous family kill. Nothing was
            // replayed, so this is an ordinary dead session: refuse it and stop.
            throw ApiException.unauthorized("refresh_token_revoked",
                    "Session has ended. Please sign in again.");
        }

        if (existing.getRevokedAt() != null) {
            // Rotated, then presented a second time. Either the client or a thief is
            // replaying it, and there is no way to tell which, so end every session.
            int revoked = revokeAllInSeparateTransaction(existing.getUser().getId());
            log.warn("Refresh token reuse detected for user {}; revoked {} sessions",
                    existing.getUser().getId(), revoked);
            auditService.record(existing.getUser().getId(), AuditAction.TOKEN_REUSE_DETECTED,
                    "RefreshToken", existing.getId(), Map.of("revokedSessions", revoked), ip);
            throw ApiException.unauthorized("refresh_token_reused",
                    "Session ended for security reasons. Please sign in again.");
        }

        if (!existing.isActive(Instant.now())) {
            throw ApiException.unauthorized("refresh_token_expired", "Refresh token has expired");
        }

        User user = existing.getUser();
        if (!user.getStatus().canAuthenticate()) {
            revokeAllInSeparateTransaction(user.getId());
            throw ApiException.forbidden("account_not_active", "This account cannot sign in");
        }

        String replacementRaw = randomToken();
        RefreshToken replacement = new RefreshToken(user, hash(replacementRaw),
                Instant.now().plus(jwtProperties.refreshTokenTtl()), existing.getDeviceLabel(), ip);
        repository.save(replacement);
        existing.replaceWith(replacement);
        repository.save(existing);

        return new Rotation(user, replacementRaw);
    }

    /** Revokes a single session. Unknown tokens are ignored: logout is idempotent. */
    @Transactional
    public void revoke(String rawToken) {
        repository.findByTokenHash(hash(rawToken)).ifPresent(token -> {
            token.revoke();
            repository.save(token);
        });
    }

    @Transactional
    public int revokeAllForUser(UUID userId) {
        return repository.revokeAllForUser(userId, Instant.now());
    }

    /** Commits a revoke-all immediately, so a subsequent throw cannot undo it. */
    private int revokeAllInSeparateTransaction(UUID userId) {
        Integer revoked = revocationTransaction.execute(
                status -> repository.revokeAllForUser(userId, Instant.now()));
        return revoked == null ? 0 : revoked;
    }

    private String randomToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    public record Rotation(User user, String refreshToken) {
    }
}
