package mn.innex.stay.payment.web;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import mn.innex.stay.common.ApiException;
import mn.innex.stay.payment.domain.Payment;
import mn.innex.stay.payment.domain.PaymentProvider;
import mn.innex.stay.payment.gateway.SimulatedPaymentGateway;
import mn.innex.stay.payment.service.PaymentService;
import mn.innex.stay.payment.web.dto.PaymentResponse;
import mn.innex.stay.security.CurrentActor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

/**
 * Development affordance: settles a simulated payment as if the guest had paid.
 *
 * <p>Exists because a QR rail settles out-of-band, and without a bank app there is
 * no way to complete a checkout locally. Rather than a back door that skips the
 * payment machinery, this builds a properly signed callback and feeds it through
 * the same handler a real provider's callback goes through — so the flow being
 * exercised is the real one.
 *
 * <p>Registered only while {@code app.payments.simulated.enabled=true}, so in any
 * deployed environment this endpoint does not exist. It also still requires the
 * caller to be the booking's guest.
 */
@RestController
@RequestMapping("/api/v1/bookings/{bookingId}/payments/{paymentId}")
@ConditionalOnProperty(name = "app.payments.simulated.enabled", havingValue = "true")
public class SimulatedPaymentController {

    private static final Logger log = LoggerFactory.getLogger(SimulatedPaymentController.class);

    private final PaymentService paymentService;
    private final SimulatedPaymentGateway gateway;
    private final ObjectMapper objectMapper;

    public SimulatedPaymentController(PaymentService paymentService,
                                      SimulatedPaymentGateway gateway,
                                      ObjectMapper objectMapper) {
        this.paymentService = paymentService;
        this.gateway = gateway;
        this.objectMapper = objectMapper;
        log.warn("Simulated payment settlement endpoint is registered at "
                + "POST /api/v1/bookings/{id}/payments/{paymentId}/simulate-settlement. "
                + "This must not be enabled outside development.");
    }

    /** Marks a simulated payment settled, as a real provider callback would. */
    @PostMapping("/simulate-settlement")
    public PaymentResponse settle(@PathVariable UUID bookingId, @PathVariable UUID paymentId) {
        Payment payment = paymentService.requireForGuest(
                CurrentActor.requireUserId(), bookingId, paymentId);

        if (payment.getProvider() != PaymentProvider.SIMULATED) {
            throw ApiException.badRequest("not_a_simulated_payment",
                    "Only a simulated payment can be settled this way");
        }
        if (payment.getProviderRef() == null) {
            throw ApiException.conflict("payment_not_open",
                    "This payment has no open invoice to settle");
        }

        Map<String, Object> callback = new LinkedHashMap<>();
        callback.put("eventId", UUID.randomUUID().toString());
        callback.put("invoiceId", payment.getProviderRef());
        callback.put("status", "PAID");
        callback.put("amount", payment.getAmount().toPlainString());
        callback.put("currency", payment.getCurrency());

        String body = objectMapper.writeValueAsString(callback);
        // Signed exactly as the gateway signs its own callbacks, so the signature
        // verification path is genuinely exercised rather than bypassed.
        Map<String, String> headers = Map.of(
                SimulatedPaymentGateway.SIGNATURE_HEADER, gateway.sign(body));

        paymentService.handleCallback(PaymentProvider.SIMULATED, body, headers);

        return PaymentResponse.from(paymentService.requireForGuest(
                CurrentActor.requireUserId(), bookingId, paymentId));
    }
}
