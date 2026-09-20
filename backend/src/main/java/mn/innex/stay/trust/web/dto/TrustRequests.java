package mn.innex.stay.trust.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import mn.innex.stay.trust.domain.KycDocumentType;
import mn.innex.stay.user.domain.KycStatus;

/** Request payloads for trust and safety. Small, and always read together. */
public final class TrustRequests {

    private TrustRequests() {
    }

    /** A guest saying something is wrong with a listing. */
    public record ReportListing(
            @NotBlank @Size(max = 64) String reason,
            @Size(max = 2000) String details,
            java.util.UUID bookingId) {
    }

    /** An admin's decision on a flag. A dismissal may explain itself; an upholding should. */
    public record ResolveFlag(@Size(max = 500) String note) {
    }

    public record ReleasePayout(@Size(max = 500) String note) {
    }

    public record HoldPayout(@NotBlank @Size(max = 64) String reason) {
    }

    /** Records a transfer that actually happened. */
    public record MarkPayoutPaid(
            @NotBlank @Size(max = 128) String providerRef,
            @Size(max = 500) String note) {
    }

    /**
     * Identity documents. The images are uploaded to storage first and referenced
     * by key, the same handshake the galleries use — these objects are private,
     * so nothing here is ever turned into a public URL.
     */
    public record SubmitKyc(
            @NotNull KycDocumentType documentType,
            @NotBlank @Size(max = 64) String documentNumber,
            @NotBlank @Size(max = 160) String fullName,
            @NotBlank @Size(max = 512) String documentImageKey,
            @Size(max = 512) String selfieImageKey) {
    }

    public record ReviewKyc(
            @NotNull KycStatus outcome,
            @Size(max = 500) String note) {
    }
}
