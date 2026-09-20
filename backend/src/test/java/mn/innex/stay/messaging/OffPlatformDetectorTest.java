package mn.innex.stay.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import mn.innex.stay.messaging.service.OffPlatformDetector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The line between a host arranging a stay and a host arranging to be paid
 * outside it.
 *
 * <p>Both halves matter equally. Missing a real attempt costs a guest their money
 * with no way to get it back; flagging an ordinary message teaches hosts the
 * platform is broken and pushes them off it — which is the thing this is meant
 * to prevent.
 */
class OffPlatformDetectorTest {

    private final OffPlatformDetector detector = new OffPlatformDetector();

    @ParameterizedTest
    @DisplayName("asks to be paid directly are caught, in either language")
    @ValueSource(strings = {
            "Хаанбанк 5301234567 руу шилжүүлээрэй",
            "Дансны дугаар 401 900 1234, Голомт банк",
            "Please transfer to my Khan Bank account 5301234567",
            "Just send the money to my bank account directly and skip the site",
            "Сайтаар биш, шууд надад бэлнээр өгөөрэй",
            "cheaper if you pay me cash outside the platform",
    })
    void catchesPaymentRedirection(String body) {
        assertThat(detector.inspect(body)).isPresent();
    }

    @ParameterizedTest
    @DisplayName("ordinary arrangements are left alone")
    @ValueSource(strings = {
            "Сайн байна уу, 5 цагт ирж болох уу?",
            "Hi! We will arrive around 6pm, is that alright?",
            "The gate code is 4471, the key is with the neighbour",
            "Манай байшин 4 давхарт, 12 тоот",
            "Breakfast is included, it is served until 10",
            "Thanks, the stay was great — 5 stars from us",
    })
    void leavesNormalConversationAlone(String body) {
        assertThat(detector.inspect(body)).isEmpty();
    }

    @Test
    @DisplayName("a flag says what was matched, so a moderator need not guess")
    void reasonIsSpecific() {
        assertThat(detector.inspect("Дансны дугаар 5301234567 руу шилжүүлнэ үү"))
                .get()
                .asString()
                .isNotBlank();
    }

    @Test
    @DisplayName("an empty message is not an accusation")
    void ignoresEmptyBodies() {
        assertThat(detector.inspect("")).isEmpty();
        assertThat(detector.inspect(null)).isEmpty();
    }
}
