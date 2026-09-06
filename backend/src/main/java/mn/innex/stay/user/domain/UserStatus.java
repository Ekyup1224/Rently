package mn.innex.stay.user.domain;

public enum UserStatus {

    /** Registered but the phone number has never been verified; cannot hold a session. */
    PENDING_VERIFICATION,

    ACTIVE,

    /** Blocked by an admin. Existing sessions are revoked on suspension. */
    SUSPENDED,

    /** Soft-deleted: retained for booking history, cannot authenticate. */
    DELETED;

    public boolean canAuthenticate() {
        return this == ACTIVE || this == PENDING_VERIFICATION;
    }
}
