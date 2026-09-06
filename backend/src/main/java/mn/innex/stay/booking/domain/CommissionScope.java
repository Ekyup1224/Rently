package mn.innex.stay.booking.domain;

/** How narrowly a {@link CommissionRule} applies. Most specific effective rule wins. */
public enum CommissionScope {

    /** Applies to everything. Exactly one is expected to be effective at a time. */
    GLOBAL,

    /** Narrowed to one property type, e.g. a different rate for gers. */
    PROPERTY_TYPE,

    /** Narrowed to one hotel, negotiated per property. Used from Step 3. */
    HOTEL
}
