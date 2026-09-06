package mn.innex.stay.common.web;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Resolves the caller's IP for audit rows and rate limiting.
 *
 * <p>Trusts {@code X-Forwarded-For} because the deployment target is a single app
 * behind Nginx. If the app is ever exposed directly, this becomes spoofable and
 * must be replaced with Spring's {@code ForwardedHeaderFilter} plus a trusted
 * proxy list.
 */
public final class ClientIp {

    private ClientIp() {
    }

    public static String of(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String first = forwarded.split(",")[0].trim();
            if (!first.isBlank()) {
                return truncate(first);
            }
        }
        return truncate(request.getRemoteAddr());
    }

    private static String truncate(String ip) {
        return ip == null || ip.length() <= 64 ? ip : ip.substring(0, 64);
    }
}
