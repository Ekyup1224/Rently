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

    public static final String PROPERTY_CREATED = "PROPERTY_CREATED";
    public static final String PROPERTY_UPDATED = "PROPERTY_UPDATED";
    public static final String PROPERTY_SUBMITTED = "PROPERTY_SUBMITTED";
    public static final String PROPERTY_APPROVED = "PROPERTY_APPROVED";
    public static final String PROPERTY_REJECTED = "PROPERTY_REJECTED";
    public static final String PROPERTY_STATUS_CHANGED = "PROPERTY_STATUS_CHANGED";
    public static final String PROPERTY_DELETED = "PROPERTY_DELETED";
    public static final String PROPERTY_PHOTO_ADDED = "PROPERTY_PHOTO_ADDED";
    public static final String PROPERTY_PHOTO_REMOVED = "PROPERTY_PHOTO_REMOVED";
    public static final String PROPERTY_CALENDAR_UPDATED = "PROPERTY_CALENDAR_UPDATED";

    public static final String BOOKING_CREATED = "BOOKING_CREATED";
    public static final String BOOKING_APPROVED = "BOOKING_APPROVED";
    public static final String BOOKING_DECLINED = "BOOKING_DECLINED";
    public static final String BOOKING_CONFIRMED = "BOOKING_CONFIRMED";
    public static final String BOOKING_CANCELLED = "BOOKING_CANCELLED";
    public static final String BOOKING_EXPIRED = "BOOKING_EXPIRED";
    public static final String BOOKING_COMPLETED = "BOOKING_COMPLETED";

    public static final String PAYMENT_STARTED = "PAYMENT_STARTED";
    public static final String PAYMENT_SUCCEEDED = "PAYMENT_SUCCEEDED";
    public static final String PAYMENT_FAILED = "PAYMENT_FAILED";
    public static final String PAYMENT_CALLBACK_REJECTED = "PAYMENT_CALLBACK_REJECTED";
    public static final String REFUND_ISSUED = "REFUND_ISSUED";
    public static final String COMMISSION_RULE_CHANGED = "COMMISSION_RULE_CHANGED";

    private AuditAction() {
    }
}
