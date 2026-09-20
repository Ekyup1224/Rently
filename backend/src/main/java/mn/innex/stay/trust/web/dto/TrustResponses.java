package mn.innex.stay.trust.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import mn.innex.stay.booking.domain.Booking;
import mn.innex.stay.trust.domain.KycSubmission;
import mn.innex.stay.trust.domain.ListingFlag;
import mn.innex.stay.trust.domain.Payout;

/** Response shapes for trust and safety. */
public final class TrustResponses {

    private TrustResponses() {
    }

    /**
     * A payout as a host or an admin sees it.
     *
     * @param blockedReason why the money is held, in the same words the admin
     *                      console and the host's earnings page both show
     */
    public record PayoutView(
            UUID id,
            UUID bookingId,
            String bookingReference,
            String listingTitle,
            java.time.LocalDate checkIn,
            java.time.LocalDate checkOut,
            BigDecimal amount,
            String currency,
            String status,
            Instant releaseAfter,
            String blockedReason,
            Instant releasedAt,
            Instant paidAt,
            String providerRef,
            Instant createdAt) {

        public static PayoutView from(Payout payout) {
            Booking booking = payout.getBooking();
            return new PayoutView(
                    payout.getId(),
                    booking.getId(),
                    booking.getReference(),
                    titleOf(booking),
                    booking.getCheckIn(),
                    booking.getCheckOut(),
                    payout.getAmount(),
                    payout.getCurrency(),
                    payout.getStatus().name(),
                    payout.getReleaseAfter(),
                    payout.getBlockedReason(),
                    payout.getReleasedAt(),
                    payout.getPaidAt(),
                    payout.getProviderRef(),
                    payout.getCreatedAt());
        }

        private static String titleOf(Booking booking) {
            if (booking.isHotelStay()) {
                return booking.getRoomType().getHotel().getName()
                        + " · " + booking.getRoomType().getName();
            }
            return booking.getProperty() == null ? "Stay" : booking.getProperty().getTitle();
        }
    }

    /** A flag in the review queue. */
    public record FlagView(
            UUID id,
            String supplyKind,
            UUID supplyId,
            String type,
            String status,
            UUID raisedBy,
            UUID bookingId,
            String summary,
            Map<String, Object> details,
            String resolutionNote,
            Instant resolvedAt,
            Instant createdAt) {

        public static FlagView from(ListingFlag flag) {
            return new FlagView(flag.getId(), flag.getSupplyKind().name(), flag.getSupplyId(),
                    flag.getType().name(), flag.getStatus().name(), flag.getRaisedBy(),
                    flag.getBookingId(), flag.getSummary(), flag.getDetails(),
                    flag.getResolutionNote(), flag.getResolvedAt(), flag.getCreatedAt());
        }
    }

    /**
     * A KYC submission.
     *
     * <p>The document number is deliberately absent from the host's own view and
     * present only for a reviewer: it is the one field that turns this record into
     * something worth stealing.
     */
    public record KycView(
            UUID id,
            UUID userId,
            String userName,
            String userPhone,
            String documentType,
            String documentNumber,
            String fullName,
            String status,
            String reviewNote,
            Instant reviewedAt,
            Instant createdAt) {

        public static KycView forReviewer(KycSubmission submission) {
            return new KycView(submission.getId(), submission.getUser().getId(),
                    submission.getUser().getFullName(), submission.getUser().getPhone(),
                    submission.getDocumentType().name(), submission.getDocumentNumber(),
                    submission.getFullName(), submission.getStatus().name(),
                    submission.getReviewNote(), submission.getReviewedAt(),
                    submission.getCreatedAt());
        }

        public static KycView forSelf(KycSubmission submission) {
            return new KycView(submission.getId(), submission.getUser().getId(), null, null,
                    submission.getDocumentType().name(), null, submission.getFullName(),
                    submission.getStatus().name(), submission.getReviewNote(),
                    submission.getReviewedAt(), submission.getCreatedAt());
        }
    }
}
