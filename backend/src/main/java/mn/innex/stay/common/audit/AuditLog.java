package mn.innex.stay.common.audit;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

/**
 * Append-only record of who did what. Written by services on every state change
 * that an operator or auditor might later have to explain; never updated or deleted.
 */
@Entity
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(nullable = false, length = 64)
    private String action;

    @Column(name = "target_type", length = 64)
    private String targetType;

    @Column(name = "target_id", length = 64)
    private String targetId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> metadata = Map.of();

    @Column(length = 64)
    private String ip;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected AuditLog() {
    }

    public AuditLog(UUID actorId, String action, String targetType, String targetId,
                    Map<String, Object> metadata, String ip) {
        this.actorId = actorId;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.metadata = metadata == null ? Map.of() : metadata;
        this.ip = ip;
    }

    public UUID getId() {
        return id;
    }

    public UUID getActorId() {
        return actorId;
    }

    public String getAction() {
        return action;
    }

    public String getTargetType() {
        return targetType;
    }

    public String getTargetId() {
        return targetId;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public String getIp() {
        return ip;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
