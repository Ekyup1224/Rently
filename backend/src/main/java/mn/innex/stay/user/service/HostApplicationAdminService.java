package mn.innex.stay.user.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import mn.innex.stay.common.ApiException;
import mn.innex.stay.common.audit.AuditAction;
import mn.innex.stay.common.audit.AuditService;
import mn.innex.stay.user.domain.HostApplication;
import mn.innex.stay.user.domain.HostApplicationStatus;
import mn.innex.stay.user.domain.Organization;
import mn.innex.stay.user.domain.OrganizationStatus;
import mn.innex.stay.user.domain.OrganizationType;
import mn.innex.stay.user.domain.Role;
import mn.innex.stay.user.domain.User;
import mn.innex.stay.user.repo.HostApplicationRepository;
import mn.innex.stay.user.repo.OrganizationRepository;
import mn.innex.stay.user.repo.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deciding who becomes a host.
 *
 * <p>Approval is what actually grants anything: submitting an application in Step
 * 1 recorded an intention and nothing more. For a hotel this is also the only
 * place an {@link Organization} is created — a hotel belongs to a business, and
 * the business comes into existence when the platform accepts it.
 *
 * <p>Kept minimal on purpose. The full review console, with document checks and a
 * worked queue, is Step 4; this exists so hotels can be onboarded at all.
 */
@Service
public class HostApplicationAdminService {

    private static final Logger log = LoggerFactory.getLogger(HostApplicationAdminService.class);

    private final HostApplicationRepository applicationRepository;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public HostApplicationAdminService(HostApplicationRepository applicationRepository,
                                       OrganizationRepository organizationRepository,
                                       UserRepository userRepository, AuditService auditService) {
        this.applicationRepository = applicationRepository;
        this.organizationRepository = organizationRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public Page<HostApplication> list(HostApplicationStatus status, Pageable pageable) {
        return status == null
                ? applicationRepository.findAll(pageable)
                : applicationRepository.findByStatus(status, pageable);
    }

    /**
     * Approves an application, granting the role it asked for.
     *
     * <p>A hotel application also creates the organization the grant is scoped to,
     * reusing an existing business when the registration number already matches
     * one — a second hotel from the same company should not become a second
     * company.
     */
    @Transactional
    public HostApplication approve(UUID actorId, UUID applicationId, String note, String ip) {
        HostApplication application = require(applicationId);
        if (application.getStatus() != HostApplicationStatus.PENDING) {
            throw ApiException.conflict("application_not_pending",
                    "This application has already been decided");
        }

        User applicant = userRepository.findByIdWithRoles(application.getUser().getId())
                .orElseThrow(() -> ApiException.notFound("user_not_found", "Applicant not found"));

        Organization organization = null;
        if (application.getRequestedRole() == Role.HOTEL_MANAGER) {
            organization = resolveOrCreateOrganization(actorId, application, applicant, ip);
        }

        boolean granted = applicant.grantRole(application.getRequestedRole(), organization, actorId);
        if (granted) {
            userRepository.save(applicant);
        }

        application.decide(HostApplicationStatus.APPROVED, actorId, note);
        applicationRepository.save(application);

        auditService.record(actorId, AuditAction.HOST_APPLICATION_DECIDED, "HostApplication",
                applicationId, Map.of(
                        "decision", "APPROVED",
                        "role", application.getRequestedRole().name(),
                        "userId", applicant.getId().toString(),
                        "organizationId", organization == null ? "" : organization.getId().toString(),
                        "roleGranted", granted), ip);
        log.info("Approved {} application for user {}", application.getRequestedRole(),
                applicant.getId());
        return application;
    }

    /** Rejects an application. A reason is required: the applicant has to know why. */
    @Transactional
    public HostApplication reject(UUID actorId, UUID applicationId, String note, String ip) {
        if (note == null || note.isBlank()) {
            throw ApiException.badRequest("reason_required",
                    "A reason is required so the applicant knows what to fix");
        }
        HostApplication application = require(applicationId);
        if (application.getStatus() != HostApplicationStatus.PENDING) {
            throw ApiException.conflict("application_not_pending",
                    "This application has already been decided");
        }

        application.decide(HostApplicationStatus.REJECTED, actorId, note);
        applicationRepository.save(application);

        auditService.record(actorId, AuditAction.HOST_APPLICATION_DECIDED, "HostApplication",
                applicationId, Map.of("decision", "REJECTED",
                        "role", application.getRequestedRole().name(),
                        "reason", note), ip);
        return application;
    }

    /** Applications one account has filed, for the admin's view of that user. */
    @Transactional(readOnly = true)
    public List<HostApplication> forUser(UUID userId) {
        return applicationRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    private Organization resolveOrCreateOrganization(UUID actorId, HostApplication application,
                                                     User applicant, String ip) {
        String registrationNo = blankToNull(application.getOrganizationRegistrationNo());

        if (registrationNo != null) {
            var existing = organizationRepository.findByRegistrationNo(registrationNo);
            if (existing.isPresent()) {
                // Same registered company: reuse it rather than creating a duplicate.
                return existing.get();
            }
        }

        String name = blankToNull(application.getOrganizationName());
        if (name == null) {
            throw ApiException.badRequest("organization_name_missing",
                    "This application has no business name to register");
        }

        Organization organization = new Organization(name, OrganizationType.HOTEL_BUSINESS,
                registrationNo, applicant);
        // Approving the application is the acceptance; there is no second gate.
        organization.setStatus(OrganizationStatus.ACTIVE);
        organizationRepository.save(organization);

        auditService.record(actorId, AuditAction.ORGANIZATION_CREATED, "Organization",
                organization.getId(), Map.of("name", name,
                        "registrationNo", registrationNo == null ? "" : registrationNo,
                        "ownerUserId", applicant.getId().toString()), ip);
        return organization;
    }

    private HostApplication require(UUID applicationId) {
        return applicationRepository.findById(applicationId).orElseThrow(
                () -> ApiException.notFound("application_not_found", "Application not found"));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
