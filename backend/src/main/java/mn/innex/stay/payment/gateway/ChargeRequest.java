package mn.innex.stay.payment.gateway;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * What a gateway needs to open a charge.
 *
 * @param paymentId      our row's id, passed to the provider so its callback can
 *                       be matched back without guessing
 * @param bookingRef     shown to the payer in their banking app
 * @param amount         gross amount in {@code currency}; providers that want
 *                       minor units convert at their own boundary
 * @param description    invoice line the payer sees
 */
public record ChargeRequest(
        UUID paymentId,
        String bookingRef,
        BigDecimal amount,
        String currency,
        String description) {
}
