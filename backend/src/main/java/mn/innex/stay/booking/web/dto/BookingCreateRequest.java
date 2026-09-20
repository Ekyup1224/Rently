package mn.innex.stay.booking.web.dto;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A booking request, for either supply type.
 *
 * <p>Exactly one of {@code propertyId} and {@code roomTypeId} identifies what is
 * being booked. Carries no prices: the server recomputes the whole breakdown from
 * the listing and its calendar, so a tampered client changes nothing.
 *
 * @param checkOut exclusive: the morning the guest leaves
 * @param rooms    rooms of one type, for a hotel stay. Ignored for a house.
 * @param message  note to the host, shown with a request-to-book
 */
public record BookingCreateRequest(
        UUID propertyId,
        UUID roomTypeId,
        @NotNull LocalDate checkIn,
        @NotNull LocalDate checkOut,
        @Min(1) int guests,
        @Min(1) Integer rooms,
        @Size(max = 2000) String message) {

    /** True when this is a hotel reservation rather than a whole-place booking. */
    public boolean isHotelStay() {
        return roomTypeId != null;
    }

    public int roomsOrOne() {
        return rooms == null ? 1 : rooms;
    }

    /** Whether the request names exactly one thing to book. */
    public boolean namesExactlyOneSupply() {
        return (propertyId == null) != (roomTypeId == null);
    }
}
