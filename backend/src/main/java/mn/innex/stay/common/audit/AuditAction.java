package mn.innex.stay.common.audit;

/**
 * Audit action names. Kept as constants rather than an enum so that adding an
 * action in a later module needs no change here, while these stay greppable.
 */
public final class AuditAction {

    public static final String USER_REGISTERED = "USER_REGISTERED";
    public static final String USER_LOGGED_IN = "USER_LOGGED_IN";
    public static final String USER_LOGGED_OUT = "USER_LOGGED_OUT";
    public static final String USER_PHONE_VERIFIED = "USER_PHONE_VERIFIED";
    public static final String USER_PASSWORD_SET = "USER_PASSWORD_SET";
    public static final String USER_PASSWORD_RESET = "USER_PASSWORD_RESET";
    public static final String USER_PROFILE_UPDATED = "USER_PROFILE_UPDATED";
    public static final String USER_STATUS_CHANGED = "USER_STATUS_CHANGED";
    public static final String USER_ROLE_GRANTED = "USER_ROLE_GRANTED";
    public static final String USER_ROLE_REVOKED = "USER_ROLE_REVOKED";
    public static final String TOKEN_REFRESHED = "TOKEN_REFRESHED";
    public static final String TOKEN_REUSE_DETECTED = "TOKEN_REUSE_DETECTED";
    public static final String HOST_APPLICATION_SUBMITTED = "HOST_APPLICATION_SUBMITTED";
    public static final String HOST_APPLICATION_WITHDRAWN = "HOST_APPLICATION_WITHDRAWN";

    private AuditAction() {
    }
}
