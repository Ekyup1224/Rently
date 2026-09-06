package mn.innex.stay.common.audit;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

/** Composable filters for the admin audit-log reader. Each is a no-op when its argument is null. */
public final class AuditLogSpecifications {

    private AuditLogSpecifications() {
    }

    public static Specification<AuditLog> actorIs(UUID actorId) {
        return actorId == null ? null : (root, query, cb) -> cb.equal(root.get("actorId"), actorId);
    }

    public static Specification<AuditLog> actionIs(String action) {
        return action == null || action.isBlank()
                ? null
                : (root, query, cb) -> cb.equal(root.get("action"), action);
    }

    public static Specification<AuditLog> targetTypeIs(String targetType) {
        return targetType == null || targetType.isBlank()
                ? null
                : (root, query, cb) -> cb.equal(root.get("targetType"), targetType);
    }

    public static Specification<AuditLog> targetIdIs(String targetId) {
        return targetId == null || targetId.isBlank()
                ? null
                : (root, query, cb) -> cb.equal(root.get("targetId"), targetId);
    }

    public static Specification<AuditLog> createdFrom(Instant from) {
        return from == null
                ? null
                : (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), from);
    }

    public static Specification<AuditLog> createdBefore(Instant to) {
        return to == null ? null : (root, query, cb) -> cb.lessThan(root.get("createdAt"), to);
    }

    /** Combines the non-null specs with AND; returns an unrestricted spec when all are null. */
    @SafeVarargs
    public static Specification<AuditLog> allOf(Specification<AuditLog>... specs) {
        Specification<AuditLog> combined = (root, query, cb) -> cb.conjunction();
        for (Specification<AuditLog> spec : specs) {
            if (spec != null) {
                combined = combined.and(spec);
            }
        }
        return combined;
    }
}
