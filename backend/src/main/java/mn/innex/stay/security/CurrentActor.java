package mn.innex.stay.security;

import java.util.Optional;
import java.util.UUID;

import mn.innex.stay.common.ApiException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Reads the authenticated user out of the security context. Controllers use this
 * instead of taking a user id from the request, so a caller can never act as
 * someone else by editing a path variable.
 */
public final class CurrentActor {

    private CurrentActor() {
    }

    public static Optional<UUID> userIdOrEmpty() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            return Optional.empty();
        }
        Jwt jwt = jwtAuthentication.getToken();
        try {
            return Optional.of(UUID.fromString(jwt.getSubject()));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    /** @throws ApiException 401 when there is no authenticated user */
    public static UUID requireUserId() {
        return userIdOrEmpty().orElseThrow(
                () -> ApiException.unauthorized("unauthenticated", "Authentication is required"));
    }
}
