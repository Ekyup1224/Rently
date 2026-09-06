package mn.innex.stay.user.service;

import java.util.Map;
import java.util.UUID;

import mn.innex.stay.common.ApiException;
import mn.innex.stay.common.audit.AuditAction;
import mn.innex.stay.common.audit.AuditService;
import mn.innex.stay.user.auth.RefreshTokenService;
import mn.innex.stay.user.domain.KycStatus;
import mn.innex.stay.user.domain.Organization;
import mn.innex.stay.user.domain.Role;
import mn.innex.stay.user.domain.User;
import mn.innex.stay.user.domain.UserStatus;
import mn.innex.stay.user.repo.OrganizationRepository;
import mn.innex.stay.user.repo.UserRepository;
import mn.innex.stay.user.repo.UserSpecifications;
import mn.innex.stay.user.web.dto.UserResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Operations the Super Admin console performs on other people's accounts. This is
 * the user module's published interface to {@code mn.innex.stay.admin} — the admin
 * module never touches user entities or repositories directly.
 */
@Service
public class UserAdminService {

    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final RefreshTokenService refreshTokenService;
    private final AuditService auditService;

    public UserAdminService(UserRepository userRepository,
                            OrganizationRepository organizationRepository,
                            RefreshTokenService refreshTokenService,
                            AuditService auditService) {
        this.userRepository = userRepository;
        this.organizationRepository = organizationRepository;
        this.refreshTokenService = refreshTokenService;
        this.auditService = auditService;
    }

    /**
     * Paged user search for the admin grid.
     *
     * <p>Returns DTOs rather than entities on purpose: the page query does not join
     * role grants, so mapping has to happen while the persistence session is still
     * open. Handing entities to the controller would throw on the first lazy read.
     */
    @Transactional(readOnly = true)
    public Page<UserResponse> search(String term, UserStatus status, KycStatus kycStatus, Role role,
                                     Pageable pageable) {
        return userRepository.findAll(UserSpecifications.allOf(
                UserSpecifications.matches(term),
                UserSpecifications.statusIs(status),
                UserSpecifications.kycStatusIs(kycStatus),
                UserSpecifications.hasRole(role)), pageable)
                .map(UserResponse::from);
    }

    @Transactional(readOnly = true)
    public User require(UUID userId) {
        return userRepository.findByIdWithRoles(userId)
                .orElseThrow(() -> ApiException.notFound("user_not_found", "User not found"));
    }

    /**
     * Changes an account's status. Suspending or deleting also revokes every
     * session, so the block takes effect within one access-token lifetime at worst.
     */
    @Transactional
    public User changeStatus(UUID actorId, UUID targetUserId, UserStatus status, String reason, String ip) {
        User user = require(targetUserId);

        if (actorId.equals(targetUserId) && status != UserStatus.ACTIVE) {
            throw ApiException.badRequest("cannot_modify_self",
                    "An admin cannot suspend or delete their own account");
        }

        UserStatus previous = user.getStatus();
        user.setStatus(status);
        userRepository.save(user);

        int revokedSessions = 0;
        if (status == UserStatus.SUSPENDED || status == UserStatus.DELETED) {
            revokedSessions = refreshTokenService.revokeAllForUser(targetUserId);
        }

        auditService.record(actorId, AuditAction.USER_STATUS_CHANGED, "User", targetUserId,
                Map.of("from", previous.name(), "to", status.name(),
                        "reason", reason == null ? "" : reason,
                        "revokedSessions", revokedSessions), ip);
        return user;
    }

    @Transactional
    public User setKycStatus(UUID actorId, UUID targetUserId, KycStatus kycStatus, String ip) {
        User user = require(targetUserId);
        KycStatus previous = user.getKycStatus();
        user.setKycStatus(kycStatus);
        userRepository.save(user);
        auditService.record(actorId, "USER_KYC_STATUS_CHANGED", "User", targetUserId,
                Map.of("from", previous.name(), "to", kycStatus.name()), ip);
        return user;
    }

    /**
     * Grants a role. Hotel-side roles need the organization they apply to; the
     * others must not carry one.
     */
    @Transactional
    public User grantRole(UUID actorId, UUID targetUserId, Role role, UUID organizationId, String ip) {
        User user = require(targetUserId);
        Organization organization = resolveOrganization(role, organizationId);

        boolean granted = user.grantRole(role, organization, actorId);
        if (!granted) {
            throw ApiException.conflict("role_already_held", "The user already holds this role");
        }
        userRepository.save(user);

        auditService.record(actorId, AuditAction.USER_ROLE_GRANTED, "User", targetUserId,
                Map.of("role", role.name(),
                        "organizationId", organizationId == null ? "" : organizationId.toString()), ip);
        return user;
    }

    @Transactional
    public User revokeRole(UUID actorId, UUID targetUserId, Role role, UUID organizationId, String ip) {
        User user = require(targetUserId);

        if (role == Role.SUPER_ADMIN && actorId.equals(targetUserId)) {
            throw ApiException.badRequest("cannot_modify_self",
                    "An admin cannot revoke their own SUPER_ADMIN role");
        }
        if (role == Role.CLIENT) {
            throw ApiException.badRequest("role_not_revocable",
                    "The CLIENT role is held by every account and cannot be revoked");
        }

        Organization organization = role.isOrganizationScoped()
                ? resolveOrganization(role, organizationId)
                : null;
        boolean revoked = user.revokeRole(role, organization);
        if (!revoked) {
            throw ApiException.notFound("role_not_held", "The user does not hold this role");
        }
        userRepository.save(user);

        // A revoked host loses access to portal screens their old token still claims,
        // so end their sessions and make them re-authenticate.
        int revokedSessions = refreshTokenService.revokeAllForUser(targetUserId);

        auditService.record(actorId, AuditAction.USER_ROLE_REVOKED, "User", targetUserId,
                Map.of("role", role.name(),
                        "organizationId", organizationId == null ? "" : organizationId.toString(),
                        "revokedSessions", revokedSessions), ip);
        return user;
    }

    private Organization resolveOrganization(Role role, UUID organizationId) {
        if (!role.isOrganizationScoped()) {
            if (organizationId != null) {
                throw ApiException.badRequest("organization_not_applicable",
                        "Role " + role + " is platform-wide and takes no organization");
            }
            return null;
        }
        if (organizationId == null) {
            throw ApiException.badRequest("organization_required",
                    "Role " + role + " must be scoped to an organization");
        }
        return organizationRepository.findById(organizationId)
                .orElseThrow(() -> ApiException.notFound("organization_not_found",
                        "Organization not found"));
    }
}
