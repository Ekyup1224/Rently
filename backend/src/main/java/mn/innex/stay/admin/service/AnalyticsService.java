package mn.innex.stay.admin.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import mn.innex.stay.booking.repo.BookingRepository;
import mn.innex.stay.common.ApiException;
import mn.innex.stay.common.Money;
import mn.innex.stay.hotel.repo.HotelRepository;
import mn.innex.stay.listing.repo.PropertyRepository;
import mn.innex.stay.payment.repo.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The numbers a platform owner actually runs the business on.
 *
 * <p>Four of them, per the spec: what guests spent, how many stays that was, what
 * the platform kept, and how much supply is live. Deliberately not a wall of
 * metrics — figures nobody acts on cost the same to maintain as ones that matter,
 * and mislead when they go stale.
 *
 * <p>Everything is derived. There is no rollup table to drift out of sync with
 * the bookings and payments it came from; if that becomes slow, the answer is a
 * materialized view with a known refresh time, not a second copy of the truth.
 */
@Service
public class AnalyticsService {

    private static final ZoneId ULAANBAATAR = ZoneId.of("Asia/Ulaanbaatar");
    private static final int MAX_RANGE_DAYS = 400;

    private final PaymentRepository payments;
    private final BookingRepository bookings;
    private final PropertyRepository properties;
    private final HotelRepository hotels;

    public AnalyticsService(PaymentRepository payments, BookingRepository bookings,
                            PropertyRepository properties, HotelRepository hotels) {
        this.payments = payments;
        this.bookings = bookings;
        this.properties = properties;
        this.hotels = hotels;
    }

    @Transactional(readOnly = true)
    public Overview overview(LocalDate from, LocalDate to) {
        assertSaneRange(from, to);
        Instant start = from.atStartOfDay(ULAANBAATAR).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(ULAANBAATAR).toInstant();

        Object[] settled = payments.sumSettled(start, end).get(0);
        BigDecimal charged = Money.of((BigDecimal) settled[0]);
        BigDecimal refunded = Money.of((BigDecimal) settled[1]);
        // What guests actually spent: charges less anything given back.
        BigDecimal grossValue = Money.of(charged.subtract(refunded));

        Object[] booked = bookings.sumCommissionAndCount(start, end).get(0);
        BigDecimal commission = Money.of((BigDecimal) booked[0]);
        long bookingCount = ((Number) booked[1]).longValue();
        long cancelled = ((Number) booked[2]).longValue();

        // Take rate against gross value, because that is the money that moved.
        BigDecimal takeRate = Money.isPositive(grossValue)
                ? commission.multiply(BigDecimal.valueOf(100))
                        .divide(grossValue, 2, RoundingMode.HALF_UP)
                : Money.ZERO;

        BigDecimal cancellationRate = bookingCount + cancelled == 0
                ? Money.ZERO
                : BigDecimal.valueOf(cancelled * 100L)
                        .divide(BigDecimal.valueOf(bookingCount + cancelled), 2,
                                RoundingMode.HALF_UP);

        return new Overview(from, to, grossValue, refunded, commission, takeRate,
                bookingCount, cancelled, cancellationRate,
                ((Number) settled[2]).longValue(),
                properties.countLiveListings(), hotels.countLiveHotels(), "MNT");
    }

    /**
     * The same story day by day, for a chart.
     *
     * @param interval {@code day} or {@code week}; anything finer is noise at this
     *                 volume and anything coarser hides a bad week
     */
    @Transactional(readOnly = true)
    public List<Point> series(LocalDate from, LocalDate to, String interval) {
        assertSaneRange(from, to);
        boolean weekly = "week".equalsIgnoreCase(interval);
        if (!weekly && !"day".equalsIgnoreCase(interval)) {
            throw ApiException.badRequest("invalid_interval", "Interval must be day or week");
        }

        List<Point> points = new java.util.ArrayList<>();
        LocalDate cursor = from;
        while (!cursor.isAfter(to)) {
            LocalDate bucketEnd = weekly ? cursor.plusDays(6) : cursor;
            LocalDate clamped = bucketEnd.isAfter(to) ? to : bucketEnd;

            Instant start = cursor.atStartOfDay(ULAANBAATAR).toInstant();
            Instant end = clamped.plusDays(1).atStartOfDay(ULAANBAATAR).toInstant();

            Object[] settled = payments.sumSettled(start, end).get(0);
            Object[] booked = bookings.sumCommissionAndCount(start, end).get(0);
            BigDecimal gross = Money.of(((BigDecimal) settled[0]).subtract((BigDecimal) settled[1]));

            points.add(new Point(cursor, gross, Money.of((BigDecimal) booked[0]),
                    ((Number) booked[1]).longValue()));
            cursor = clamped.plusDays(1);
        }
        return points;
    }

    private void assertSaneRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw ApiException.badRequest("dates_required", "Both from and to are required");
        }
        if (to.isBefore(from)) {
            throw ApiException.badRequest("invalid_date_range", "to must not be before from");
        }
        if (java.time.temporal.ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS) {
            throw ApiException.badRequest("range_too_wide",
                    "A range cannot exceed " + MAX_RANGE_DAYS + " days");
        }
    }

    /**
     * @param grossValue     what guests spent, net of refunds
     * @param commission     what the platform kept from hosts
     * @param takeRatePercent commission as a share of gross value
     */
    public record Overview(
            LocalDate from,
            LocalDate to,
            BigDecimal grossValue,
            BigDecimal refunded,
            BigDecimal commission,
            BigDecimal takeRatePercent,
            long bookings,
            long cancelledBookings,
            BigDecimal cancellationRatePercent,
            long settledCharges,
            long liveListings,
            long liveHotels,
            String currency) {
    }

    public record Point(LocalDate date, BigDecimal grossValue, BigDecimal commission,
                        long bookings) {
    }
}
