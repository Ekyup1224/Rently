package mn.innex.stay.common.config;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import mn.innex.stay.listing.storage.S3Properties;
import mn.innex.stay.payment.gateway.SimulatedPaymentProperties;
import mn.innex.stay.security.CorsProperties;
import mn.innex.stay.security.JwtProperties;
import mn.innex.stay.user.auth.OtpProperties;
import mn.innex.stay.user.service.BootstrapProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Refuses to start when a development convenience is still switched on.
 *
 * <p>Every dangerous setting in {@code application.yml} defaults to the value
 * that makes a laptop pleasant: OTP codes go to the log and come back in the
 * response, payments can be settled without money moving, the JWT secret and the
 * bootstrap admin password are published in {@code .env.example}. Each one is
 * one environment variable away from being correct, and nothing noticed when
 * that variable was missing — a deployment made with a forgotten flag came up
 * healthy and served traffic.
 *
 * <p>So this keys off the <em>absence</em> of the {@code dev} profile rather
 * than the presence of a {@code prod} one. Forgetting to set a profile is the
 * mistake people actually make, and that mistake now stops the application
 * instead of quietly loosening it. Running the dev profile somewhere real is
 * still possible, but it takes someone deliberately asking for it.
 *
 * <p>{@code ./mvnw spring-boot:run} activates {@code dev} through the Spring Boot
 * Maven plugin, and the tests activate it through {@code IntegrationTest}, so the
 * two ways this is run locally are unaffected. A packaged jar started with no
 * profile — which is how it reaches a server — gets checked.
 *
 * <p>Every violation is reported at once. Finding these one redeploy at a time
 * is how a hardening step turns into an afternoon.
 */
@Configuration(proxyBeanMethods = false)
@Profile("!dev")
public class ProductionSafetyCheck {

    /**
     * Published in {@code .env.example} and committed, so these are public
     * knowledge rather than secrets — which is the whole point of rejecting them.
     */
    static final String DEV_JWT_SECRET = "dev-only-secret-change-me-at-least-32-bytes-long!!";
    static final String DEV_CALLBACK_SECRET = "dev-simulated-callback-secret-change-me";
    static final String DEV_ADMIN_PASSWORD = "ChangeMe123!";

    /** HS256 needs a key at least as long as its output to deliver its rated strength. */
    private static final int MIN_JWT_SECRET_BYTES = 32;

    public ProductionSafetyCheck(JwtProperties jwt,
                                 OtpProperties otp,
                                 SimulatedPaymentProperties simulatedPayments,
                                 BootstrapProperties bootstrap,
                                 S3Properties s3,
                                 CorsProperties cors) {
        List<String> violations = new ArrayList<>();

        if (DEV_JWT_SECRET.equals(jwt.secret())) {
            violations.add("app.security.jwt.secret is the published development value. "
                    + "Set JWT_SECRET; generate one with: openssl rand -base64 48");
        } else if (jwt.secret() == null
                || jwt.secret().getBytes(StandardCharsets.UTF_8).length < MIN_JWT_SECRET_BYTES) {
            violations.add("app.security.jwt.secret must be at least "
                    + MIN_JWT_SECRET_BYTES + " bytes for HS256. Set a longer JWT_SECRET.");
        }

        if ("log".equalsIgnoreCase(otp.delivery())) {
            violations.add("app.otp.delivery=log writes every sign-in code to the application "
                    + "log, where anyone who can read logs can sign in as anyone. "
                    + "Set OTP_DELIVERY=sms and provide an SmsSender bean.");
        }

        if (otp.exposeCodeInResponse()) {
            violations.add("app.otp.expose-code-in-response=true returns the sign-in code to "
                    + "whoever asked for it, so the phone stops being a second factor at all. "
                    + "Set OTP_EXPOSE_CODE=false.");
        }

        if (simulatedPayments.enabled()) {
            violations.add("app.payments.simulated.enabled=true registers an endpoint that marks "
                    + "a booking paid without money moving. Set PAYMENTS_SIMULATED_ENABLED=false.");
        }

        if (DEV_CALLBACK_SECRET.equals(simulatedPayments.callbackSecret())) {
            violations.add("app.payments.simulated.callback-secret is the published development "
                    + "value, so callbacks can be forged. Set PAYMENTS_SIMULATED_SECRET.");
        }

        if (bootstrap.superAdmin() != null
                && bootstrap.superAdmin().enabled()
                && DEV_ADMIN_PASSWORD.equals(bootstrap.superAdmin().password())) {
            violations.add("app.bootstrap.super-admin.password is the published development value, "
                    + "and that account owns every admin action. Set BOOTSTRAP_ADMIN_PASSWORD.");
        }

        if (s3.autoCreateBucket()) {
            violations.add("app.storage.s3.auto-create-bucket=true creates the bucket and opens "
                    + "its photo prefix for public reads. Set S3_AUTO_CREATE_BUCKET=false and "
                    + "front the bucket with a CDN.");
        }

        List<String> localOrigins = cors.allowedOrigins() == null ? List.of()
                : cors.allowedOrigins().stream().filter(ProductionSafetyCheck::isLoopback).toList();
        if (!localOrigins.isEmpty()) {
            violations.add("app.cors.allowed-origins still contains development origins "
                    + localOrigins + ", which means the deployed frontends are not in the list. "
                    + "Set CORS_ALLOWED_ORIGINS to the real origins, comma-separated.");
        }

        if (!violations.isEmpty()) {
            throw new IllegalStateException(describe(violations));
        }
    }

    private static boolean isLoopback(String origin) {
        String lower = origin.toLowerCase();
        return lower.contains("localhost") || lower.contains("127.0.0.1") || lower.contains("[::1]");
    }

    private static String describe(List<String> violations) {
        StringBuilder message = new StringBuilder()
                .append("Refusing to start: ")
                .append(violations.size())
                .append(violations.size() == 1 ? " development setting is" : " development settings are")
                .append(" still active outside the 'dev' profile.\n");
        for (int i = 0; i < violations.size(); i++) {
            message.append("  ").append(i + 1).append(". ").append(violations.get(i)).append('\n');
        }
        return message
                .append("\nIf this is a development machine, run with the 'dev' profile ")
                .append("(./mvnw spring-boot:run does this already), or ")
                .append("SPRING_PROFILES_ACTIVE=dev.")
                .toString();
    }
}
