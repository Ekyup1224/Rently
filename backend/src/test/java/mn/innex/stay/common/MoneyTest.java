package mn.innex.stay.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MoneyTest {

    @ParameterizedTest(name = "{0}% of {1} = {2}")
    @CsvSource({
            "10,   565000,   56500.00",
            "3,    565000,   16950.00",
            "12.5, 100000,   12500.00",
            "0,    565000,   0.00",
            "10,   0,        0.00",
            // Rounds half-up at the second decimal rather than truncating.
            "33.33, 1000,    333.30",
            "7,     1234.56, 86.42"
    })
    @DisplayName("applies percentages with half-up rounding at two decimals")
    void percentOf(String percent, String amount, String expected) {
        assertThat(Money.percentOf(new BigDecimal(amount), new BigDecimal(percent)))
                .isEqualByComparingTo(expected);
    }

    @Test
    @DisplayName("a commission split always reconciles to the whole")
    void splitReconciles() {
        // The exact reason host payout is derived by subtraction rather than by
        // applying the complementary percentage: rounding both halves can lose or
        // invent a unit, and money that does not add up is a support ticket.
        for (int amount = 1; amount <= 2000; amount++) {
            BigDecimal accommodation = BigDecimal.valueOf(amount).movePointRight(2);
            BigDecimal commission = Money.percentOf(accommodation, new BigDecimal("13.37"));
            BigDecimal payout = Money.subtract(accommodation, commission);

            assertThat(Money.add(commission, payout))
                    .as("commission + payout must equal the accommodation total for %s",
                            accommodation)
                    .isEqualByComparingTo(Money.of(accommodation));
        }
    }

    @Test
    @DisplayName("normalizes scale and treats null as zero")
    void normalizes() {
        assertThat(Money.of(new BigDecimal("180000"))).isEqualByComparingTo("180000.00");
        assertThat(Money.of((BigDecimal) null)).isEqualByComparingTo("0.00");
        assertThat(Money.of(new BigDecimal("1.005"))).isEqualByComparingTo("1.01");
        assertThat(Money.multiply(new BigDecimal("180000"), 3)).isEqualByComparingTo("540000.00");
    }

    @Test
    @DisplayName("addition never drifts across many terms")
    void additionDoesNotDrift() {
        BigDecimal sum = Money.ZERO;
        for (int night = 0; night < 365; night++) {
            sum = Money.add(sum, new BigDecimal("180000.33"));
        }
        assertThat(sum).isEqualByComparingTo("65700120.45");
    }
}
