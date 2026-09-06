package mn.innex.stay.payment.web.dto;

import mn.innex.stay.payment.domain.PaymentProvider;

/**
 * Starts a payment.
 *
 * @param provider which rail to use; null takes the deployment's default. Only
 *                 providers this deployment has credentials for are accepted.
 */
public record StartPaymentRequest(PaymentProvider provider) {
}
