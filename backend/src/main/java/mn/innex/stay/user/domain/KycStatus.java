package mn.innex.stay.user.domain;

/** Identity-verification state. Documents themselves arrive with the KYC module. */
public enum KycStatus {
    NONE, PENDING, VERIFIED, REJECTED
}
