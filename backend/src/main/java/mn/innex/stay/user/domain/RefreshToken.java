package mn.innex.stay.user.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

/**
 * A refresh-token session. Only the SHA-256 hash of the token is persisted, so
 * the raw token exists solely in the client's storage.
 *
 * <p>Tokens rotate on every refresh: the old row is revoked and points at its
 * successor via {@code replacedBy}. Presenting an already-revoked token is
 * therefore detectable as replay, and the whole family is killed.
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token_hash", nullable = false, length = 64, unique = true)
    private String tokenHash;

    @Column(name = "device_label", length = 120)
    private String deviceLabel;

    @Column(length = 64)
    private String ip;

    @Column(name = "issued_at", nullable = false, insertable = false, updatable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "replaced_by")
    private UUID replacedBy;

    protected RefreshToken() {
    }

    public RefreshToken(User user, String tokenHash, Instant expiresAt, String deviceLabel, String ip) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.deviceLabel = deviceLabel;
        this.ip = ip;
    }

    public boolean isActive(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }

    public void revoke() {
        if (revokedAt == null) {
            revokedAt = Instant.now();
        }
    }

    public void replaceWith(RefreshToken successor) {
        revoke();
        this.replacedBy = successor.getId();
    }

    public UUID getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public String getDeviceLabel() {
        return deviceLabel;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public UUID getReplacedBy() {
        return replacedBy;
    }
}
