package mn.innex.stay.listing.web.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Asks what a stay would cost, without creating anything. */
public record QuoteRequest(
        @NotNull LocalDate checkIn,
        @NotNull LocalDate checkOut,
        @Min(1) int guests) {
}
