package mn.innex.stay.admin.web.dto;

import jakarta.validation.constraints.NotNull;
import mn.innex.stay.user.domain.KycStatus;

public record ChangeKycStatusRequest(@NotNull KycStatus kycStatus) {
}
