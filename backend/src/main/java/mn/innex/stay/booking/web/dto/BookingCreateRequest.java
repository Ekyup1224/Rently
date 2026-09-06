package mn.innex.stay.booking.web.dto;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A booking request.
 *
 * <p>Carries no prices. The server recomputes the entire breakdown from the
 * listing and its calendar, so a tampered client changes nothing about what is
 * charged.
 *
 * @param checkOut exclusive: the morning the guest leaves
 * @param message  note to the host, shown with a request-to-book
 */
public record BookingCreateRequest(
        @NotNull UUID propertyId,
        @NotNull LocalDate checkIn,
        @NotNull LocalDate checkOut,
        @Min(1) int guests,
        @Size(max = 2000) String message) {
}
