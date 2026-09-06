package mn.innex.stay.admin.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import mn.innex.stay.user.domain.UserStatus;

/**
 * @param reason recorded in the audit row; required for anything but reactivation
 */
public record ChangeStatusRequest(
        @NotNull UserStatus status,
        @Size(max = 500) String reason) {
}
