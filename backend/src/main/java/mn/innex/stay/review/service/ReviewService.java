package mn.innex.stay.review.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import mn.innex.stay.booking.domain.Booking;
import mn.innex.stay.booking.domain.BookingStatus;
import mn.innex.stay.booking.repo.BookingRepository;
import mn.innex.stay.common.ApiException;
import mn.innex.stay.common.PlatformTime;
import mn.innex.stay.common.audit.AuditAction;
import mn.innex.stay.common.audit.AuditService;
import mn.innex.stay.common.supply.SupplyKind;
import mn.innex.stay.hotel.repo.HotelRepository;
import mn.innex.stay.listing.repo.PropertyRepository;
import mn.innex.stay.review.domain.Review;
import mn.innex.stay.review.domain.ReviewStatus;
import mn.innex.stay.review.domain.ReviewSubject;
import mn.innex.stay.review.repo.ReviewRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writing, publishing and moderating reviews.
 *
 * <p>Three rules do the work. Only someone who actually stayed may write one, so
 * reviews cannot be bought or invented. Neither side sees the other's until both
 * have written or the window closes, so nobody writes in fear of retaliation.
 * And the window closes at all, so one side's silence cannot bury the other's
 * review for ever.
 */
@Service
public class ReviewService {

    private static final Logger log = LoggerFactory.getLogger(ReviewService.class);

    /** How long after checkout a review may still be written. */
    private static final Duration WRITE_WINDOW = Duration.ofDays(14);

    /** Stays that actually happened. Nothing else can be reviewed. */
    private static final List<BookingStatus> REVIEWABLE = List.of(
            BookingStatus.CHECKED_OUT, BookingStatus.COMPLETED);

    private final ReviewRepository reviews;
    private final BookingRepository bookings;
    private final PropertyRepository properties;
    private final HotelRepository hotels;
    private final AuditService auditService;

    public ReviewService(ReviewRepository reviews, BookingRepository bookings,
                         PropertyRepository properties, HotelRepository hotels,
                         AuditService auditService) {
        this.reviews = reviews;
        this.bookings = bookings;
        this.properties = properties;
        this.hotels = hotels;
        this.auditService = auditService;
    }

    /**
     * Writes one side of a stay's reviews.
     *
     * <p>Whether this is a guest reviewing the place or a host reviewing the
     * guest is decided by who is asking, not by what they send: a guest cannot
     * file a review of themselves.
     *
     * @throws ApiException 404 when the stay is not theirs, 409 when it is too
     *                      early, too late, or already written
     */
    @Transactional
    public Review write(UUID authorId, UUID bookingId, int rating, Map<String, Integer> subRatings,
                        String comment, String ip) {
        Booking booking = bookings.findByIdWithDetails(bookingId)
                .orElseThrow(() -> ApiException.notFound("booking_not_found", "No such booking"));

        boolean isGuest = booking.getGuest().getId().equals(authorId);
        boolean isHost = booking.getHost().getId().equals(authorId);
        if (!isGuest && !isHost) {
            throw ApiException.notFound("booking_not_found", "No such booking");
        }
        if (!REVIEWABLE.contains(booking.getStatus())) {
            throw ApiException.conflict("stay_not_finished",
                    "A stay can only be reviewed once it is over");
        }
        if (PlatformTime.today().isAfter(
                booking.getCheckOut().plusDays(WRITE_WINDOW.toDays()))) {
            throw ApiException.conflict("review_window_closed",
                    "Reviews close " + WRITE_WINDOW.toDays() + " days after checkout");
        }
        if (reviews.findByBookingIdAndAuthorId(bookingId, authorId).isPresent()) {
            throw ApiException.conflict("already_reviewed", "You have already reviewed this stay");
        }
        if (rating < 1 || rating > 5) {
            throw ApiException.badRequest("invalid_rating", "A rating is 1 to 5");
        }

        // A guest reviews the place; a host reviews the person. Which supply the
        // review attaches to only matters for the first.
        SupplyKind kind = null;
        UUID supplyId = null;
        if (isGuest) {
            kind = booking.isHotelStay() ? SupplyKind.HOTEL : SupplyKind.PROPERTY;
            supplyId = booking.isHotelStay()
                    ? booking.getRoomType().getHotel().getId()
                    : booking.getProperty().getId();
        }

        Review review = reviews.save(new Review(booking, isGuest ? booking.getGuest() : booking.getHost(),
                isGuest ? ReviewSubject.SUPPLY : ReviewSubject.GUEST, kind, supplyId, rating,
                subRatings, comment));

        // Both sides have now written, so both become readable at once.
        List<Review> counterparts = reviews.findCounterparts(bookingId, authorId);
        if (!counterparts.isEmpty()) {
            publish(review);
            counterparts.forEach(this::publish);
        }

        auditService.record(authorId, AuditAction.REVIEW_WRITTEN, "Booking", bookingId,
                Map.of("rating", String.valueOf(rating),
                       "subject", review.getSubject().name()), ip);
        return review;
    }

