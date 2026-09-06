package mn.innex.stay.admin.web;

import java.time.Instant;
import java.util.UUID;

import mn.innex.stay.admin.web.dto.AuditLogResponse;
import mn.innex.stay.common.audit.AuditLogRepository;
import mn.innex.stay.common.audit.AuditLogSpecifications;
import mn.innex.stay.common.web.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only audit trail viewer. Audit rows are never editable, by anyone. */
@RestController
@RequestMapping("/api/v1/admin/audit-logs")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminAuditLogController {

    private final AuditLogRepository auditLogRepository;

    public AdminAuditLogController(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * @param from inclusive lower bound on {@code createdAt}
     * @param to   exclusive upper bound on {@code createdAt}
     */
    @GetMapping
    public PageResponse<AuditLogResponse> list(
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String targetId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return PageResponse.of(auditLogRepository.findAll(AuditLogSpecifications.allOf(
                AuditLogSpecifications.actorIs(actorId),
                AuditLogSpecifications.actionIs(action),
                AuditLogSpecifications.targetTypeIs(targetType),
                AuditLogSpecifications.targetIdIs(targetId),
                AuditLogSpecifications.createdFrom(from),
                AuditLogSpecifications.createdBefore(to)), pageable), AuditLogResponse::from);
    }
}
