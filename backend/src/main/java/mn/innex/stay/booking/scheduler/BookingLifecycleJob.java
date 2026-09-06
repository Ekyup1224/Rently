package mn.innex.stay.booking.scheduler;

import java.time.Instant;
import java.util.List;

import mn.innex.stay.booking.domain.Booking;
import mn.innex.stay.booking.domain.BookingStatus;
import mn.innex.stay.booking.repo.BookingRepository;
import mn.innex.stay.booking.service.BookingService;
import mn.innex.stay.common.PlatformTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Moves bookings along when nobody acts.
 *
 * <p>Two deadlines matter. An unanswered request-to-book and an unpaid hold both
 * sit on dates that other guests cannot book, so releasing them promptly is the
 * difference between a calendar that works and one clogged with abandoned
 * checkouts. And a stay that has ended needs to become COMPLETED for payouts and
 * reviews to have something to hang off.
 *
 * <p>Each booking is advanced in its own transaction, so one bad row cannot stop
 * the sweep.
 *
 * <p>Single-instance assumption: with more than one app instance these jobs would
 * run concurrently. The work is idempotent, so the worst case is wasted queries
 * rather than wrong state, but a scheduler lock belongs here before scaling out.
 */
@Component
public class BookingLifecycleJob {

    private static final Logger log = LoggerFactory.getLogger(BookingLifecycleJob.class);

    private static final List<BookingStatus> PENDING_WITH_DEADLINE = List.of(
            BookingStatus.PENDING_HOST_APPROVAL, BookingStatus.PENDING_PAYMENT);

    private static final List<BookingStatus> STAYED = List.of(
            BookingStatus.CONFIRMED, BookingStatus.CHECKED_IN, BookingStatus.CHECKED_OUT);

    private final BookingRepository bookingRepository;
    private final BookingService bookingService;

    public BookingLifecycleJob(BookingRepository bookingRepository, BookingService bookingService) {
        this.bookingRepository = bookingRepository;
        this.bookingService = bookingService;
    }

    /**
     * Releases dates held by bookings whose deadline has passed. Runs often, since
     * every minute of delay is a minute a listing is unbookable for no reason.
     */
    @Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT30S")
    @Transactional(readOnly = true)
    public void expireOverdueBookings() {
        List<Booking> overdue = bookingRepository.findExpired(PENDING_WITH_DEADLINE, Instant.now());
        if (overdue.isEmpty()) {
            return;
        }

        int expired = 0;
        for (Booking booking : overdue) {
            try {
                bookingService.expire(booking.getId());
                expired++;
            } catch (RuntimeException ex) {
                // One unexpirable booking must not stall the rest of the sweep.
                log.error("Could not expire booking {}", booking.getReference(), ex);
            }
        }
        log.info("Expired {} of {} overdue booking(s)", expired, overdue.size());
    }

    /**
     * Marks finished stays complete. Runs nightly rather than continuously: nothing
     * depends on it happening at a particular minute.
     */
    @Scheduled(cron = "0 15 3 * * *", zone = "Asia/Ulaanbaatar")
    @Transactional(readOnly = true)
    public void completeFinishedStays() {
        List<Booking> finished = bookingRepository.findCompletable(STAYED, PlatformTime.today());
        if (finished.isEmpty()) {
            return;
        }

        int completed = 0;
        for (Booking booking : finished) {
            try {
                bookingService.complete(booking.getId());
                completed++;
            } catch (RuntimeException ex) {
                log.error("Could not complete booking {}", booking.getReference(), ex);
            }
        }
        log.info("Completed {} of {} finished stay(s)", completed, finished.size());
    }
}
