package mn.innex.stay.trust.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import mn.innex.stay.common.supply.SupplyKind;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

/**
 * Something about a listing that needs a person to decide.
 *
 * <p>Machine suspicion and guest complaints share one queue on purpose: the
 * question they raise is the same one — is this listing real, and should its
 * money move? While a flag is open the listing cannot be approved and its
 * payouts stay blocked.
 */
@Entity
@Table(name = "listing_flags")
public class ListingFlag {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "supply_kind", nullable = false, length = 16, updatable = false)
    private SupplyKind supplyKind;

    @Column(name = "supply_id", nullable = false, updatable = false)
    private UUID supplyId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32, updatable = false)
    private FlagType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private FlagStatus status = FlagStatus.OPEN;

    /** The guest who reported it; null when a check raised the flag. */
    @Column(name = "raised_by", updatable = false)
    private UUID raisedBy;

    @Column(name = "booking_id", updatable = false)
    private UUID bookingId;

    @Column(nullable = false, length = 500, updatable = false)
    private String summary;

    /** Evidence: matching photo ids and distance, or the guest's own words. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> details = Map.of();

    @Column(name = "resolved_by")
    private UUID resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolution_note", length = 500)
    private String resolutionNote;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ListingFlag() {
    }

    public ListingFlag(SupplyKind supplyKind, UUID supplyId, FlagType type, UUID raisedBy,
                       UUID bookingId, String summary, Map<String, Object> details) {
        this.supplyKind = supplyKind;
        this.supplyId = supplyId;
        this.type = type;
        this.raisedBy = raisedBy;
        this.bookingId = bookingId;
        this.summary = summary;
        this.details = details == null ? Map.of() : details;
    }

    public void resolve(FlagStatus outcome, UUID resolvedBy, String note) {
        this.status = outcome;
        this.resolvedBy = resolvedBy;
        this.resolvedAt = Instant.now();
        this.resolutionNote = note;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public SupplyKind getSupplyKind() {
        return supplyKind;
    }

    public UUID getSupplyId() {
        return supplyId;
    }

    public FlagType getType() {
        return type;
    }

    public FlagStatus getStatus() {
        return status;
    }

    public UUID getRaisedBy() {
        return raisedBy;
    }

    public UUID getBookingId() {
        return bookingId;
    }

    public String getSummary() {
        return summary;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    public UUID getResolvedBy() {
        return resolvedBy;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public String getResolutionNote() {
        return resolutionNote;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
