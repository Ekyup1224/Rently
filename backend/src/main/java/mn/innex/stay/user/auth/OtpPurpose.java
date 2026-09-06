package mn.innex.stay.user.auth;

/**
 * What a one-time code is good for. Codes are namespaced by purpose so a code
 * texted for a password reset cannot be replayed to log in.
 */
public enum OtpPurpose {

    /** Register-or-sign-in: the phone-first primary auth path. */
    LOGIN,

    /** Verifying a phone number added or changed on an existing account. */
    PHONE_VERIFY,

    PASSWORD_RESET
}
