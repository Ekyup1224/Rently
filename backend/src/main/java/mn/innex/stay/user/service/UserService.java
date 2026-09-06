package mn.innex.stay.user.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import mn.innex.stay.common.ApiException;
import mn.innex.stay.common.Emails;
import mn.innex.stay.common.audit.AuditAction;
import mn.innex.stay.common.audit.AuditService;
import mn.innex.stay.user.domain.HostApplication;
import mn.innex.stay.user.domain.HostApplicationStatus;
import mn.innex.stay.user.domain.Role;
import mn.innex.stay.user.domain.User;
import mn.innex.stay.user.repo.HostApplicationRepository;
import mn.innex.stay.user.repo.UserRepository;
import mn.innex.stay.user.web.dto.HostApplicationRequest;
import mn.innex.stay.user.web.dto.SetPasswordRequest;
import mn.innex.stay.user.web.dto.UpdateProfileRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Self-service operations on the signed-in account. Every method takes the actor
 * id from the security context, never from the request, so one user can never
 * modify another through these paths.
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final HostApplicationRepository hostApplicationRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public UserService(UserRepository userRepository,
                       HostApplicationRepository hostApplicationRepository,
                       PasswordEncoder passwordEncoder,
                       AuditService auditService) {
        this.userRepository = userRepository;
        this.hostApplicationRepository = hostApplicationRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public User require(UUID userId) {
        return userRepository.findByIdWithRoles(userId)
                .orElseThrow(() -> ApiException.notFound("user_not_found", "User not found"));
    }

    /** Applies only the non-null fields of the request. */
    @Transactional
    public User updateProfile(UUID userId, UpdateProfileRequest request, String ip) {
        User user = require(userId);

        if (request.fullName() != null) {
            user.setFullName(request.fullName().isBlank() ? null : request.fullName().trim());
        }
        if (request.email() != null) {
            String email = Emails.normalize(request.email());
            boolean changed = !user.getEmail().map(email::equals).orElse(false);
            if (changed && email != null && userRepository.existsByEmail(email)) {
                throw ApiException.conflict("email_taken", "This email is already in use.");
            }
            if (changed) {
                // Clears email_verified_at: a new address has not been confirmed yet.
                user.setEmail(email);
            }
        }
        if (request.locale() != null) {
            user.setLocale(request.locale());
        }

        userRepository.save(user);
        auditService.record(userId, AuditAction.USER_PROFILE_UPDATED, "User", userId, Map.of(), ip);
        return user;
    }

    /**
     * Sets the account password. An account that already has one must prove it;
     * an OTP-only account is setting its first password and cannot.
     */
    @Transactional
    public void setPassword(UUID userId, SetPasswordRequest request, String ip) {
        User user = require(userId);

        if (user.hasPassword()) {
            if (request.currentPassword() == null
                    || !passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
                throw ApiException.badRequest("current_password_invalid",
                        "Current password is incorrect");
            }
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        auditService.record(userId, AuditAction.USER_PASSWORD_SET, "User", userId, Map.of(), ip);
    }

    /**
     * Files a request to become a host. Grants nothing on its own — a SUPER_ADMIN
     * decides in the Step 4 approval queue.
     */
    @Transactional
    public HostApplication submitHostApplication(UUID userId, HostApplicationRequest request, String ip) {
        User user = require(userId);
        Role requestedRole = Role.valueOf(request.requestedRole());

        if (user.hasRole(requestedRole)) {
            throw ApiException.conflict("role_already_held", "You already hold this role");
        }
        if (requestedRole == Role.HOTEL_MANAGER
                && (request.organizationName() == null || request.organizationName().isBlank())) {
            throw ApiException.badRequest("organization_name_required",
                    "A hotel application needs the business name");
        }
        hostApplicationRepository
                .findByUserIdAndRequestedRoleAndStatus(userId, requestedRole, HostApplicationStatus.PENDING)
                .ifPresent(existing -> {
                    throw ApiException.conflict("application_pending",
                            "An application for this role is already under review");
                });

        HostApplication application = hostApplicationRepository.save(new HostApplication(
                user, requestedRole, request.organizationName(),
                request.organizationRegistrationNo(), request.note()));

        auditService.record(userId, AuditAction.HOST_APPLICATION_SUBMITTED, "HostApplication",
                application.getId(), Map.of("requestedRole", requestedRole.name()), ip);
        return application;
    }

    @Transactional(readOnly = true)
    public List<HostApplication> listHostApplications(UUID userId) {
        return hostApplicationRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional
    public HostApplication withdrawHostApplication(UUID userId, UUID applicationId, String ip) {
        HostApplication application = hostApplicationRepository.findById(applicationId)
                .orElseThrow(() -> ApiException.notFound("application_not_found", "Application not found"));

        // Ownership check: an id from the URL never grants access to someone else's row.
        if (!application.getUser().getId().equals(userId)) {
            throw ApiException.notFound("application_not_found", "Application not found");
        }
        if (application.getStatus() != HostApplicationStatus.PENDING) {
            throw ApiException.conflict("application_not_pending",
                    "Only a pending application can be withdrawn");
        }

        application.withdraw();
        hostApplicationRepository.save(application);
        auditService.record(userId, AuditAction.HOST_APPLICATION_WITHDRAWN, "HostApplication",
                applicationId, Map.of(), ip);
        return application;
    }
}
