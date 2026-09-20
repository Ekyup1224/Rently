package mn.innex.stay.trust.domain;

public enum PayoutBatchStatus {

    /** Assembled, not yet sent to the bank. */
    OPEN,

    /** The transfer file has been produced and handed over. */
    EXPORTED,

    /** The bank has confirmed the transfers landed. */
    SETTLED
}
