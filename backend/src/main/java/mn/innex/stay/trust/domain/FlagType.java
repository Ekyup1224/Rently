package mn.innex.stay.trust.domain;

/** Why a listing needs looking at. */
public enum FlagType {

    /** A photo matches one already published by a different account. */
    DUPLICATE_PHOTO,

    /** A guest said something is wrong with this listing. */
    GUEST_REPORT
}
