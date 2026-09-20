package mn.innex.stay.trust.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import mn.innex.stay.user.domain.KycStatus;
import mn.innex.stay.user.domain.User;
import org.hibernate.annotations.UuidGenerator;

/**
 * Identity documents a host submitted, and what an admin made of them.
 *
 * <p>Not required to publish a listing — demanding documents before someone has
 * seen any value costs more supply than it saves in fraud. Required to be paid,
 * which is where it actually bites: a fake host can list, but cannot collect
 * without putting a real identity and a matching bank account behind it.
 */
@Entity
@Table(name = "kyc_submissions")
public class KycSubmission {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 32, updatable = false)
    private KycDocumentType documentType;

    @Column(name = "document_number", nullable = false, length = 64, updatable = false)
    private String documentNumber;

    /** As printed on the document; the bank account name has to match it. */
    @Column(name = "full_name", nullable = false, length = 160, updatable = false)
    private String fullName;

    @Column(name = "document_image_key", nullable = false, length = 512, updatable = false)
    private String documentImageKey;

    @Column(name = "selfie_image_key", length = 512, updatable = false)
    private String selfieImageKey;

    /** Reuses the account-level enum, minus NONE: a submission is never "none". */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private KycStatus status = KycStatus.PENDING;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "review_note", length = 500)
    private String reviewNote;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected KycSubmission() {
    }

    public KycSubmission(User user, KycDocumentType documentType, String documentNumber,
                         String fullName, String documentImageKey, String selfieImageKey) {
        this.user = user;
        this.documentType = documentType;
        this.documentNumber = documentNumber;
        this.fullName = fullName;
        this.documentImageKey = documentImageKey;
        this.selfieImageKey = selfieImageKey;
    }

    public void review(KycStatus outcome, UUID reviewedBy, String note) {
        this.status = outcome;
        this.reviewedBy = reviewedBy;
        this.reviewedAt = Instant.now();
        this.reviewNote = note;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public KycDocumentType getDocumentType() {
        return documentType;
    }

    public String getDocumentNumber() {
        return documentNumber;
    }

    public String getFullName() {
        return fullName;
    }

    public String getDocumentImageKey() {
        return documentImageKey;
    }

    public String getSelfieImageKey() {
        return selfieImageKey;
    }

    public KycStatus getStatus() {
        return status;
    }

    public UUID getReviewedBy() {
        return reviewedBy;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public String getReviewNote() {
        return reviewNote;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
