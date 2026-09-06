package mn.innex.stay.payment.domain;

/** Payment rails. Mongolian guests use the QR/wallet providers; cards go to Stripe. */
public enum PaymentProvider {

    /** Mongolia's dominant QR invoice rail. */
    QPAY,

    SOCIALPAY,

    /** Cards and international guests. */
    STRIPE,

    /**
     * Stands in for a real provider until merchant credentials exist: it mimics
     * QPay's invoice/QR/callback shape so the booking flow can be exercised end
     * to end. It settles payments without money moving, so it must never be
     * enabled outside development.
     */
    SIMULATED
}
