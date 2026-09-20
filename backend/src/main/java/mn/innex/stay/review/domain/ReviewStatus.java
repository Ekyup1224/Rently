package mn.innex.stay.review.domain;

public enum ReviewStatus {

    /** Normal. Shown once it is visible. */
    PUBLISHED,

    /** Taken down by a moderator; never shown, and excluded from the average. */
    HIDDEN
}
