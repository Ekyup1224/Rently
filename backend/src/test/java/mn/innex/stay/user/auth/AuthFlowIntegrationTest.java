package mn.innex.stay.user.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


import mn.innex.stay.IntegrationTest;
import mn.innex.stay.user.domain.UserStatus;
import mn.innex.stay.user.repo.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** The phone-first authentication path, end to end over HTTP. */
class AuthFlowIntegrationTest extends IntegrationTest {

    /** Each test uses its own number so per-destination rate limits stay independent. */

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RecordingSmsSender smsSender;

    @Autowired
    private StringRedisTemplate redis;

    private String phone;

    @BeforeEach
    void setUp() {
        phone = uniquePhone();
        smsSender.clear();
    }

    @Test
    @DisplayName("an unknown number gets an account and a session from one code")
    void otpLoginCreatesAccountAndSession() throws Exception {
        requestCode();
        assertThat(userRepository.findByPhone(phone))
                .as("account is created when the code is requested, before it is verified")
                .isPresent()
                .get()
                .extracting(mn.innex.stay.user.domain.User::getStatus)
                .isEqualTo(UserStatus.PENDING_VERIFICATION);

        MvcResult result = verifyCode(smsSender.lastCodeFor(phone))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.user.status").value("ACTIVE"))
                .andExpect(jsonPath("$.user.phoneVerified").value(true))
                .andExpect(jsonPath("$.user.roles[0].role").value("CLIENT"))
                .andExpect(jsonPath("$.user.roles[0].organizationId").doesNotExist())
                .andReturn();

        String accessToken = readJson(result, "accessToken");
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value(phone));
    }

    @Test
    @DisplayName("a code works once and only once")
    void codeIsSingleUse() throws Exception {
        requestCode();
        String code = smsSender.lastCodeFor(phone);
        verifyCode(code).andExpect(status().isOk());
        verifyCode(code)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("otp_expired"));
    }

    @Test
    @DisplayName("a wrong code is rejected without burning the real one")
    void wrongCodeDoesNotInvalidateTheRealCode() throws Exception {
        requestCode();
        String code = smsSender.lastCodeFor(phone);
        String wrongCode = code.equals("000000") ? "111111" : "000000";

        verifyCode(wrongCode)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("otp_invalid"));
        verifyCode(code).andExpect(status().isOk());
    }

    @Test
    @DisplayName("repeated wrong codes lock the number and burn the code")
    void exhaustingAttemptsLocksTheNumber() throws Exception {
        requestCode();
        String code = smsSender.lastCodeFor(phone);

        // The policy allows 5 attempts; the fifth trips the lockout.
        for (int attempt = 1; attempt <= 4; attempt++) {
            verifyCode("000000").andExpect(status().isBadRequest());
        }
        verifyCode("000000")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("otp_locked"));

        // Even the correct code is now useless: the lockout precedes verification.
        verifyCode(code)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("otp_locked"));
    }

    @Test
    @DisplayName("a resend within the cooldown is refused and sends no second SMS")
    void resendIsRateLimited() throws Exception {
        requestCode();
        assertThat(smsSender.countFor(phone)).isEqualTo(1);

        mockMvc.perform(post("/api/v1/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + phone + "\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("otp_cooldown"));

        assertThat(smsSender.countFor(phone))
                .as("a refused resend must not reach the SMS gateway")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("refresh rotates the token and replaying the old one ends every session")
    void refreshRotatesAndDetectsReplay() throws Exception {
        requestCode();
        MvcResult session = verifyCode(smsSender.lastCodeFor(phone)).andExpect(status().isOk()).andReturn();
        String firstRefresh = readJson(session, "refreshToken");

        MvcResult rotated = refresh(firstRefresh).andExpect(status().isOk()).andReturn();
        String secondRefresh = readJson(rotated, "refreshToken");
        assertThat(secondRefresh).isNotEqualTo(firstRefresh);

        refresh(firstRefresh)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("refresh_token_reused"));

        // The successor must die with it, or a thief keeps the session they stole.
        // It was revoked without being replaced, so it reports as a dead session.
        refresh(secondRefresh)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("refresh_token_revoked"));
    }

    @Test
    @DisplayName("logout revokes only the session that was presented")
    void logoutRevokesOneSession() throws Exception {
        requestCode();
        MvcResult first = verifyCode(smsSender.lastCodeFor(phone)).andExpect(status().isOk()).andReturn();
        String accessToken = readJson(first, "accessToken");
        String refreshToken = readJson(first, "refreshToken");

        // A second device: clear the cooldown rather than waiting it out.
        redis.delete("otp:cooldown:LOGIN:" + phone);
        requestCode();
        MvcResult second = verifyCode(smsSender.lastCodeFor(phone)).andExpect(status().isOk()).andReturn();
        String otherDeviceRefresh = readJson(second, "refreshToken");

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isNoContent());

        // Presenting the logged-out token must not be mistaken for a replay: the
        // other device has to survive.
        refresh(refreshToken)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("refresh_token_revoked"));
        refresh(otherDeviceRefresh)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    @DisplayName("unauthenticated and mis-signed tokens are both 401")
    void protectedEndpointsRequireAValidToken() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("unauthenticated"));

        // Correctly shaped JWT, wrong signature.
        String forged = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIwMDAwMDAwMC0wMDAwLTcwMDAtODAwMC0wMDAwMDAwMDAwMDAi"
                + "LCJyb2xlcyI6WyJTVVBFUl9BRE1JTiJdfQ.not-a-valid-signature";
        mockMvc.perform(get("/api/v1/admin/users").header("Authorization", "Bearer " + forged))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a guest cannot reach the admin API even with a valid token")
    void guestCannotReachAdminApi() throws Exception {
        requestCode();
        MvcResult session = verifyCode(smsSender.lastCodeFor(phone)).andExpect(status().isOk()).andReturn();

        mockMvc.perform(get("/api/v1/admin/users")
                        .header("Authorization", "Bearer " + readJson(session, "accessToken")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("forbidden"));
    }

    @Test
    @DisplayName("a suspended account cannot request a code")
    void suspendedAccountCannotRequestCode() throws Exception {
        requestCode();
        verifyCode(smsSender.lastCodeFor(phone)).andExpect(status().isOk());

        var user = userRepository.findByPhone(phone).orElseThrow();
        user.setStatus(UserStatus.SUSPENDED);
        userRepository.save(user);
        redis.delete("otp:cooldown:LOGIN:" + phone);

        mockMvc.perform(post("/api/v1/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + phone + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("account_not_active"));
    }

    @Test
    @DisplayName("the development configuration returns the code, and it is the real one")
    void devConfigurationReturnsTheCode() throws Exception {
        MvcResult issued = mockMvc.perform(post("/api/v1/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + phone + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.devCode").exists())
                .andReturn();

        // Worth asserting rather than assuming: a code that does not actually work
        // would send someone back to the log, which is the whole point of this.
        assertThat(readJson(issued, "devCode")).isEqualTo(smsSender.lastCodeFor(phone));
        verifyCode(readJson(issued, "devCode")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("a malformed phone number is rejected before anything is created")
    void malformedPhoneIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"12345\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_phone"));
        assertThat(smsSender.countFor("+97612345")).isZero();
    }

    private void requestCode() throws Exception {
        mockMvc.perform(post("/api/v1/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"" + phone + "\",\"locale\":\"mn\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resendAfterSeconds").value(60));
    }

    private org.springframework.test.web.servlet.ResultActions verifyCode(String code) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/otp/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\":\"" + phone + "\",\"code\":\"" + code + "\"}"));
    }

    private org.springframework.test.web.servlet.ResultActions refresh(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refreshToken + "\"}"));
    }

    private String readJson(MvcResult result, String field) throws Exception {
        return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$." + field);
    }
}
