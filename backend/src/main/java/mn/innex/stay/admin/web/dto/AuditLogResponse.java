package mn.innex.stay.admin.web.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import mn.innex.stay.common.audit.AuditLog;

public record AuditLogResponse(
        UUID id,
        UUID actorId,
        String action,
        String targetType,
        String targetId,
        Map<String, Object> metadata,
        String ip,
        Instant createdAt) {

    public static AuditLogResponse from(AuditLog log) {
        return new AuditLogResponse(log.getId(), log.getActorId(), log.getAction(),
                log.getTargetType(), log.getTargetId(), log.getMetadata(), log.getIp(),
                log.getCreatedAt());
    }
}
