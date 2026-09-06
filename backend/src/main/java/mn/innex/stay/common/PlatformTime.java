package mn.innex.stay.common;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * The platform's civil calendar.
 *
 * <p>Check-in and check-out are local dates, not instants: a guest arriving on the
 * 10th arrives on the 10th regardless of where the server runs. Mongolia is a
 * single timezone, so one zone constant is enough — when the platform expands, a
 * per-property zone replaces this and every use of {@link #today()} becomes a
 * question of whose day it is.
 */
public final class PlatformTime {

    public static final ZoneId ZONE = ZoneId.of("Asia/Ulaanbaatar");

    private PlatformTime() {
    }

    /** Today in the platform's timezone, which is what "cannot book in the past" means. */
    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }
}
