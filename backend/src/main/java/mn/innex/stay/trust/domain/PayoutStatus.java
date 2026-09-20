package mn.innex.stay.trust.domain;

/** Where a host's money has got to. */
public enum PayoutStatus {

    /** Waiting for the hold to expire. The normal state of a fresh booking. */
    PENDING,

    /**
     * Held past its release time because something needs a human: an unverified
     * payee, a reported listing, a photo that belongs to someone else.
     */
    BLOCKED,

    /** Cleared for transfer. The money has not moved yet. */
    RELEASED,

    /** Transferred, with the bank reference recorded. */
    PAID,

    /** The booking was cancelled or refunded, so there is nothing to pay. */
    CANCELLED;

    public boolean isFinal() {
        return this == PAID || this == CANCELLED;
    }
}
