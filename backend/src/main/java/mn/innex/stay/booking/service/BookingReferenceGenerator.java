package mn.innex.stay.booking.service;

import java.security.SecureRandom;

import mn.innex.stay.booking.repo.BookingRepository;
import org.springframework.stereotype.Component;

/**
 * Generates the short code guests and support staff quote at each other.
 *
 * <p>Uses a Crockford-style alphabet with I, L, O and U removed: nothing here
 * should be misread over a phone line or turn into an unfortunate word. Codes are
 * random rather than sequential so they reveal nothing about booking volume.
 */
@Component
public class BookingReferenceGenerator {

    private static final char[] ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final int CODE_LENGTH = 6;
    private static final String PREFIX = "SB-";
    private static final int MAX_ATTEMPTS = 10;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final BookingRepository bookingRepository;

    public BookingReferenceGenerator(BookingRepository bookingRepository) {
        this.bookingRepository = bookingRepository;
    }

    /**
     * @return an unused reference such as {@code SB-7KQ2M9}
     * @throws IllegalStateException if the space is so crowded that ten tries all
     *                               collided, which means the code length needs raising
     */
    public String next() {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String candidate = generate();
            if (!bookingRepository.existsByReference(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException(
                "Could not generate a unique booking reference in " + MAX_ATTEMPTS + " attempts");
    }

    private String generate() {
        StringBuilder code = new StringBuilder(PREFIX);
        for (int index = 0; index < CODE_LENGTH; index++) {
            code.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
        }
        return code.toString();
    }
}
