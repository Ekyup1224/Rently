package mn.innex.stay.booking.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import mn.innex.stay.booking.domain.Booking;
import mn.innex.stay.booking.domain.BookingStatus;
import mn.innex.stay.booking.repo.BookingRepository;
import mn.innex.stay.booking.web.dto.EarningsSummaryResponse;
import mn.innex.stay.common.ApiException;
import mn.innex.stay.common.Money;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What a host has earned, and what is still coming.
 *
 * <p>Reports earnings, not payouts: money is attributed to the period a stay ended
 * in, and payout automation arrives in Step 5. Keeping those separate now avoids a
 * host reading this screen as "paid" and reconciling against their bank.
 *
 * <p>Aggregation happens in Java rather than SQL because a host has tens of
 * bookings, not millions. When that stops being true this becomes a grouped query.
 */
@Service
public class EarningsService {

    private static final List<BookingStatus> EARNED = List.of(
            BookingStatus.CHECKED_OUT, BookingStatus.COMPLETED);

    private static final List<BookingStatus> UPCOMING = List.of(
            BookingStatus.CONFIRMED, BookingStatus.CHECKED_IN);

    /** Guards against a request that would scan a host's entire history. */
    private static final int MAX_RANGE_DAYS = 800;

    private final BookingRepository bookingRepository;

    public EarningsService(BookingRepository bookingRepository) {
        this.bookingRepository = bookingRepository;
    }

    @Transactional(readOnly = true)
    public EarningsSummaryResponse summarize(UUID hostId, LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw ApiException.badRequest("dates_required", "Both from and to are required");
        }
        if (to.isBefore(from)) {
            throw ApiException.badRequest("invalid_date_range", "to must not be before from");
        }
        if (java.time.temporal.ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS) {
            throw ApiException.badRequest("range_too_wide",
                    "An earnings range cannot exceed " + MAX_RANGE_DAYS + " days");
        }

        LocalDate toExclusive = to.plusDays(1);
        List<Booking> completed = bookingRepository
                .findByHostIdAndStatusInAndCheckOutGreaterThanEqualAndCheckOutLessThan(
                        hostId, EARNED, from, toExclusive);
        List<Booking> upcoming = bookingRepository
                .findByHostIdAndStatusInAndCheckOutGreaterThanEqualAndCheckOutLessThan(
                        hostId, UPCOMING, from, toExclusive);

        BigDecimal earned = Money.ZERO;
        BigDecimal commission = Money.ZERO;
        Map<LocalDate, MonthAccumulator> months = new LinkedHashMap<>();

        for (Booking booking : completed) {
            earned = Money.add(earned, booking.getHostPayout());
            commission = Money.add(commission, booking.getHostCommission());
            LocalDate month = booking.getCheckOut().withDayOfMonth(1);
            months.computeIfAbsent(month, key -> new MonthAccumulator())
                    .add(booking.getHostPayout());
        }

        BigDecimal upcomingTotal = Money.ZERO;
        for (Booking booking : upcoming) {
            upcomingTotal = Money.add(upcomingTotal, booking.getHostPayout());
        }

        List<EarningsSummaryResponse.MonthlyEarning> byMonth = new ArrayList<>();
        months.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> byMonth.add(new EarningsSummaryResponse.MonthlyEarning(
                        entry.getKey(), entry.getValue().total, entry.getValue().count)));

        String currency = completed.isEmpty()
                ? (upcoming.isEmpty() ? "MNT" : upcoming.get(0).getCurrency())
                : completed.get(0).getCurrency();

        return new EarningsSummaryResponse(from, to, currency, earned, upcomingTotal, commission,
                completed.size(), upcoming.size(), byMonth);
    }

    private static final class MonthAccumulator {
        private BigDecimal total = Money.ZERO;
        private int count;

        void add(BigDecimal amount) {
            total = Money.add(total, amount);
            count++;
        }
    }
}
