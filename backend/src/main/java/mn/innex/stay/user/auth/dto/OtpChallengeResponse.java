package mn.innex.stay.user.auth.dto;

/**
 * Result of requesting a code. Deliberately says nothing about whether the number
 * already had an account, so the endpoint cannot be used to enumerate users.
 *
 * @param resendAfterSeconds how long the client should disable its resend button
 * @param devCode            the code itself, in a development configuration only, so
 *                           signing in locally does not mean reading the server log.
 *                           Null in every other configuration, which this
 *                           application's Jackson settings omit from the JSON
 *                           entirely — a client cannot tell a withheld code from a
 *                           server that never had the feature.
 */
public record OtpChallengeResponse(String message, long resendAfterSeconds, String devCode) {

    public static OtpChallengeResponse sent(long resendAfterSeconds, String devCode) {
        return new OtpChallengeResponse("A verification code has been sent.",
                resendAfterSeconds, devCode);
    }
}
