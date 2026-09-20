package mn.innex.stay.user.web.dto;

import java.time.Instant;
import java.util.UUID;

import mn.innex.stay.user.domain.HostApplication;
import mn.innex.stay.user.domain.HostApplicationStatus;

public record HostApplicationResponse(
        UUID id,
        UUID applicantId,
        String applicantName,
        String applicantPhone,
        String applicantKycStatus,
        String requestedRole,
        String organizationName,
        String organizationRegistrationNo,
        String note,
        HostApplicationStatus status,
        String decisionNote,
        Instant decidedAt,
        Instant createdAt) {

    /**
     * Names the applicant.
     *
     * <p>Approving grants a role to a person, so a reviewer has to be able to see
     * which person: the queue used to show an organization and a date and nothing
     * else, which is not enough to decide on.
     */
    public static HostApplicationResponse from(HostApplication application) {
        var applicant = application.getUser();
        return new HostApplicationResponse(
                application.getId(),
                applicant.getId(),
                applicant.getFullName(),
                applicant.getPhone(),
                applicant.getKycStatus().name(),
                application.getRequestedRole().name(),
                application.getOrganizationName(),
                application.getOrganizationRegistrationNo(),
                application.getNote(),
                application.getStatus(),
                application.getDecisionNote(),
                application.getDecidedAt(),
                application.getCreatedAt());
    }
}
