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

    public static final String HOTEL_CREATED = "HOTEL_CREATED";
    public static final String HOTEL_UPDATED = "HOTEL_UPDATED";
    public static final String HOTEL_SUBMITTED = "HOTEL_SUBMITTED";
    public static final String HOTEL_APPROVED = "HOTEL_APPROVED";
    public static final String HOTEL_REJECTED = "HOTEL_REJECTED";
    public static final String HOTEL_STATUS_CHANGED = "HOTEL_STATUS_CHANGED";
    public static final String HOTEL_PHOTO_ADDED = "HOTEL_PHOTO_ADDED";
    public static final String HOTEL_PHOTO_REMOVED = "HOTEL_PHOTO_REMOVED";
    public static final String HOTEL_STAFF_ADDED = "HOTEL_STAFF_ADDED";
    public static final String HOTEL_STAFF_REMOVED = "HOTEL_STAFF_REMOVED";
    public static final String ROOM_TYPE_CREATED = "ROOM_TYPE_CREATED";
    public static final String ROOM_TYPE_UPDATED = "ROOM_TYPE_UPDATED";
    public static final String ROOM_TYPE_DELETED = "ROOM_TYPE_DELETED";
    public static final String ROOM_INVENTORY_UPDATED = "ROOM_INVENTORY_UPDATED";
    public static final String BOOKING_CHECKED_IN = "BOOKING_CHECKED_IN";
    public static final String BOOKING_CHECKED_OUT = "BOOKING_CHECKED_OUT";
    public static final String ORGANIZATION_CREATED = "ORGANIZATION_CREATED";
    public static final String HOST_APPLICATION_DECIDED = "HOST_APPLICATION_DECIDED";

    public static final String PAYMENT_STARTED = "PAYMENT_STARTED";
    public static final String PAYMENT_SUCCEEDED = "PAYMENT_SUCCEEDED";
    public static final String PAYMENT_FAILED = "PAYMENT_FAILED";
    public static final String PAYMENT_CALLBACK_REJECTED = "PAYMENT_CALLBACK_REJECTED";
    public static final String REFUND_ISSUED = "REFUND_ISSUED";
    public static final String COMMISSION_RULE_CHANGED = "COMMISSION_RULE_CHANGED";

    public static final String PAYOUT_SCHEDULED = "PAYOUT_SCHEDULED";
    public static final String PAYOUT_RELEASED = "PAYOUT_RELEASED";
    public static final String PAYOUT_BLOCKED = "PAYOUT_BLOCKED";
    public static final String PAYOUT_PAID = "PAYOUT_PAID";
    public static final String PAYOUT_CANCELLED = "PAYOUT_CANCELLED";
    public static final String LISTING_REPORTED = "LISTING_REPORTED";
    public static final String LISTING_FLAG_RESOLVED = "LISTING_FLAG_RESOLVED";
    public static final String KYC_SUBMITTED = "KYC_SUBMITTED";
    public static final String KYC_REVIEWED = "KYC_REVIEWED";

    public static final String REVIEW_WRITTEN = "REVIEW_WRITTEN";
    public static final String REVIEW_RESPONDED = "REVIEW_RESPONDED";
    public static final String REVIEW_MODERATED = "REVIEW_MODERATED";
    public static final String MESSAGE_SENT = "MESSAGE_SENT";
    public static final String MESSAGE_FLAGGED = "MESSAGE_FLAGGED";
    public static final String PAYOUT_BATCH_CREATED = "PAYOUT_BATCH_CREATED";
    public static final String PAYOUT_BATCH_EXPORTED = "PAYOUT_BATCH_EXPORTED";

    private AuditAction() {
    }
}
