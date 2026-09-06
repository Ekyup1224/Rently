package mn.innex.stay.user.auth.dto;

import mn.innex.stay.user.web.dto.UserResponse;

/**
 * A freshly minted session.
 *
 * @param accessToken      short-lived bearer JWT for the Authorization header
 * @param expiresInSeconds access-token lifetime, so the client can refresh ahead of expiry
 * @param refreshToken     opaque single-use token; store it, send it only to /auth/refresh
 */
public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds,
        String refreshToken,
        UserResponse user) {

    public static AuthResponse of(String accessToken, long expiresInSeconds,
                                  String refreshToken, UserResponse user) {
        return new AuthResponse(accessToken, "Bearer", expiresInSeconds, refreshToken, user);
    }
}
