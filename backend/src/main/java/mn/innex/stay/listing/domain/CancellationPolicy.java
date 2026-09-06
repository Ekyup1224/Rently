package mn.innex.stay.listing.domain;

/**
 * Cancellation tiers offered to hosts, with the refund each gives a guest.
 *
 * <p>The percentages are business policy, kept in one place so they can be read
 * and argued about without reading the booking code. Every booking snapshots the
 * policy it was made under, so changing a listing's tier never alters the terms
 * of a stay someone already paid for.
 *
 * <p>Refunds apply to the accommodation total (nights plus cleaning fee). The
 * guest service fee is returned only on a full refund.
 */
public enum CancellationPolicy {

    /** Full refund until 24 hours before check-in, then the first night is withheld. */
    FLEXIBLE(1, 100, 0, true),

    /** Full refund until 5 days before check-in, then 50%. */
    MODERATE(5, 100, 50, false),

    /** 50% until 7 days before check-in, then nothing. */
    STRICT(7, 50, 0, false);

    private final int freeCancellationDays;
    private final int refundPercentBeforeCutoff;
    private final int refundPercentAfterCutoff;
    private final boolean withholdFirstNightAfterCutoff;

    CancellationPolicy(int freeCancellationDays, int refundPercentBeforeCutoff,
                       int refundPercentAfterCutoff, boolean withholdFirstNightAfterCutoff) {
        this.freeCancellationDays = freeCancellationDays;
        this.refundPercentBeforeCutoff = refundPercentBeforeCutoff;
        this.refundPercentAfterCutoff = refundPercentAfterCutoff;
        this.withholdFirstNightAfterCutoff = withholdFirstNightAfterCutoff;
    }

    /** Days before check-in up to which {@link #getRefundPercentBeforeCutoff()} applies. */
    public int getFreeCancellationDays() {
        return freeCancellationDays;
    }

    public int getRefundPercentBeforeCutoff() {
        return refundPercentBeforeCutoff;
    }

    public int getRefundPercentAfterCutoff() {
        return refundPercentAfterCutoff;
    }

    /**
     * When true, a late cancellation withholds one night rather than applying
     * {@link #getRefundPercentAfterCutoff()} to the whole stay.
     */
    public boolean isWithholdFirstNightAfterCutoff() {
        return withholdFirstNightAfterCutoff;
    }
}
