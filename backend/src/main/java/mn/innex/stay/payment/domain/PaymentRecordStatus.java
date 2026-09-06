package mn.innex.stay.payment.domain;

/**
 * State of one payment attempt with a provider.
 *
 * <p>Distinct from the booking's own payment status: a booking may accumulate
 * several payment rows (a failed attempt, a retry, later a refund) and its status
 * is the aggregate of them.
 */
public enum PaymentRecordStatus {

    /** Row created, provider not yet called. */
    CREATED,

    /** Provider has an open invoice; waiting on the payer. */
    PENDING,

    SUCCEEDED,

    FAILED,

    CANCELLED,

    /** The provider's invoice lapsed unpaid. */
    EXPIRED;

    public boolean isSettled() {
        return this == SUCCEEDED;
    }

    public boolean isFinished() {
        return this == SUCCEEDED || this == FAILED || this == CANCELLED || this == EXPIRED;
    }
}
