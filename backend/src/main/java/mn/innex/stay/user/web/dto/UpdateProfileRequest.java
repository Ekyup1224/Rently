package mn.innex.stay.user.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Partial profile update: null fields are left untouched.
 *
 * <p>Phone is not editable here — changing the account's primary identifier needs
 * its own OTP-verified flow, which arrives with the KYC work.
 */
public record UpdateProfileRequest(
        @Size(max = 150) String fullName,
        @Email @Size(max = 255) String email,
        @Pattern(regexp = "mn|en", message = "locale must be mn or en") String locale) {
}
