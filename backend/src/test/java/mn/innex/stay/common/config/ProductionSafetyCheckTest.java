package mn.innex.stay.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;

import mn.innex.stay.listing.storage.S3Properties;
import mn.innex.stay.payment.gateway.SimulatedPaymentProperties;
import mn.innex.stay.security.CorsProperties;
import mn.innex.stay.security.JwtProperties;
import mn.innex.stay.user.auth.OtpProperties;
import mn.innex.stay.user.service.BootstrapProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * The guard that stops a deployment made with a forgotten environment variable.
 *
 * <p>Each case here is a setting that, left at its development default, is not a
 * degraded experience but a way in: codes readable from the log, bookings
 * settled without money, a signing key published in the repository. They are
 * asserted individually because the point is that no single one of them can slip
 * through on its own.
 */
class ProductionSafetyCheckTest {

    @Test
    @DisplayName("a deployment left on the development defaults does not start")
    void developmentDefaultsAreRejected() {
        assertThatThrownBy(() -> check(dev()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Refusing to start");
    }

    @Test
    @DisplayName("every violation is named at once, not one redeploy at a time")
    void allViolationsAreReportedTogether() {
        assertThatThrownBy(() -> check(dev()))
                .hasMessageContaining("app.security.jwt.secret")
                .hasMessageContaining("app.otp.delivery")
                .hasMessageContaining("app.otp.expose-code-in-response")
                .hasMessageContaining("app.payments.simulated.enabled")
                .hasMessageContaining("app.payments.simulated.callback-secret")
                .hasMessageContaining("app.bootstrap.super-admin.password")
                .hasMessageContaining("app.storage.s3.auto-create-bucket")
                .hasMessageContaining("app.cors.allowed-origins");
    }

    @Test
    @DisplayName("a correctly configured deployment starts")
    void safeSettingsPass() {
        assertThatCode(() -> check(safe())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("the published development signing key is rejected by value")
    void devJwtSecretIsRejected() {
        Settings s = safe().withJwt(ProductionSafetyCheck.DEV_JWT_SECRET);
        assertThatThrownBy(() -> check(s)).hasMessageContaining("openssl rand");
    }

    @Test
    @DisplayName("a short signing key is rejected even when it is not the dev one")
    void shortJwtSecretIsRejected() {
        Settings s = safe().withJwt("too-short-to-be-hs256");
        assertThatThrownBy(() -> check(s)).hasMessageContaining("at least 32 bytes");
    }

    @Test
    @DisplayName("logging sign-in codes is rejected")
    void loggedOtpDeliveryIsRejected() {
        Settings s = safe().withOtp("log", false);
        assertThatThrownBy(() -> check(s)).hasMessageContaining("OTP_DELIVERY=sms");
    }

    @Test
    @DisplayName("returning the sign-in code to the caller is rejected on its own")
    void exposedOtpCodeIsRejected() {
        // Separate from delivery on purpose: devCodeVisible() already requires both,
        // so this one could look harmless while delivery is correct. It is not — it
        // is the setting that stops the phone being a second factor.
        Settings s = safe().withOtp("sms", true);
        assertThatThrownBy(() -> check(s)).hasMessageContaining("OTP_EXPOSE_CODE=false");
    }

    @Test
    @DisplayName("the settle-without-paying endpoint is rejected")
    void simulatedPaymentsAreRejected() {
        Settings s = safe().withSimulatedPayments(true, "a-real-callback-secret-value");
        assertThatThrownBy(() -> check(s))
                .hasMessageContaining("PAYMENTS_SIMULATED_ENABLED=false");
    }

    @Test
    @DisplayName("localhost left in the CORS list is rejected")
    void localhostCorsIsRejected() {
        Settings s = safe().withCors(List.of("https://rently.mn", "http://localhost:3000"));
        assertThatThrownBy(() -> check(s)).hasMessageContaining("CORS_ALLOWED_ORIGINS");
    }

    @Test
    @DisplayName("the bootstrap password is only checked while bootstrapping is on")
    void disabledBootstrapIsNotFlagged() {
        // Turning the bootstrap off is a legitimate way to deploy — the admin
        // already exists — and the published password is then inert. Flagging it
        // would train people to ignore the guard.
        Settings s = safe().withBootstrap(false, ProductionSafetyCheck.DEV_ADMIN_PASSWORD);
        assertThatCode(() -> check(s)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("the guard is wired off under the dev profile and on without it")
    void profileGatingIsWiredCorrectly() {
        // The logic above is worth nothing if the bean never registers, or registers
        // on a laptop. This pins the @Profile("!dev") that decides which.
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withUserConfiguration(ProductionSafetyCheck.class)
                .withBean(JwtProperties.class, () -> dev().jwt)
                .withBean(OtpProperties.class, () -> dev().otp)
                .withBean(SimulatedPaymentProperties.class, () -> dev().payments)
                .withBean(BootstrapProperties.class, () -> dev().bootstrap)
                .withBean(S3Properties.class, () -> dev().s3)
                .withBean(CorsProperties.class, () -> dev().cors);

        // On a laptop: the bean is never created, so the dev settings are fine.
        runner.withPropertyValues("spring.profiles.active=dev")
                .run(context -> assertThat(context)
                        .hasNotFailed()
                        .doesNotHaveBean(ProductionSafetyCheck.class));

        // With no profile set — how a packaged jar reaches a server — it runs.
        runner.run(context -> {
            assertThat(context).hasFailed();
            // Spring wraps a constructor throw in BeanCreationException, so the
            // message a deployer actually needs is one level down.
            assertThat(context).getFailure().rootCause()
                    .hasMessageContaining("Refusing to start");
        });
    }

    private static void check(Settings s) {
        new ProductionSafetyCheck(s.jwt, s.otp, s.payments, s.bootstrap, s.s3, s.cors);
    }

    /** Everything a deployment would be wrong about, all at once. */
    private static Settings dev() {
        return new Settings(
                new JwtProperties("stay-backend", ProductionSafetyCheck.DEV_JWT_SECRET,
                        Duration.ofMinutes(15), Duration.ofDays(30)),
                otpProperties("log", true),
                new SimulatedPaymentProperties(true,
                        ProductionSafetyCheck.DEV_CALLBACK_SECRET, Duration.ofMinutes(30)),
                bootstrapProperties(true, ProductionSafetyCheck.DEV_ADMIN_PASSWORD),
                s3Properties(true),
                new CorsProperties(List.of("http://localhost:5173", "http://localhost:3000")));
    }

    /** What a real deployment should look like. */
    private static Settings safe() {
        return new Settings(
                new JwtProperties("stay-backend", "a-genuinely-long-production-signing-key-value",
                        Duration.ofMinutes(15), Duration.ofDays(30)),
                otpProperties("sms", false),
                new SimulatedPaymentProperties(false, "a-real-callback-secret-value",
                        Duration.ofMinutes(30)),
                bootstrapProperties(true, "a-real-admin-password"),
                s3Properties(false),
                new CorsProperties(List.of("https://rently.mn")));
    }

    private static OtpProperties otpProperties(String delivery, boolean expose) {
        return new OtpProperties(6, Duration.ofMinutes(5), 5, Duration.ofSeconds(60), 5,
                Duration.ofMinutes(15), delivery, expose);
    }

    private static BootstrapProperties bootstrapProperties(boolean enabled, String password) {
        return new BootstrapProperties(new BootstrapProperties.SuperAdmin(
                enabled, "+97699000000", "admin@rently.mn", password));
    }

    private static S3Properties s3Properties(boolean autoCreate) {
        return new S3Properties("https://s3.example", "us-east-1", "rently-media",
                "key", "secret", true, "https://cdn.example", Duration.ofMinutes(5),
                10_485_760L, autoCreate);
    }

    private record Settings(JwtProperties jwt,
                            OtpProperties otp,
                            SimulatedPaymentProperties payments,
                            BootstrapProperties bootstrap,
                            S3Properties s3,
                            CorsProperties cors) {

        Settings withJwt(String secret) {
            return new Settings(new JwtProperties(jwt.issuer(), secret,
                    jwt.accessTokenTtl(), jwt.refreshTokenTtl()), otp, payments, bootstrap, s3, cors);
        }

        Settings withOtp(String delivery, boolean expose) {
            return new Settings(jwt, otpProperties(delivery, expose), payments, bootstrap, s3, cors);
        }

        Settings withSimulatedPayments(boolean enabled, String secret) {
            return new Settings(jwt, otp,
                    new SimulatedPaymentProperties(enabled, secret, payments.invoiceTtl()),
                    bootstrap, s3, cors);
        }

        Settings withBootstrap(boolean enabled, String password) {
            return new Settings(jwt, otp, payments,
                    bootstrapProperties(enabled, password), s3, cors);
        }

        Settings withCors(List<String> origins) {
            return new Settings(jwt, otp, payments, bootstrap, s3, new CorsProperties(origins));
        }
    }
}
