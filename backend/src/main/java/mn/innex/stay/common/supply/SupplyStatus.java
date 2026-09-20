package mn.innex.stay.listing.domain;

/**
 * Listing lifecycle. Only {@link #APPROVED} is visible to guests.
 *
 * <p>{@link #PAUSED} and {@link #SUSPENDED} are both invisible but differ in who
 * can undo them: an owner can unpause their own listing, while a suspension is an
 * admin action the owner cannot reverse.
 */
public enum PropertyStatus {

    /** Being written. Not submitted, not visible. */
    DRAFT,

    /** Submitted and waiting on admin review. */
    PENDING_REVIEW,

    /** Live and bookable. */
    APPROVED,

    /** Review failed; the owner can fix it and resubmit. */
    REJECTED,

    /** Temporarily withdrawn by the owner. */
    PAUSED,

    /** Withdrawn by an admin; the owner cannot reverse it. */
    SUSPENDED;

    public boolean isPubliclyVisible() {
        return this == APPROVED;
    }

    /** Whether the owner may still edit the listing's content in this state. */
    public boolean isOwnerEditable() {
        return this != SUSPENDED;
    }

    /** Whether the owner may submit it for review from here. */
    public boolean isSubmittable() {
        return this == DRAFT || this == REJECTED;
    }
}
