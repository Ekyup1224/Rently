package mn.innex.stay.user.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import mn.innex.stay.user.domain.KycStatus;
import mn.innex.stay.user.domain.User;
import mn.innex.stay.user.domain.UserStatus;

/**
 * The account as its owner (or an admin) sees it. Contains no password material
 * and no other user's data, so it is safe to return from every authenticated
 * endpoint that deals with a single account.
 */
public record UserResponse(
        UUID id,
        String phone,
        String email,
        String fullName,
        UserStatus status,
        KycStatus kycStatus,
        String locale,
        boolean phoneVerified,
        boolean emailVerified,
        boolean hasPassword,
        List<RoleGrant> roles,
        Instant lastLoginAt,
        Instant createdAt) {

    /**
     * One role the account holds. {@code organizationId} is set only for
     * hotel-side roles, which are always scoped to a business.
     */
    public record RoleGrant(String role, UUID organizationId, String organizationName) {
    }

    public static UserResponse from(User user) {
        List<RoleGrant> roles = user.getRoles().stream()
                .map(grant -> new RoleGrant(
                        grant.getRole().name(),
                        grant.getOrganization() == null ? null : grant.getOrganization().getId(),
                        grant.getOrganization() == null ? null : grant.getOrganization().getName()))
                .toList();
        return new UserResponse(
                user.getId(),
                user.getPhone(),
                user.getEmail().orElse(null),
                user.getFullName(),
                user.getStatus(),
                user.getKycStatus(),
                user.getLocale(),
                user.getPhoneVerifiedAt() != null,
                user.getEmailVerifiedAt() != null,
                user.hasPassword(),
                roles,
                user.getLastLoginAt(),
                user.getCreatedAt());
    }
}
