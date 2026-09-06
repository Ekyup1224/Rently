package mn.innex.stay.common.audit;

import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes audit rows. Every write runs in its own transaction so that a failure
 * to audit can never roll back the business change it was recording, and vice
 * versa: an audit row is kept even if the caller's transaction later fails.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository repository;

    public AuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(UUID actorId, String action, String targetType, String targetId,
                       Map<String, Object> metadata, String ip) {
        try {
            repository.save(new AuditLog(actorId, action, targetType, targetId, metadata, ip));
        } catch (RuntimeException ex) {
            // Losing an audit row must not fail the user's request; it does need investigating.
            log.error("Failed to write audit row action={} target={}:{}", action, targetType, targetId, ex);
        }
    }

    public void record(UUID actorId, String action, String targetType, UUID targetId,
                       Map<String, Object> metadata, String ip) {
        record(actorId, action, targetType, targetId == null ? null : targetId.toString(), metadata, ip);
    }
}
