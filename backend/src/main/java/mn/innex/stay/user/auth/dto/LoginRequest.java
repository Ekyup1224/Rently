package mn.innex.stay.user.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Email + password sign-in.
 *
 * @param email    the account's email; phone-only accounts cannot use this path
 * @param password plaintext, compared against the BCrypt hash
 */
public record LoginRequest(
        @NotBlank @Size(max = 255) String email,
        @NotBlank @Size(max = 72) String password,
        @Size(max = 120) String deviceLabel) {
}
