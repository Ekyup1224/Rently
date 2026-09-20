package mn.innex.stay.admin.web;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import mn.innex.stay.common.web.ClientIp;
import mn.innex.stay.common.web.PageResponse;
import mn.innex.stay.security.CurrentActor;
import mn.innex.stay.user.domain.HostApplicationStatus;
import mn.innex.stay.user.service.HostApplicationAdminService;
import mn.innex.stay.user.web.dto.HostApplicationResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Deciding host applications.
 *
 * <p>Approving a hotel application is what creates the business the hotel will
 * belong to, so this is the door every hotel comes through. The fuller review
 * console — with identity documents and a worked queue — is Step 4.
 */
@RestController
@RequestMapping("/api/v1/admin/host-applications")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminHostApplicationController {

    private final HostApplicationAdminService applicationService;

    public AdminHostApplicationController(HostApplicationAdminService applicationService) {
        this.applicationService = applicationService;
    }

    @GetMapping
    public PageResponse<HostApplicationResponse> list(
            @RequestParam(required = false) HostApplicationStatus status,
            @PageableDefault(size = 25, sort = "createdAt", direction = Sort.Direction.ASC)
            Pageable pageable) {
        return PageResponse.of(applicationService.list(status, pageable),
                HostApplicationResponse::from);
    }

    /**
     * Grants the requested role. For a hotel application this also registers the
     * business, reusing an existing one when the registration number matches.
     */
    @PostMapping("/{applicationId}/approve")
    public HostApplicationResponse approve(@PathVariable UUID applicationId,
                                           @Valid @RequestBody(required = false) Decision decision,
                                           HttpServletRequest httpRequest) {
        return HostApplicationResponse.from(applicationService.approve(
                CurrentActor.requireUserId(), applicationId,
                decision == null ? null : decision.note(), ClientIp.of(httpRequest)));
    }

    /** Turns an application down. The reason is shown to the applicant. */
    @PostMapping("/{applicationId}/reject")
    public HostApplicationResponse reject(@PathVariable UUID applicationId,
                                          @Valid @RequestBody Decision decision,
                                          HttpServletRequest httpRequest) {
        return HostApplicationResponse.from(applicationService.reject(
                CurrentActor.requireUserId(), applicationId, decision.note(),
                ClientIp.of(httpRequest)));
    }

    /** @param note shown to the applicant; required when rejecting */
    public record Decision(@Size(max = 2000) String note) {
    }
}
