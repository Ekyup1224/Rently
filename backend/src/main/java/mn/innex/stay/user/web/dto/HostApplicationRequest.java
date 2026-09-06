package mn.innex.stay.user.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request to become a host. Submitting grants nothing: a SUPER_ADMIN reviews it
 * in the Step 4 approval queue, and approval is what creates the role grant.
 *
 * @param requestedRole            HOUSE_OWNER or HOTEL_MANAGER
 * @param organizationName         required for HOTEL_MANAGER
 * @param organizationRegistrationNo state business-registration number, for HOTEL_MANAGER
 */
public record HostApplicationRequest(
        @NotNull @Pattern(regexp = "HOUSE_OWNER|HOTEL_MANAGER",
                message = "requestedRole must be HOUSE_OWNER or HOTEL_MANAGER") String requestedRole,
        @Size(max = 200) String organizationName,
        @Size(max = 64) String organizationRegistrationNo,
        @Size(max = 2000) String note) {
}
