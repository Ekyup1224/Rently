package mn.innex.stay.booking.web.dto;

import jakarta.validation.constraints.Size;

/** A host's note when accepting or declining a request. */
public record HostDecisionRequest(@Size(max = 1000) String note) {
}
