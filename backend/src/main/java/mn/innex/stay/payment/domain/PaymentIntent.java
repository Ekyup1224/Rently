package mn.innex.stay.payment.domain;

/** Direction of a payment row. Refunds point back at the charge they reverse. */
public enum PaymentIntent {
    CHARGE, REFUND
}
