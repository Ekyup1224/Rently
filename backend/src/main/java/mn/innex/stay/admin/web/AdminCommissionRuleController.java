package mn.innex.stay.admin.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import mn.innex.stay.booking.domain.CommissionRule;
import mn.innex.stay.booking.domain.CommissionScope;
import mn.innex.stay.booking.service.CommissionRuleService;
import mn.innex.stay.common.audit.AuditAction;
import mn.innex.stay.common.audit.AuditService;
import mn.innex.stay.common.web.ClientIp;
import mn.innex.stay.security.CurrentActor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The platform's take rate.
 *
 * <p>There is no update or delete: a rate change closes the current rule and opens
 * a new one, so what a past booking was priced under stays recoverable. The
 * listing shows the whole history for that reason.
 */
@RestController
@RequestMapping("/api/v1/admin/commission-rules")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminCommissionRuleController {

    private final CommissionRuleService commissionRuleService;
    private final AuditService auditService;

    public AdminCommissionRuleController(CommissionRuleService commissionRuleService,
                                         AuditService auditService) {
        this.commissionRuleService = commissionRuleService;
        this.auditService = auditService;
    }

    @GetMapping
    public List<CommissionRuleResponse> list() {
        return commissionRuleService.listAll().stream().map(CommissionRuleResponse::from).toList();
    }

    /** Introduces a new rate, superseding the one it replaces at the same instant. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CommissionRuleResponse create(@Valid @RequestBody CommissionRuleRequest request,
                                         HttpServletRequest httpRequest) {
        UUID actorId = CurrentActor.requireUserId();
        CommissionRule rule = commissionRuleService.replace(request.scope(), request.category(),
                request.hostFeePercent(), request.guestFeePercent(), request.note(), actorId);

        auditService.record(actorId, AuditAction.COMMISSION_RULE_CHANGED, "CommissionRule",
                rule.getId(), Map.of("scope", rule.getScope().name(),
                        "category", rule.getCategory() == null ? "" : rule.getCategory(),
                        "hostFeePercent", rule.getHostFeePercent().toPlainString(),
                        "guestFeePercent", rule.getGuestFeePercent().toPlainString()),
                ClientIp.of(httpRequest));
        return CommissionRuleResponse.from(rule);
    }

    /**
     * @param category required for a scoped rule (a property type), forbidden for GLOBAL
     * @param note     why the rate changed; worth having when someone asks in a year
     */
    public record CommissionRuleRequest(
            @NotNull CommissionScope scope,
            @Size(max = 32) String category,
            @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal hostFeePercent,
            @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal guestFeePercent,
            @Size(max = 255) String note) {
    }

    /** @param effectiveTo null means this rule is the one currently in force */
    public record CommissionRuleResponse(
            UUID id,
            CommissionScope scope,
            String category,
            BigDecimal hostFeePercent,
            BigDecimal guestFeePercent,
            Instant effectiveFrom,
            Instant effectiveTo,
            String note,
            Instant createdAt) {

        static CommissionRuleResponse from(CommissionRule rule) {
            return new CommissionRuleResponse(rule.getId(), rule.getScope(), rule.getCategory(),
                    rule.getHostFeePercent(), rule.getGuestFeePercent(), rule.getEffectiveFrom(),
                    rule.getEffectiveTo(), rule.getNote(), rule.getCreatedAt());
        }
    }
}
