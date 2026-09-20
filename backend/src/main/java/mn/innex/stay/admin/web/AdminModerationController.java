package mn.innex.stay.admin.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import mn.innex.stay.common.web.ClientIp;
import mn.innex.stay.common.web.PageResponse;
import mn.innex.stay.messaging.service.MessagingService;
import mn.innex.stay.review.domain.Review;
import mn.innex.stay.review.domain.ReviewStatus;
import mn.innex.stay.review.service.ReviewService;
import mn.innex.stay.security.CurrentActor;
import mn.innex.stay.trust.domain.PayoutBatch;
import mn.innex.stay.trust.service.PayoutBatchService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Moderating what people write, and paying hosts.
 *
 * <p>Grouped because both are the same job in practice — someone working through
 * the day's queues — and because the flagged-message queue is where an attempt
 * to move payment off the platform surfaces, which is precisely a payout
 * question.
 */
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminModerationController {

    private final ReviewService reviews;
    private final MessagingService messaging;
    private final PayoutBatchService batches;

    public AdminModerationController(ReviewService reviews, MessagingService messaging,
                                     PayoutBatchService batches) {
        this.reviews = reviews;
        this.messaging = messaging;
        this.batches = batches;
    }

    // --- reviews ---------------------------------------------------------

    @GetMapping("/reviews")
    public PageResponse<ModeratedReview> listReviews(
            @RequestParam(required = false) List<ReviewStatus> status,
            @PageableDefault(size = 25) Pageable pageable) {
        return PageResponse.of(reviews.moderationQueue(status, pageable), ModeratedReview::from);
    }

    /** Takes a review down. The reason is shown to its author. */
    @PostMapping("/reviews/{reviewId}/hide")
    public ModeratedReview hide(@PathVariable UUID reviewId,
                                @Valid @RequestBody ModerateReview request,
                                HttpServletRequest httpRequest) {
        return ModeratedReview.from(reviews.moderate(CurrentActor.requireUserId(), reviewId,
                true, request.reason(), ClientIp.of(httpRequest)));
    }

    @PostMapping("/reviews/{reviewId}/restore")
    public ModeratedReview restore(@PathVariable UUID reviewId,
                                   HttpServletRequest httpRequest) {
        return ModeratedReview.from(reviews.moderate(CurrentActor.requireUserId(), reviewId,
                false, null, ClientIp.of(httpRequest)));
    }

    // --- flagged messages ------------------------------------------------

    /**
     * Messages the off-platform detector was unhappy about.
     *
     * <p>Reading private messages is intrusive, so only flagged ones appear here
     * — never a whole conversation — and each one already has a listing flag
     * beside it freezing that host's money.
     */
    @GetMapping("/messages/flagged")
    public PageResponse<FlaggedMessage> flaggedMessages(
            @PageableDefault(size = 25) Pageable pageable) {
        return PageResponse.of(messaging.flagged(pageable), message ->
                new FlaggedMessage(message.getId(), message.getFlaggedReason(),
                        message.getSender().getFullName(), message.getSender().getPhone(),
                        message.getConversation().getBooking().getReference(),
                        message.getBody(), message.getSentAt()));
    }

    // --- payout batches --------------------------------------------------

    @GetMapping("/payout-batches")
    public PageResponse<BatchView> listBatches(@PageableDefault(size = 25) Pageable pageable) {
        return PageResponse.of(batches.list(pageable), BatchView::from);
    }

    /** Gathers everything released into one transfer run. */
    @PostMapping("/payout-batches")
    public BatchView assemble(HttpServletRequest httpRequest) {
        return BatchView.from(batches.assemble(CurrentActor.requireUserId(),
                ClientIp.of(httpRequest)));
    }

    /** One line per host: what the bank file is made of. */
    @GetMapping("/payout-batches/{batchId}/lines")
    public List<PayoutBatchService.TransferLine> lines(@PathVariable UUID batchId) {
        return batches.transferLines(batchId);
    }

    @PostMapping("/payout-batches/{batchId}/exported")
    public BatchView markExported(@PathVariable UUID batchId, HttpServletRequest httpRequest) {
        return BatchView.from(batches.markExported(CurrentActor.requireUserId(), batchId,
                ClientIp.of(httpRequest)));
    }

    /** Confirms the bank moved it, marking every payout in the run as paid. */
    @PostMapping("/payout-batches/{batchId}/settled")
    public BatchView settle(@PathVariable UUID batchId,
                            @Valid @RequestBody SettleBatch request,
                            HttpServletRequest httpRequest) {
        return BatchView.from(batches.settle(CurrentActor.requireUserId(), batchId,
                request.providerRef(), ClientIp.of(httpRequest)));
    }

    public record ModerateReview(@NotBlank @Size(max = 500) String reason) {
    }

    public record SettleBatch(@NotBlank @Size(max = 128) String providerRef) {
    }

    public record ModeratedReview(UUID id, UUID bookingId, String bookingReference,
                                  String subject, String authorName, int rating, String comment,
                                  boolean visible, String status, String hiddenReason,
                                  Instant createdAt) {

        static ModeratedReview from(Review review) {
            return new ModeratedReview(review.getId(), review.getBooking().getId(),
                    review.getBooking().getReference(), review.getSubject().name(),
                    review.getAuthor().getFullName(), review.getRating(), review.getComment(),
                    review.isReadable(), review.getStatus().name(), review.getHiddenReason(),
                    review.getCreatedAt());
        }
    }

    public record FlaggedMessage(UUID id, String reason, String senderName, String senderPhone,
                                 String bookingReference, String body, Instant sentAt) {
    }

    public record BatchView(UUID id, int payoutCount, BigDecimal total, String currency,
                            String status, Instant createdAt, Instant exportedAt) {

        static BatchView from(PayoutBatch batch) {
            return new BatchView(batch.getId(), batch.getPayoutCount(), batch.getTotal(),
                    batch.getCurrency(), batch.getStatus().name(), batch.getCreatedAt(),
                    batch.getExportedAt());
        }
    }
}
