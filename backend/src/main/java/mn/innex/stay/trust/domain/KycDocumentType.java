package mn.innex.stay.trust.domain;

public enum KycDocumentType {
    NATIONAL_ID,
    PASSPORT,
    DRIVING_LICENCE,
    /** For an organization rather than a person: the state registration document. */
    BUSINESS_REGISTRATION
}
