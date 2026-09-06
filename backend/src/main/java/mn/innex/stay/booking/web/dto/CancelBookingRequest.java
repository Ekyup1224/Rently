package mn.innex.stay.booking.web.dto;

import jakarta.validation.constraints.Size;

/** @param reason shown to the other party and recorded in the audit trail */
public record CancelBookingRequest(@Size(max = 1000) String reason) {
}
