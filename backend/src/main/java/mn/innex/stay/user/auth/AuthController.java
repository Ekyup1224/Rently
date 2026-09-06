package mn.innex.stay.user.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import mn.innex.stay.common.ApiException;
import mn.innex.stay.common.web.ClientIp;
import mn.innex.stay.security.CurrentActor;
import mn.innex.stay.user.auth.dto.AuthResponse;
import mn.innex.stay.user.auth.dto.ForgotPasswordRequest;
import mn.innex.stay.user.auth.dto.LoginRequest;
import mn.innex.stay.user.auth.dto.OtpChallengeResponse;
import mn.innex.stay.user.auth.dto.OtpRequest;
import mn.innex.stay.user.auth.dto.OtpVerifyRequest;
import mn.innex.stay.user.auth.dto.RefreshRequest;
import mn.innex.stay.user.auth.dto.RegisterRequest;
import mn.innex.stay.user.auth.dto.ResetPasswordRequest;
import mn.innex.stay.user.repo.UserRepository;
import mn.innex.stay.user.web.dto.UserResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentication endpoints. Everything here except {@code /me} and {@code /logout}
 * is unauthenticated, and each one is rate limited by the OTP policy or by
 * returning identical errors for every failure mode.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final UserRepository userRepository;

    public AuthController(AuthService authService, UserRepository userRepository) {
        this.authService = authService;
        this.userRepository = userRepository;
    }

    /**
     * Sends a login code, creating the account if the number is new. This is the
     * primary entry point: it is both "register" and "sign in".
     */
    @PostMapping("/otp/request")
    public OtpChallengeResponse requestOtp(@Valid @RequestBody OtpRequest request,
                                           HttpServletRequest httpRequest) {
        return authService.requestLoginOtp(request, ClientIp.of(httpRequest));
    }

    /** Exchanges a login code for an access token and a refresh token. */
    @PostMapping("/otp/verify")
    public AuthResponse verifyOtp(@Valid @RequestBody OtpVerifyRequest request,
                                  HttpServletRequest httpRequest) {
        return authService.verifyLoginOtp(request, ClientIp.of(httpRequest));
    }

    /** Email + password registration. The phone still needs verifying afterwards. */
    @PostMapping("/register")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.CREATED)
    public OtpChallengeResponse register(@Valid @RequestBody RegisterRequest request,
                                         HttpServletRequest httpRequest) {
        return authService.register(request, ClientIp.of(httpRequest));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request,
                              HttpServletRequest httpRequest) {
        return authService.login(request, ClientIp.of(httpRequest));
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest request,
                                HttpServletRequest httpRequest) {
        return authService.refresh(request.refreshToken(), ClientIp.of(httpRequest));
    }

    /** Revokes the presented refresh token. The access token expires on its own. */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request,
                                       HttpServletRequest httpRequest) {
        authService.logout(request.refreshToken(), CurrentActor.userIdOrEmpty().orElse(null),
                ClientIp.of(httpRequest));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/password/forgot")
    public OtpChallengeResponse forgotPassword(@Valid @RequestBody ForgotPasswordRequest request,
                                               HttpServletRequest httpRequest) {
        return authService.forgotPassword(request, ClientIp.of(httpRequest));
    }

    @PostMapping("/password/reset")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request,
                                              HttpServletRequest httpRequest) {
        authService.resetPassword(request, ClientIp.of(httpRequest));
        return ResponseEntity.noContent().build();
    }

    /** The signed-in account, including its role grants. Used by both frontends on boot. */
    @GetMapping("/me")
    public UserResponse me() {
        return userRepository.findByIdWithRoles(CurrentActor.requireUserId())
                .map(UserResponse::from)
                .orElseThrow(() -> ApiException.unauthorized("unauthenticated",
                        "Authentication is required"));
    }
}
