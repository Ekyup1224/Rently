package mn.innex.stay.common;

import org.springframework.http.HttpStatus;

/**
 * Normalizes user-typed phone numbers to E.164, defaulting to Mongolia (+976)
 * when no country code is given. Every phone number that reaches the database
 * has passed through {@link #normalize(String)}.
 */
public final class PhoneNumbers {

    /** Mongolian mobile and landline subscriber numbers are 8 digits. */
    private static final int MN_SUBSCRIBER_LENGTH = 8;
    private static final String MN_COUNTRY_CODE = "976";

    private PhoneNumbers() {
    }

    /**
     * @return the number in E.164 form, e.g. {@code +97699112233}
     * @throws ApiException with code {@code invalid_phone} if it cannot be normalized
     */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            throw invalid(raw);
        }
        String digits = raw.replaceAll("[^0-9+]", "");
        if (digits.startsWith("00")) {
            digits = "+" + digits.substring(2);
        }
        if (digits.startsWith("+")) {
            String body = digits.substring(1).replaceAll("[^0-9]", "");
            if (body.length() < 7 || body.length() > 15 || body.startsWith("0")) {
                throw invalid(raw);
            }
            return "+" + body;
        }
        // No country code: an 8-digit local number is Mongolian, anything else is a typo.
        String body = digits.replaceAll("[^0-9]", "");
        if (body.startsWith(MN_COUNTRY_CODE) && body.length() == MN_COUNTRY_CODE.length() + MN_SUBSCRIBER_LENGTH) {
            return "+" + body;
        }
        if (body.length() == MN_SUBSCRIBER_LENGTH) {
            return "+" + MN_COUNTRY_CODE + body;
        }
        throw invalid(raw);
    }

    /** Masks all but the last two digits, for logs and audit metadata. */
    public static String mask(String e164) {
        if (e164 == null || e164.length() < 4) {
            return "***";
        }
        return e164.substring(0, 4) + "*".repeat(e164.length() - 6) + e164.substring(e164.length() - 2);
    }

    private static ApiException invalid(String raw) {
        return new ApiException(HttpStatus.BAD_REQUEST, "invalid_phone",
                "Phone number is not a valid E.164 or 8-digit Mongolian number: " + raw);
    }
}
