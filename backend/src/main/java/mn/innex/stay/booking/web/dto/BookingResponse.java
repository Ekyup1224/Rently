package mn.innex.stay.booking.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import mn.innex.stay.booking.domain.Booking;
import mn.innex.stay.booking.domain.BookingPaymentStatus;
import mn.innex.stay.booking.domain.BookingStatus;
import mn.innex.stay.listing.domain.CancellationPolicy;
import mn.innex.stay.listing.storage.ObjectStorage;

/**
 * A booking as one of its two parties sees it.
 *
 * <p>{@code viewer} controls what is included: a guest sees what they paid, a host
 * sees what they will receive. Neither sees the other's figures, and the platform
 * commission appears only on the host's view.
 *
 * @param counterpartyName the other party's first name; full contact details
 *                         arrive with messaging in Step 5
 * @param expiresAt        deadline for the current pending state, so the UI can
 *                         count down instead of leaving people guessing
 */
public record BookingResponse(
        UUID id,
        String reference,
        BookingStatus status,
        BookingPaymentStatus paymentStatus,
        LocalDate checkIn,
        LocalDate checkOut,
        int nights,
        int guestCount,
        String currency,
        BigDecimal nightlySubtotal,
        BigDecimal cleaningFee,
        BigDecimal guestServiceFee,
        BigDecimal tax,
        BigDecimal total,
        BigDecimal hostCommission,
        BigDecimal hostPayout,
        CancellationPolicy cancellationPolicy,
        String guestMessage,
        String hostResponseNote,
        BigDecimal refundAmount,
        String cancellationReason,
        Instant expiresAt,
        Instant confirmedAt,
        Instant createdAt,
        ListingRef listing,
        String counterpartyName,
        Viewer viewer) {

    public enum Viewer {
        GUEST, HOST
    }

    /** Just enough of the listing to render a trip or reservation card. */
    public record ListingRef(UUID id, String title, String city, String district,
                             String coverPhotoUrl) {
    }

    public static BookingResponse forGuest(Booking booking, ObjectStorage storage) {
        return build(booking, storage, Viewer.GUEST);
    }

    public static BookingResponse forHost(Booking booking, ObjectStorage storage) {
        return build(booking, storage, Viewer.HOST);
    }

    private static BookingResponse build(Booking booking, ObjectStorage storage, Viewer viewer) {
        var property = booking.getProperty();
        String coverUrl = property == null ? null : property.getPhotos().stream()
                .filter(mn.innex.stay.listing.domain.PropertyPhoto::isCover)
                .findFirst()
                .or(() -> property.getPhotos().stream().findFirst())
                .map(photo -> storage.publicUrl(photo.getStorageKey()))
                .orElse(null);

        boolean host = viewer == Viewer.HOST;
        var counterparty = host ? booking.getGuest() : booking.getHost();

        return new BookingResponse(
                booking.getId(), booking.getReference(), booking.getStatus(),
                booking.getPaymentStatus(), booking.getCheckIn(), booking.getCheckOut(),
                booking.nightCount(), booking.getGuestCount(), booking.getCurrency(),
                booking.getNightlySubtotal(), booking.getCleaningFee(),
                // The guest service fee is the guest's cost, not part of the host's economics.
                host ? null : booking.getGuestServiceFee(),
                booking.getTax(),
                host ? null : booking.getTotal(),
                host ? booking.getHostCommission() : null,
                host ? booking.getHostPayout() : null,
                booking.getCancellationPolicy(), booking.getGuestMessage(),
                booking.getHostResponseNote(), booking.getRefundAmount(),
                booking.getCancellationReason(), booking.getExpiresAt(),
                booking.getConfirmedAt(), booking.getCreatedAt(),
                property == null ? null : new ListingRef(property.getId(), property.getTitle(),
                        property.getCity(), property.getDistrict(), coverUrl),
                firstName(counterparty), viewer);
    }

    private static String firstName(mn.innex.stay.user.domain.User user) {
        if (user == null || user.getFullName() == null || user.getFullName().isBlank()) {
            return null;
        }
        return user.getFullName().trim().split("\\s+")[0];
    }
}
