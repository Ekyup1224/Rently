package mn.innex.stay.trust.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

/**
 * A set of released payouts sent as one transfer run.
 *
 * <p>Exists because eleven stays should be one payment, not eleven: one line on
 * the host's bank statement and one file for whoever sends it. The batch is the
 * record of what was in that run, which is what anybody reconciles against when
 * a host says the amount looks wrong.
 */
@Entity
@Table(name = "payout_batches")
public class PayoutBatch {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "payout_count", nullable = false)
    private int payoutCount;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    @Column(nullable = false, length = 3)
    private String currency = "MNT";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PayoutBatchStatus status = PayoutBatchStatus.OPEN;

    @Column(name = "exported_at")
    private Instant exportedAt;

    protected PayoutBatch() {
    }

    public PayoutBatch(UUID createdBy, int payoutCount, BigDecimal total, String currency) {
        this.createdBy = createdBy;
        this.payoutCount = payoutCount;
        this.total = total;
        this.currency = currency;
    }

    /** Marks the moment the file left, which is what reconciliation dates from. */
    public void markExported() {
        this.status = PayoutBatchStatus.EXPORTED;
        this.exportedAt = Instant.now();
    }

    public void markSettled() {
        this.status = PayoutBatchStatus.SETTLED;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public int getPayoutCount() {
        return payoutCount;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public String getCurrency() {
        return currency;
    }

    public PayoutBatchStatus getStatus() {
        return status;
    }

    public Instant getExportedAt() {
        return exportedAt;
    }
}
