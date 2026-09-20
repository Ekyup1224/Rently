package mn.innex.stay.review.web;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import mn.innex.stay.common.web.ClientIp;
import mn.innex.stay.common.web.PageResponse;
import mn.innex.stay.review.domain.Review;
import mn.innex.stay.review.service.ReviewService;
import mn.innex.stay.security.CurrentActor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reviews, from both sides of a stay.
 *
 * <p>Reading them is public — they are the main thing a guest weighs when
 * choosing — while writing one requires having been there, which is what keeps
 * them worth reading.
 */
@RestController
@RequestMapping("/api/v1")
public class ReviewController {

    private final ReviewService reviews;

    public ReviewController(ReviewService reviews) {
        this.reviews = reviews;
    }

    /** Anyone may read a listing's reviews, signed in or not. */
    @GetMapping("/listings/{propertyId}/reviews")
    public PageResponse<ReviewView> forListing(@PathVariable UUID propertyId,
                                               @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.of(reviews.forSupply(propertyId, pageable), ReviewView::publicView);
    }

    @GetMapping("/hotels/{hotelId}/reviews")
    public PageResponse<ReviewView> forHotel(@PathVariable UUID hotelId,
                                             @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.of(reviews.forSupply(hotelId, pageable), ReviewView::publicView);
    }

    /**
     * Both reviews of one stay, for the people who were on it.
     *
     * <p>An unpublished review is returned to its own author only — otherwise the
     * blind period would be no period at all.
     */
    @GetMapping("/bookings/{bookingId}/reviews")
    public List<ReviewView> forBooking(@PathVariable UUID bookingId) {
        UUID actorId = CurrentActor.requireUserId();
        return reviews.forBooking(bookingId).stream()
                .filter(review -> review.isReadable()
                        || review.getAuthor().getId().equals(actorId))
                .map(review -> ReviewView.publicView(review))
                .toList();
    }

    @PostMapping("/bookings/{bookingId}/review")
    @ResponseStatus(HttpStatus.CREATED)
    public ReviewView write(@PathVariable UUID bookingId,
                            @Valid @RequestBody WriteReview request,
                            HttpServletRequest httpRequest) {
        Review review = reviews.write(CurrentActor.requireUserId(), bookingId, request.rating(),
                request.subRatings(), request.comment(), ClientIp.of(httpRequest));
        return ReviewView.publicView(review);
    }

    /** The reviewed side's single reply. */
    @PostMapping("/reviews/{reviewId}/response")
    public ReviewView respond(@PathVariable UUID reviewId,
                              @Valid @RequestBody RespondToReview request,
                              HttpServletRequest httpRequest) {
        return ReviewView.publicView(reviews.respond(CurrentActor.requireUserId(), reviewId,
                request.body(), ClientIp.of(httpRequest)));
    }

    /**
     * @param subRatings cleanliness / accuracy / location / value, each 1–5 and
     *                   all optional: a guest who only wants to give one number
     *                   should not be forced through four
     */
    public record WriteReview(
            @Min(1) @Max(5) int rating,
            Map<String, Integer> subRatings,
            @Size(max = 4000) String comment) {
    }

    public record RespondToReview(@NotBlank @Size(max = 2000) String body) {
    }

    /** A review as anyone may see it. Never carries the author's phone number. */
    public record ReviewView(
            UUID id,
            UUID bookingId,
            String subject,
            String authorName,
            int rating,
            Map<String, Integer> subRatings,
            String comment,
            String response,
            Instant respondedAt,
            /** Whether anyone but the author can read it: published and not hidden. */
            boolean visible,
            Instant createdAt) {

        static ReviewView publicView(Review review) {
            return new ReviewView(review.getId(), review.getBooking().getId(),
                    review.getSubject().name(), firstName(review.getAuthor().getFullName()),
                    review.getRating(), review.getSubRatings(), review.getComment(),
                    review.getResponse(), review.getRespondedAt(), review.isReadable(),
                    review.getCreatedAt());
        }

        /** First name only, as the listing pages already do for hosts. */
        private static String firstName(String fullName) {
            if (fullName == null || fullName.isBlank()) {
                return "Guest";
            }
            return fullName.trim().split("\\s+")[0];
        }
    }
}
