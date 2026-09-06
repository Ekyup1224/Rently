package mn.innex.stay.booking.domain;

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
 * The platform's take rate: a host-side percentage plus an optional guest service
 * fee, per build-spec section 7 — configurable, never hardcoded.
 *
 * <p>Rules are effective-dated and never edited in place. Changing the rate means
 * closing the current rule and inserting a new one, so a booking priced last month
 * can always be explained by the rule it recorded.
 */
@Entity
@Table(name = "commission_rules")
public class CommissionRule {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private CommissionScope scope = CommissionScope.GLOBAL;

    /** The property type or hotel this rule narrows to; null for a GLOBAL rule. */
    @Column(length = 32)
    private String category;

    @Column(name = "host_fee_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal hostFeePercent;

    @Column(name = "guest_fee_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal guestFeePercent = BigDecimal.ZERO;

    @Column(name = "effective_from", nullable = false)
    private Instant effectiveFrom;

    @Column(name = "effective_to")
    private Instant effectiveTo;

    @Column(length = 255)
    private String note;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected CommissionRule() {
    }

    public CommissionRule(CommissionScope scope, String category, BigDecimal hostFeePercent,
                          BigDecimal guestFeePercent, Instant effectiveFrom, String note,
                          UUID createdBy) {
        this.scope = scope;
        this.category = category;
        this.hostFeePercent = hostFeePercent;
        this.guestFeePercent = guestFeePercent == null ? BigDecimal.ZERO : guestFeePercent;
        this.effectiveFrom = effectiveFrom == null ? Instant.now() : effectiveFrom;
        this.note = note;
        this.createdBy = createdBy;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        if (effectiveFrom == null) {
            effectiveFrom = createdAt;
        }
    }

    /** Closes this rule so a replacement can take over. */
    public void closeAt(Instant when) {
        this.effectiveTo = when;
    }

    public boolean isEffectiveAt(Instant when) {
        return !effectiveFrom.isAfter(when) && (effectiveTo == null || effectiveTo.isAfter(when));
    }

    public UUID getId() {
        return id;
    }

    public CommissionScope getScope() {
        return scope;
    }

    public String getCategory() {
        return category;
    }

    public BigDecimal getHostFeePercent() {
        return hostFeePercent;
    }

    public BigDecimal getGuestFeePercent() {
        return guestFeePercent;
    }

    public Instant getEffectiveFrom() {
        return effectiveFrom;
    }

    public Instant getEffectiveTo() {
        return effectiveTo;
    }

    public String getNote() {
        return note;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
