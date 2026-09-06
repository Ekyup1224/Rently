package mn.innex.stay.user.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Exchange a one-time code for a session.
 *
 * @param deviceLabel free-text device name shown in the user's session list
 */
public record OtpVerifyRequest(
        @NotBlank @Size(max = 32) String phone,
        @NotBlank @Size(min = 4, max = 8) String code,
        @Size(max = 120) String deviceLabel) {
}
