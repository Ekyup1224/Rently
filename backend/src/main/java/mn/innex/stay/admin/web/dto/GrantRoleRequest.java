package mn.innex.stay.admin.web.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import mn.innex.stay.user.domain.Role;

/**
 * @param organizationId required for HOTEL_MANAGER and HOTEL_STAFF, forbidden otherwise
 */
public record GrantRoleRequest(@NotNull Role role, UUID organizationId) {
}
