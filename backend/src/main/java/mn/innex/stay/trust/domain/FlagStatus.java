package mn.innex.stay.trust.domain;

public enum FlagStatus {

    /** Awaiting a decision. Blocks approval, and blocks the listing's payouts. */
    OPEN,

    /** Looked at and found harmless. */
    DISMISSED,

    /** Confirmed. The listing is suspended and its payouts stay blocked. */
    UPHELD
}
