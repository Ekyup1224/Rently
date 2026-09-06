package mn.innex.stay.payment.web;

import java.util.HashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import mn.innex.stay.payment.domain.PaymentProvider;
import mn.innex.stay.payment.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Where payment providers tell us a charge settled.
 *
 * <p>These endpoints are unauthenticated — a provider cannot hold a user session —
 * so the signature check inside each gateway is the only thing between the
 * internet and a booking marked paid. Three things follow from that:
 *
 * <ul>
 *   <li>The body is taken as a raw string, because a signature covers the exact
 *       bytes sent. Letting the framework parse and re-serialize it would break
 *       verification.
 *   <li>Nothing is trusted until the signature verifies, including which payment
 *       the callback claims to be about.
 *   <li>Every delivery is logged before processing and deduped, because providers
 *       redeliver by design.
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/payments/callbacks")
public class PaymentCallbackController {

    private static final Logger log = LoggerFactory.getLogger(PaymentCallbackController.class);

    private final PaymentService paymentService;

    public PaymentCallbackController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /**
     * Handles a provider callback.
     *
     * @param provider path segment naming the rail, e.g. {@code qpay}
     * @return a short result string; providers only care about the 2xx
     */
    @PostMapping(path = "/{provider}", consumes = MediaType.ALL_VALUE)
    public Map<String, String> handle(@PathVariable String provider,
                                      @RequestBody(required = false) String rawBody,
                                      HttpServletRequest httpRequest) {
        PaymentProvider parsed = parseProvider(provider);
        Map<String, String> headers = lowercasedHeaders(httpRequest);

        String result = paymentService.handleCallback(parsed, rawBody == null ? "" : rawBody, headers);
        log.info("{} callback processed: {}", parsed, result);
        return Map.of("result", result);
    }

    private PaymentProvider parseProvider(String provider) {
        try {
            return PaymentProvider.valueOf(provider.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            // 404 rather than 400: an unknown rail is an unknown endpoint.
            throw mn.innex.stay.common.ApiException.notFound("unknown_payment_provider",
                    "No callback endpoint for provider " + provider);
        }
    }

    /** Header names are case-insensitive over the wire; gateways look them up lower-cased. */
    private Map<String, String> lowercasedHeaders(HttpServletRequest request) {
        Map<String, String> headers = new HashMap<>();
        var names = request.getHeaderNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            headers.put(name.toLowerCase(), request.getHeader(name));
        }
        return headers;
    }
}