    /**
     * Publishes reviews whose blind period has run out.
     *
     * @return how many were published
     */
    @Transactional
    public int publishExpired() {
        List<Review> due = reviews.findPublishable(
                Instant.now().minus(WRITE_WINDOW));
        due.forEach(this::publish);
        if (!due.isEmpty()) {
            log.info("Published {} review(s) whose window closed", due.size());
        }
        return due.size();
    }

    /** The reviewed side's reply, allowed once the review is readable. */
    @Transactional
    public Review respond(UUID actorId, UUID reviewId, String text, String ip) {
        Review review = reviews.findByIdWithDetails(reviewId)
                .orElseThrow(() -> ApiException.notFound("review_not_found", "No such review"));

        Booking booking = review.getBooking();
        // Only the side being reviewed may reply: the host answers a review of
        // their place, the guest answers a review of themselves.
        UUID subjectId = review.getSubject() == ReviewSubject.SUPPLY
                ? booking.getHost().getId()
                : booking.getGuest().getId();
        if (!subjectId.equals(actorId)) {
            throw ApiException.forbidden("not_your_review", "This review is not about you");
        }
        if (!review.isReadable()) {
            // Covers both "still blind" and "a moderator took it down"; replying
            // under a hidden review would publish the accusation by other means.
            throw ApiException.conflict("review_not_published",
                    "You can reply once the review is published");
        }
        if (review.getResponse() != null) {
            throw ApiException.conflict("already_responded", "You have already replied");
        }

        review.respond(text);
        reviews.save(review);
        auditService.record(actorId, AuditAction.REVIEW_RESPONDED, "Review", reviewId,
                Map.of(), ip);
        return review;
    }

    @Transactional(readOnly = true)
    public Page<Review> forSupply(UUID supplyId, Pageable pageable) {
        return reviews.findVisibleForSupply(supplyId, pageable);
    }

    @Transactional(readOnly = true)
    public List<Review> forBooking(UUID bookingId) {
        return reviews.findByBookingId(bookingId);
    }

    @Transactional(readOnly = true)
    public Page<Review> moderationQueue(List<ReviewStatus> statuses, Pageable pageable) {
        return reviews.findByStatusIn(
                statuses == null || statuses.isEmpty() ? List.of(ReviewStatus.values()) : statuses,
                pageable);
    }

    /** Takes a review down, or puts it back. Either way the average is redone. */
    @Transactional
    public Review moderate(UUID adminId, UUID reviewId, boolean hide, String reason, String ip) {
        Review review = reviews.findByIdWithDetails(reviewId)
                .orElseThrow(() -> ApiException.notFound("review_not_found", "No such review"));
        if (hide && (reason == null || reason.isBlank())) {
            throw ApiException.badRequest("reason_required",
                    "Say why, so the author can be told");
        }

        if (hide) {
            review.hide(reason);
        } else {
            review.restore();
        }
        reviews.save(review);
        refreshRating(review);

        auditService.record(adminId, AuditAction.REVIEW_MODERATED, "Review", reviewId,
                Map.of("hidden", String.valueOf(hide), "reason", reason == null ? "" : reason), ip);
        return review;
    }

    private void publish(Review review) {
        review.publish();
        reviews.save(review);
        refreshRating(review);
    }

    /**
     * Recomputes the listing's cached average from scratch.
     *
     * <p>Recomputed rather than nudged: an average adjusted incrementally drifts
     * over time, and hiding a review has to remove it from the figure — which an
     * increment cannot do.
     */
    private void refreshRating(Review review) {
        if (review.getSupplyId() == null) {
            return;
        }
        Object[] row = reviews.ratingFor(review.getSupplyId()).get(0);
        long count = ((Number) row[1]).longValue();
        BigDecimal average = count == 0 ? null
                : BigDecimal.valueOf(((Number) row[0]).doubleValue())
                        .setScale(2, RoundingMode.HALF_UP);

        if (review.getSupplyKind() == SupplyKind.HOTEL) {
            hotels.updateRating(review.getSupplyId(), average, (int) count);
        } else {
            properties.updateRating(review.getSupplyId(), average, (int) count);
        }
    }
}
