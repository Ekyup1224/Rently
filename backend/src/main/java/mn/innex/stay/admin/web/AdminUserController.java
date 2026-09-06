package mn.innex.stay.admin.web;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import mn.innex.stay.admin.web.dto.ChangeKycStatusRequest;
import mn.innex.stay.admin.web.dto.ChangeStatusRequest;
import mn.innex.stay.admin.web.dto.GrantRoleRequest;
import mn.innex.stay.common.web.ClientIp;
import mn.innex.stay.common.web.PageResponse;
import mn.innex.stay.security.CurrentActor;
import mn.innex.stay.user.domain.KycStatus;
import mn.innex.stay.user.domain.Role;
import mn.innex.stay.user.domain.UserStatus;
import mn.innex.stay.user.service.UserAdminService;
import mn.innex.stay.user.web.dto.UserResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Super Admin user administration. The URL rule in {@code SecurityConfig} already
 * restricts {@code /api/v1/admin/**}; the class-level {@code @PreAuthorize} is a
 * second, independent check so a future change to the URL rules cannot silently
 * open these up.
 */
@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminUserController {

    private final UserAdminService userAdminService;

    public AdminUserController(UserAdminService userAdminService) {
        this.userAdminService = userAdminService;
    }

    /**
     * Paged user list for the admin grid.
     *
     * @param q case-insensitive fragment matched against phone, email and full name
     */
    @GetMapping
    public PageResponse<UserResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) KycStatus kycStatus,
            @RequestParam(required = false) Role role,
            @PageableDefault(size = 25, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return PageResponse.of(userAdminService.search(q, status, kycStatus, role, pageable));
    }

    @GetMapping("/{userId}")
    public UserResponse get(@PathVariable UUID userId) {
        return UserResponse.from(userAdminService.require(userId));
    }

    /** Suspending or deleting also revokes the target's sessions. */
    @PatchMapping("/{userId}/status")
    public UserResponse changeStatus(@PathVariable UUID userId,
                                     @Valid @RequestBody ChangeStatusRequest request,
                                     HttpServletRequest httpRequest) {
        return UserResponse.from(userAdminService.changeStatus(CurrentActor.requireUserId(), userId,
                request.status(), request.reason(), ClientIp.of(httpRequest)));
    }

    @PatchMapping("/{userId}/kyc-status")
    public UserResponse changeKycStatus(@PathVariable UUID userId,
                                        @Valid @RequestBody ChangeKycStatusRequest request,
                                        HttpServletRequest httpRequest) {
        return UserResponse.from(userAdminService.setKycStatus(CurrentActor.requireUserId(), userId,
                request.kycStatus(), ClientIp.of(httpRequest)));
    }

    @PostMapping("/{userId}/roles")
    public UserResponse grantRole(@PathVariable UUID userId,
                                  @Valid @RequestBody GrantRoleRequest request,
                                  HttpServletRequest httpRequest) {
        return UserResponse.from(userAdminService.grantRole(CurrentActor.requireUserId(), userId,
                request.role(), request.organizationId(), ClientIp.of(httpRequest)));
    }

    /**
     * Revokes a role. The organization goes in the query string rather than a body
     * because DELETE requests with bodies are unreliable across proxies.
     */
    @DeleteMapping("/{userId}/roles/{role}")
    public UserResponse revokeRole(@PathVariable UUID userId,
                                   @PathVariable Role role,
                                   @RequestParam(required = false) UUID organizationId,
                                   HttpServletRequest httpRequest) {
        return UserResponse.from(userAdminService.revokeRole(CurrentActor.requireUserId(), userId,
                role, organizationId, ClientIp.of(httpRequest)));
    }
}
