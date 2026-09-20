package mn.innex.stay.user.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import mn.innex.stay.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The guard around returning an OTP code to the caller.
 *
 * <p>Handing a login code back over the API is a gift to anyone who can reach the
 * endpoint: they would no longer need the phone at all. It exists only so local
 * development does not mean reading the server log, so what matters is that it
 * cannot survive into a deployment — which is what these pin down.
 */
@SpringBootTest(properties = "app.otp.delivery=sms")
class OtpCodeExposureTest extends IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("real SMS delivery withholds the code even with the flag left on")
    void smsDeliveryWithholdsTheCode() throws Exception {
        // app.otp.expose-code-in-response is still true here, exactly as someone
        // would leave it when deploying a config copied from development.
        mockMvc.perform(post("/api/v1/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"+97699001122\"}"))
                .andExpect(status().isOk())
                // Absent rather than null: a client cannot tell a withheld code
                // from a server that never had the feature.
                .andExpect(jsonPath("$.devCode").doesNotExist());
    }

    @Test
    @DisplayName("the code is visible only when logging and exposure are both on")
    void bothConditionsAreRequired() {
        assertThat(properties("log", true).devCodeVisible()).isTrue();
        assertThat(properties("log", false).devCodeVisible()).isFalse();
        assertThat(properties("sms", true).devCodeVisible()).isFalse();
        assertThat(properties("sms", false).devCodeVisible()).isFalse();
    }

    private static OtpProperties properties(String delivery, boolean expose) {
        return new OtpProperties(6, Duration.ofMinutes(5), 5, Duration.ofSeconds(60), 5,
                Duration.ofMinutes(15), delivery, expose);
    }
}
