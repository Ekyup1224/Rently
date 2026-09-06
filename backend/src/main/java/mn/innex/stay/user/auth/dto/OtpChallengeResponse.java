package mn.innex.stay.user.auth.dto;

/**
 * Result of requesting a code. Deliberately says nothing about whether the number
 * already had an account, so the endpoint cannot be used to enumerate users.
 *
 * @param resendAfterSeconds how long the client should disable its resend button
 */
public record OtpChallengeResponse(String message, long resendAfterSeconds) {
}
