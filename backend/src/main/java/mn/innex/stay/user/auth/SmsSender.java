package mn.innex.stay.user.auth;

/**
 * Outbound SMS. Implemented by {@link LoggingSmsSender} for development; a
 * carrier-backed implementation (Mobicom / Unitel / Skytel or an aggregator)
 * replaces it by declaring itself the primary bean once a contract exists.
 */
public interface SmsSender {

    void send(String e164Phone, String message);
}
