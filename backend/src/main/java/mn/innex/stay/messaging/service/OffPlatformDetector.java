package mn.innex.stay.messaging.service;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * Spots a host trying to move payment off the platform.
 *
 * <p>This is the bypass that defeats everything else. Payouts are held until a
 * guest checks in, so a fraudulent listing earns nothing — unless the host can
 * persuade the guest to pay them directly, at which point no hold, refund or
 * flag protects anybody. It is also the single most common scam on every
 * accommodation marketplace.
 *
 * <p>Matching is deliberately crude and deliberately non-blocking. It catches
 * the obvious attempt in Mongolian and English — an account number, a bank name
 * next to a request to transfer, an offer of a discount for paying outside —
 * and raises a flag a person then reads. Anything cleverer would need to
 * understand intent, and anything that blocked messages outright would break the
 * many honest conversations that mention money.
 */
@Component
public class OffPlatformDetector {

    /**
     * Mongolian bank account numbers run to 8–14 digits, often spaced or dashed;
     * ten is the most common length by far. Eight is the floor because a run of
     * digits that short is as likely to be a price or a date, and this only ever
     * fires with a bank word beside it anyway.
     */
    private static final Pattern ACCOUNT_NUMBER =
            Pattern.compile("\\b\\d[\\d\\s-]{6,16}\\d\\b");

    /**
     * Named because seeing one of these beside a transfer request is the signal.
     *
     * <p>Matched against the text with its spaces removed, since "Хаан банк" and
     * "Хаанбанк" are both ordinary spellings and a scammer writes whichever.
     */
    private static final List<String> BANKS = List.of(
            "khanbank", "хаанбанк", "golomt", "голомт", "tdb", "худалдаахөгжил",
            "statebank", "төрийнбанк", "xacbank", "хасбанк", "iban", "swift");

    private static final List<String> TRANSFER_WORDS = List.of(
            "transfer", "bank account", "account number", "wire", "shilzhuuleh",
            "шилжүүл", "данс", "дансны дугаар", "мөнгө явуул");

    /** Asking to leave the platform, usually sweetened. */
    private static final List<String> OFF_PLATFORM = List.of(
            "outside the app", "outside the platform", "off the app", "cash only",
            "pay me directly", "pay directly", "cheaper if you", "without the platform",
            "skip the site", "skip the platform", "skip the app",
            "аппаас гадуур", "шууд төл", "бэлнээр", "платформоос гадуур");

    /**
     * Words that turn a mention of money into a redirection of it.
     *
     * <p>"My bank account" is something a host might legitimately mention; "send
     * it to my bank account directly" is not. The difference is only ever one of
     * these words, which is why they are matched beside a transfer word rather
     * than on their own — "шууд" alone means "straight away" as often as it
     * means "go around the platform".
     */
    private static final List<String> DIRECTNESS = List.of(
            "directly", "direct to", "instead of", "skip", "шууд", "гадуур");

    /**
     * @return why this message looks like an off-platform payment request, or
     *         empty when nothing stands out
     */
    public Optional<String> inspect(String body) {
        if (body == null || body.isBlank()) {
            return Optional.empty();
        }
        String text = body.toLowerCase();
        String compact = text.replaceAll("\\s+", "");

        if (containsAny(text, OFF_PLATFORM)) {
            return Optional.of("off_platform_request");
        }
        // An account number alone is weak; with a bank or a transfer word beside
        // it, it is somebody being asked to pay outside.
        boolean named = containsAny(compact, BANKS);
        boolean asked = containsAny(text, TRANSFER_WORDS);
        if ((named || asked) && ACCOUNT_NUMBER.matcher(body).find()) {
            return Optional.of("bank_details_shared");
        }
        if (named && asked) {
            return Optional.of("payment_details_requested");
        }
        // No number and no bank named, but being pointed somewhere other than
        // the checkout is the request itself.
        if (asked && containsAny(text, DIRECTNESS)) {
            return Optional.of("payment_details_requested");
        }
        return Optional.empty();
    }

    private static boolean containsAny(String text, List<String> needles) {
        return needles.stream().anyMatch(text::contains);
    }
}
