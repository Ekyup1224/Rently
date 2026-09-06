package mn.innex.stay.booking.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;

import mn.innex.stay.listing.domain.CancellationPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The cancellation policies, checked at their boundaries.
 *
 * <p>These are the numbers a guest is promised at booking time and a host budgets
 * against, so the off-by-one at each cutoff is the whole point of the test.
 */
class RefundCalculatorTest {

    private static final LocalDate CHECK_IN = LocalDate.of(2026, 10, 10);
    private static final BigDecimal ACCOMMODATION = new BigDecimal("565000.00");
    private static final BigDecimal SERVICE_FEE = new BigDecimal("16950.00");
    private static final BigDecimal FIRST_NIGHT = new BigDecimal("180000.00");

    private final RefundCalculator calculator = new RefundCalculator();

    private BigDecimal refundOn(CancellationPolicy policy, LocalDate cancelledOn) {
        return calculator.refundFor(policy, CHECK_IN, cancelledOn,
                ACCOMMODATION, SERVICE_FEE, FIRST_NIGHT);
    }

    @Nested
    @DisplayName("FLEXIBLE: full refund until 24h before, then the first night is withheld")
    class Flexible {

        @Test
        void wellBeforeCheckIn() {
            // Full accommodation refund, and the service fee comes back with it.
            assertThat(refundOn(CancellationPolicy.FLEXIBLE, CHECK_IN.minusDays(10)))
                    .isEqualByComparingTo("581950.00");
        }

        @Test
        void exactlyAtTheCutoff() {
            // One day before is still inside the free window.
            assertThat(refundOn(CancellationPolicy.FLEXIBLE, CHECK_IN.minusDays(1)))
                    .isEqualByComparingTo("581950.00");
        }

        @Test
        void onCheckInDay() {
            // Past the cutoff: the first night is kept, and the service fee is not
            // returned because the guest was not made whole on the stay.
            assertThat(refundOn(CancellationPolicy.FLEXIBLE, CHECK_IN))
                    .isEqualByComparingTo("385000.00");
        }
    }

    @Nested
    @DisplayName("MODERATE: full refund until 5 days before, then 50%")
    class Moderate {

        @Test
        void beforeTheCutoff() {
            assertThat(refundOn(CancellationPolicy.MODERATE, CHECK_IN.minusDays(30)))
                    .isEqualByComparingTo("581950.00");
        }

        @Test
        void exactlyFiveDaysBefore() {
            assertThat(refundOn(CancellationPolicy.MODERATE, CHECK_IN.minusDays(5)))
                    .isEqualByComparingTo("581950.00");
        }

        @Test
        void fourDaysBefore() {
            // Half the accommodation, no service fee.
            assertThat(refundOn(CancellationPolicy.MODERATE, CHECK_IN.minusDays(4)))
                    .isEqualByComparingTo("282500.00");
        }
    }

    @Nested
    @DisplayName("STRICT: 50% until 7 days before, then nothing")
    class Strict {

        @Test
        void beforeTheCutoff() {
            assertThat(refundOn(CancellationPolicy.STRICT, CHECK_IN.minusDays(20)))
                    .isEqualByComparingTo("282500.00");
        }

        @Test
        void exactlySevenDaysBefore() {
            assertThat(refundOn(CancellationPolicy.STRICT, CHECK_IN.minusDays(7)))
                    .isEqualByComparingTo("282500.00");
        }

        @Test
        void sixDaysBefore() {
            assertThat(refundOn(CancellationPolicy.STRICT, CHECK_IN.minusDays(6)))
                    .isEqualByComparingTo("0.00");
        }

        @Test
        void afterCheckIn() {
            assertThat(refundOn(CancellationPolicy.STRICT, CHECK_IN.plusDays(1)))
                    .isEqualByComparingTo("0.00");
        }
    }

    @Test
    @DisplayName("a refund never exceeds what was charged, even for a one-night stay")
    void neverExceedsTheCharge() {
        BigDecimal oneNight = new BigDecimal("180000.00");
        BigDecimal refund = calculator.refundFor(CancellationPolicy.FLEXIBLE, CHECK_IN, CHECK_IN,
                oneNight, BigDecimal.ZERO, oneNight);
        // Withholding the only night leaves nothing, not a negative refund.
        assertThat(refund).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("the schedule shown at booking matches what a cancellation actually pays")
    void scheduleMatchesCalculation() {
        var schedule = calculator.scheduleFor(CancellationPolicy.MODERATE, CHECK_IN,
                ACCOMMODATION, SERVICE_FEE, FIRST_NIGHT);

        assertThat(schedule).hasSize(2);
        // The promise made before the cutoff must equal what is paid before it.
        assertThat(schedule.get(0).refundAmount())
                .isEqualByComparingTo(refundOn(CancellationPolicy.MODERATE, CHECK_IN.minusDays(6)));
        assertThat(schedule.get(1).refundAmount())
                .isEqualByComparingTo(refundOn(CancellationPolicy.MODERATE, CHECK_IN));
    }
}
