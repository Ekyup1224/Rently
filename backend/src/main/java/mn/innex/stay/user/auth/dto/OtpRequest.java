package mn.innex.stay.user.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Ask for a one-time code. The phone may be typed in any local format; it is
 * normalized to E.164 server-side.
 *
 * @param locale preferred language for the SMS and for a newly created account
 */
public record OtpRequest(
        @NotBlank @Size(max = 32) String phone,
        @Size(max = 8) String locale) {
}
