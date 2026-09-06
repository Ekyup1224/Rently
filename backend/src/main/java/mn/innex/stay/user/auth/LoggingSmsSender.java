package mn.innex.stay.user.auth;

import mn.innex.stay.common.PhoneNumbers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Development SMS sink: prints the message so OTP flows can be exercised with no
 * carrier account. Active while {@code app.otp.delivery=log}.
 *
 * <p>The full message — including the code — is logged deliberately. That is only
 * acceptable because this bean is off in every deployed environment; do not enable
 * {@code delivery=log} anywhere real.
 */
@Component
@ConditionalOnProperty(name = "app.otp.delivery", havingValue = "log", matchIfMissing = true)
public class LoggingSmsSender implements SmsSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingSmsSender.class);

    @Override
    public void send(String e164Phone, String message) {
        log.info("[DEV SMS] to={} body={}", PhoneNumbers.mask(e164Phone), message);
    }
}
