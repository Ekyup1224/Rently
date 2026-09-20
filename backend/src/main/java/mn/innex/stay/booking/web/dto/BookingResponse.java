package mn.innex.stay.booking.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import mn.innex.stay.booking.domain.Booking;
import mn.innex.stay.booking.domain.BookingPaymentStatus;
import mn.innex.stay.booking.domain.BookingStatus;
import mn.innex.stay.common.supply.CancellationPolicy;
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

    /**
     * Just enough of the supply to render a trip or reservation card.
     *
     * @param supplyType   PROPERTY or HOTEL, so the card can label itself correctly
     * @param roomTypeName the room booked, for a hotel stay
     */
    public record ListingRef(UUID id, String title, String city, String district,
                             String coverPhotoUrl, String supplyType, String roomTypeName,
                             Integer rooms) {
    }

    public static BookingResponse forGuest(Booking booking, ObjectStorage storage) {
        return build(booking, storage, Viewer.GUEST);
    }

    public static BookingResponse forHost(Booking booking, ObjectStorage storage) {
        return build(booking, storage, Viewer.HOST);
    }

    private static BookingResponse build(Booking booking, ObjectStorage storage, Viewer viewer) {
        boolean host = viewer == Viewer.HOST;
        var counterparty = host ? booking.getGuest() : booking.getHost();
        ListingRef supply = booking.isHotelStay()
                ? hotelRef(booking, storage)
                : propertyRef(booking, storage);

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
                booking.getConfirmedAt(), booking.getCreatedAt(), supply,
                firstName(counterparty), viewer);
    }

    private static ListingRef propertyRef(Booking booking, ObjectStorage storage) {
        var property = booking.getProperty();
        if (property == null) {
            return null;
        }
        String coverUrl = property.getPhotos().stream()
                .filter(mn.innex.stay.listing.domain.PropertyPhoto::isCover)
                .findFirst()
                .or(() -> property.getPhotos().stream().findFirst())
                .map(photo -> storage.publicUrl(photo.getStorageKey()))
                .orElse(null);
        return new ListingRef(property.getId(), property.getTitle(), property.getCity(),
                property.getDistrict(), coverUrl, "PROPERTY", null, null);
    }

    private static ListingRef hotelRef(Booking booking, ObjectStorage storage) {
        var roomType = booking.getRoomType();
        if (roomType == null) {
            return null;
        }
        var hotel = roomType.getHotel();
        // Prefer a picture of the room the guest actually booked; fall back to the
        // hotel's own cover when the room type has no photos yet.
        String coverUrl = roomType.getPhotos().stream()
                .filter(mn.innex.stay.hotel.domain.RoomTypePhoto::isCover)
                .findFirst()
                .or(() -> roomType.getPhotos().stream().findFirst())
                .map(photo -> storage.publicUrl(photo.getStorageKey()))
                .or(() -> hotel.getPhotos().stream()
                        .filter(mn.innex.stay.hotel.domain.HotelPhoto::isCover)
                        .findFirst()
                        .or(() -> hotel.getPhotos().stream().findFirst())
                        .map(photo -> storage.publicUrl(photo.getStorageKey())))
                .orElse(null);
        return new ListingRef(hotel.getId(), hotel.getName(), hotel.getCity(), hotel.getDistrict(),
                coverUrl, "HOTEL", roomType.getName(), booking.getRoomCount());
    }

    private static String firstName(mn.innex.stay.user.domain.User user) {
        if (user == null || user.getFullName() == null || user.getFullName().isBlank()) {
            return null;
        }
        return user.getFullName().trim().split("\\s+")[0];
    }
}
