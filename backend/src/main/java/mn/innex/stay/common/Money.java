package mn.innex.stay.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Money arithmetic for the platform.
 *
 * <p>Amounts are {@code numeric(14,2)} in the database and {@link BigDecimal} in
 * Java — never {@code double}. MNT's subunit is unused in practice, but the scale
 * keeps a second currency from needing a migration, and exact decimal arithmetic
 * keeps totals reconciling to the tögrög.
 *
 * <p>Every result is normalized to scale 2 with HALF_UP rounding. Where a total is
 * split into parts, compute one part and derive the other by subtraction
 * ({@link #subtract}) rather than rounding both — otherwise the parts can fail to
 * add up to the whole.
 */
public final class Money {

    public static final int SCALE = 2;
    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE, RoundingMode.UNNECESSARY);
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private Money() {
    }

    /** Normalizes any amount to the platform's scale and rounding. */
    public static BigDecimal of(BigDecimal amount) {
        return amount == null ? ZERO : amount.setScale(SCALE, RoundingMode.HALF_UP);
    }

    public static BigDecimal of(long amount) {
        return BigDecimal.valueOf(amount).setScale(SCALE, RoundingMode.UNNECESSARY);
    }

    public static BigDecimal add(BigDecimal... amounts) {
        BigDecimal sum = ZERO;
        for (BigDecimal amount : amounts) {
            sum = sum.add(of(amount));
        }
        return of(sum);
    }

    public static BigDecimal subtract(BigDecimal minuend, BigDecimal subtrahend) {
        return of(of(minuend).subtract(of(subtrahend)));
    }

    public static BigDecimal multiply(BigDecimal amount, long factor) {
        return of(of(amount).multiply(BigDecimal.valueOf(factor)));
    }

    /**
     * Applies a percentage, e.g. a 10.00% commission on a subtotal.
     *
     * @param percent whole percent, so 10.00 means 10%
     */
    public static BigDecimal percentOf(BigDecimal amount, BigDecimal percent) {
        if (percent == null || percent.signum() == 0) {
            return ZERO;
        }
        return of(of(amount).multiply(percent).divide(HUNDRED, SCALE + 4, RoundingMode.HALF_UP));
    }

    public static boolean isZero(BigDecimal amount) {
        return amount == null || amount.signum() == 0;
    }

    public static boolean isPositive(BigDecimal amount) {
        return amount != null && amount.signum() > 0;
    }
}
