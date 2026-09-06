package mn.innex.stay.user.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Email + password registration, the secondary auth path. A phone number is still
 * required — it is the account's primary identifier — and the account stays
 * PENDING_VERIFICATION until the code sent to that number is verified.
 */
public record RegisterRequest(
        @NotBlank @Size(max = 32) String phone,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        @Size(max = 150) String fullName,
        @Size(max = 8) String locale) {
}
