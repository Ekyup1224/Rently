package mn.innex.stay.user.auth;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import mn.innex.stay.common.ApiException;
import mn.innex.stay.common.Emails;
import mn.innex.stay.common.PhoneNumbers;
import mn.innex.stay.common.audit.AuditAction;
import mn.innex.stay.common.audit.AuditService;
import mn.innex.stay.security.TokenService;
import mn.innex.stay.user.auth.dto.AuthResponse;
import mn.innex.stay.user.auth.dto.ForgotPasswordRequest;
import mn.innex.stay.user.auth.dto.LoginRequest;
import mn.innex.stay.user.auth.dto.OtpChallengeResponse;
import mn.innex.stay.user.auth.dto.OtpRequest;
import mn.innex.stay.user.auth.dto.OtpVerifyRequest;
import mn.innex.stay.user.auth.dto.RegisterRequest;
import mn.innex.stay.user.auth.dto.ResetPasswordRequest;
import mn.innex.stay.user.domain.Role;
import mn.innex.stay.user.domain.User;
import mn.innex.stay.user.domain.UserStatus;
import mn.innex.stay.user.repo.UserRepository;
import mn.innex.stay.user.web.dto.UserResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The four ways into the platform: phone OTP (primary), email + password
 * (secondary), refresh-token rotation, and password reset.
 *
 * <p>Two rules run through all of them. Accounts are created lazily by the OTP
 * flow, so a first-time guest and a returning one hit the same endpoint. And no
 * response distinguishes "no such account" from "wrong secret", so the API cannot
 * be used to discover who has an account here.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final OtpService otpService;
    private final RefreshTokenService refreshTokenService;
    private final TokenService tokenService;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public AuthService(UserRepository userRepository, OtpService otpService,
                       RefreshTokenService refreshTokenService, TokenService tokenService,
                       PasswordEncoder passwordEncoder, AuditService auditService) {
        this.userRepository = userRepository;
        this.otpService = otpService;
        this.refreshTokenService = refreshTokenService;
        this.tokenService = tokenService;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    /**
     * Sends a login code, creating the account if this number has never been seen.
     * Registration and sign-in are the same call on the phone-first path.
     */
    @Transactional
    public OtpChallengeResponse requestLoginOtp(OtpRequest request, String ip) {
        String phone = PhoneNumbers.normalize(request.phone());
        User user = userRepository.findByPhone(phone).orElse(null);

        if (user == null) {
            user = userRepository.save(User.createWithPhone(phone, request.locale()));
            auditService.record(user.getId(), AuditAction.USER_REGISTERED, "User", user.getId(),
                    Map.of("via", "otp", "phone", PhoneNumbers.mask(phone)), ip);
            log.debug("Created account for new phone {}", PhoneNumbers.mask(phone));
        } else if (!user.getStatus().canAuthenticate()) {
            throw ApiException.forbidden("account_not_active", "This account cannot sign in");
        }

        Duration cooldown = otpService.issue(phone, OtpPurpose.LOGIN);
        return new OtpChallengeResponse("A verification code has been sent.", cooldown.toSeconds());
    }

    /** Exchanges a login code for a session, activating the account on first use. */
    @Transactional
    public AuthResponse verifyLoginOtp(OtpVerifyRequest request, String ip) {
        String phone = PhoneNumbers.normalize(request.phone());
        // A missing account is reported as an expired code: the caller learns nothing
        // about which numbers are registered.
        User user = userRepository.findByPhone(phone).orElseThrow(
                () -> ApiException.badRequest("otp_expired", "The code has expired. Request a new one."));

        if (!user.getStatus().canAuthenticate()) {
            throw ApiException.forbidden("account_not_active", "This account cannot sign in");
        }

        otpService.verify(phone, OtpPurpose.LOGIN, request.code());

        boolean firstVerification = user.getPhoneVerifiedAt() == null;
        user.markPhoneVerified();
        // Everyone is a guest first; host roles are granted separately on approval.
        boolean grantedClient = user.grantRole(Role.CLIENT, null, null);
        user.recordLogin();
        userRepository.save(user);

        if (firstVerification) {
            auditService.record(user.getId(), AuditAction.USER_PHONE_VERIFIED, "User", user.getId(),
                    Map.of("grantedClientRole", grantedClient), ip);
        }
        return startSession(user, request.deviceLabel(), ip, "otp");
    }

    /**
     * Email + password registration. Returns a code challenge rather than a
     * session: the phone number still has to be verified before first sign-in.
     */
    @Transactional
    public OtpChallengeResponse register(RegisterRequest request, String ip) {
        String phone = PhoneNumbers.normalize(request.phone());
        String email = Emails.normalize(request.email());

        if (userRepository.existsByPhone(phone)) {
            throw ApiException.conflict("phone_taken",
                    "An account already exists for this phone number. Sign in instead.");
        }
        if (email != null && userRepository.existsByEmail(email)) {
            throw ApiException.conflict("email_taken", "This email is already in use.");
        }

        User user = User.createWithPhone(phone, request.locale());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFullName(request.fullName());
        userRepository.save(user);

        auditService.record(user.getId(), AuditAction.USER_REGISTERED, "User", user.getId(),
                Map.of("via", "email", "phone", PhoneNumbers.mask(phone)), ip);

        Duration cooldown = otpService.issue(phone, OtpPurpose.LOGIN);
        return new OtpChallengeResponse(
                "Account created. A verification code has been sent to your phone.", cooldown.toSeconds());
    }

    @Transactional
    public AuthResponse login(LoginRequest request, String ip) {
        String email = Emails.normalize(request.email());
        Optional<User> found = email == null ? Optional.empty() : userRepository.findByEmail(email);

        // Same error whether the email is unknown, the account has no password, or
        // the password is wrong.
        User user = found.filter(candidate -> candidate.hasPassword())
                .filter(candidate -> passwordEncoder.matches(request.password(), candidate.getPasswordHash()))
                .orElseThrow(() -> ApiException.unauthorized("invalid_credentials",
                        "Email or password is incorrect"));

        if (user.getStatus() == UserStatus.PENDING_VERIFICATION) {
            throw ApiException.forbidden("phone_not_verified",
                    "Verify your phone number before signing in");
        }
        if (!user.getStatus().canAuthenticate()) {
            throw ApiException.forbidden("account_not_active", "This account cannot sign in");
        }

        user.recordLogin();
        userRepository.save(user);
        return startSession(user, request.deviceLabel(), ip, "password");
    }

    /** Rotates the refresh token and mints a matching access token. */
    @Transactional
    public AuthResponse refresh(String rawRefreshToken, String ip) {
        RefreshTokenService.Rotation rotation = refreshTokenService.rotate(rawRefreshToken, ip);
        User user = userRepository.findByIdWithRoles(rotation.user().getId())
                .orElseThrow(() -> ApiException.unauthorized("refresh_token_invalid",
                        "Refresh token is not recognized"));

        TokenService.AccessToken accessToken = tokenService.issueAccessToken(user);
        auditService.record(user.getId(), AuditAction.TOKEN_REFRESHED, "User", user.getId(), Map.of(), ip);
        return AuthResponse.of(accessToken.value(), accessToken.expiresInSeconds(),
                rotation.refreshToken(), UserResponse.from(user));
    }

    /** Ends one session. Idempotent: an unknown or already-revoked token is a success. */
    @Transactional
    public void logout(String rawRefreshToken, java.util.UUID actorId, String ip) {
        refreshTokenService.revoke(rawRefreshToken);
        if (actorId != null) {
            auditService.record(actorId, AuditAction.USER_LOGGED_OUT, "User", actorId, Map.of(), ip);
        }
    }

    /**
     * Starts a password reset. The response is identical whether or not the number
     * has an account, so this cannot be used to test for membership.
     */
    @Transactional
    public OtpChallengeResponse forgotPassword(ForgotPasswordRequest request, String ip) {
        String phone = PhoneNumbers.normalize(request.phone());
        Optional<User> user = userRepository.findByPhone(phone);

        long cooldownSeconds = 60;
        if (user.isPresent() && user.get().getStatus().canAuthenticate()) {
            cooldownSeconds = otpService.issue(phone, OtpPurpose.PASSWORD_RESET).toSeconds();
        } else {
            log.debug("Password reset requested for unusable phone {}", PhoneNumbers.mask(phone));
        }
        return new OtpChallengeResponse(
                "If an account exists for this number, a reset code has been sent.", cooldownSeconds);
    }

    /** Sets a new password and signs every device out, since the old one may be known. */
    @Transactional
    public void resetPassword(ResetPasswordRequest request, String ip) {
        String phone = PhoneNumbers.normalize(request.phone());
        User user = userRepository.findByPhone(phone).orElseThrow(
                () -> ApiException.badRequest("otp_expired", "The code has expired. Request a new one."));

        otpService.verify(phone, OtpPurpose.PASSWORD_RESET, request.code());

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.markPhoneVerified();
        userRepository.save(user);

        int revoked = refreshTokenService.revokeAllForUser(user.getId());
        auditService.record(user.getId(), AuditAction.USER_PASSWORD_RESET, "User", user.getId(),
                Map.of("revokedSessions", revoked), ip);
    }

    private AuthResponse startSession(User user, String deviceLabel, String ip, String method) {
        TokenService.AccessToken accessToken = tokenService.issueAccessToken(user);
        String refreshToken = refreshTokenService.issue(user, deviceLabel, ip);
        auditService.record(user.getId(), AuditAction.USER_LOGGED_IN, "User", user.getId(),
                Map.of("method", method), ip);
        return AuthResponse.of(accessToken.value(), accessToken.expiresInSeconds(),
                refreshToken, UserResponse.from(user));
    }
}
