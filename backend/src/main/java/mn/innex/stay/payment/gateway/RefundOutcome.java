package mn.innex.stay.payment.gateway;

/**
 * Result of asking a provider to return money.
 *
 * @param providerRef the provider's id for the refund, for reconciliation
 * @param settled     true when the money is already on its way back; false means
 *                    accepted but pending, and the callback will confirm
 */
public record RefundOutcome(String providerRef, boolean settled, String failureMessage) {

    public static RefundOutcome settled(String providerRef) {
        return new RefundOutcome(providerRef, true, null);
    }

    public static RefundOutcome failed(String failureMessage) {
        return new RefundOutcome(null, false, failureMessage);
    }
}
