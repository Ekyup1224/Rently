package mn.innex.stay.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class PhoneNumbersTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "99112233,        +97699112233",   // bare 8-digit local number
            "9911 2233,       +97699112233",   // spaces
            "9911-2233,       +97699112233",   // dashes
            "97699112233,     +97699112233",   // country code, no plus
            "+97699112233,    +97699112233",   // already E.164
            "+976 9911 2233,  +97699112233",   // E.164 with spaces
            "0097699112233,   +97699112233",   // 00 international prefix
            "+14155552671,    +14155552671",   // foreign number kept as given
            "+1 (415) 555-2671, +14155552671"  // punctuation stripped from an E.164 number
    })
    @DisplayName("normalizes local and international input to E.164")
    void normalizesToE164(String input, String expected) {
        assertThat(PhoneNumbers.normalize(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "1234567",        // 7 digits: too short to be a Mongolian subscriber number
            "991122334",      // 9 digits: not 8, and not a country-code form
            "4155552671",     // 10 digits, no country code: not assumed to be +1
            "+0123456789",    // E.164 country codes never start with 0
            "+1",             // too short
            "+1234567890123456", // more than 15 digits
            "abcdefgh",       // no digits at all
            ""
    })
    @DisplayName("rejects anything that is not a plausible number")
    void rejectsInvalid(String input) {
        assertThatThrownBy(() -> PhoneNumbers.normalize(input))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).getCode())
                .isEqualTo("invalid_phone");
    }

    @Test
    @DisplayName("rejects null")
    void rejectsNull() {
        assertThatThrownBy(() -> PhoneNumbers.normalize(null)).isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("masks all but the country code and last two digits")
    void masksForLogging() {
        assertThat(PhoneNumbers.mask("+97699112233")).isEqualTo("+976******33");
        assertThat(PhoneNumbers.mask("+1")).isEqualTo("***");
        assertThat(PhoneNumbers.mask(null)).isEqualTo("***");
    }
}
