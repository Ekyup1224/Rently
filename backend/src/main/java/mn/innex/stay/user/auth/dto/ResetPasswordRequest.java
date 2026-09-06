package mn.innex.stay.user.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Completes a password reset with the code sent to the account's phone. */
public record ResetPasswordRequest(
        @NotBlank @Size(max = 32) String phone,
        @NotBlank @Size(min = 4, max = 8) String code,
        @NotBlank @Size(min = 8, max = 72) String newPassword) {
}
