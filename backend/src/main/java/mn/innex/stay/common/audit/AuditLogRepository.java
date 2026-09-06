package mn.innex.stay.common.audit;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Audit rows are only ever inserted and read. Filtering goes through
 * {@link AuditLogSpecifications} rather than JPQL with nullable parameters,
 * which Postgres cannot type-infer.
 */
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID>,
        JpaSpecificationExecutor<AuditLog> {
}
