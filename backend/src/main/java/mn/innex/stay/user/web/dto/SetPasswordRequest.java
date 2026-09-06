package mn.innex.stay.user.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Adds or changes a password on the signed-in account.
 *
 * @param currentPassword required only when the account already has one; an
 *                        OTP-only account sets its first password without it
 */
public record SetPasswordRequest(
        @Size(max = 72) String currentPassword,
        @NotBlank @Size(min = 8, max = 72) String newPassword) {
}
