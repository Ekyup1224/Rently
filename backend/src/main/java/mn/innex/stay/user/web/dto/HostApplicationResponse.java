package mn.innex.stay.user.web.dto;

import java.time.Instant;
import java.util.UUID;

import mn.innex.stay.user.domain.HostApplication;
import mn.innex.stay.user.domain.HostApplicationStatus;

public record HostApplicationResponse(
        UUID id,
        String requestedRole,
        String organizationName,
        String organizationRegistrationNo,
        String note,
        HostApplicationStatus status,
        String decisionNote,
        Instant decidedAt,
        Instant createdAt) {

    public static HostApplicationResponse from(HostApplication application) {
        return new HostApplicationResponse(
                application.getId(),
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
