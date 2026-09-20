package mn.innex.stay.trust.domain;

import java.math.BigDecimal;
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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import mn.innex.stay.booking.domain.Booking;
import mn.innex.stay.user.domain.Organization;
import mn.innex.stay.user.domain.User;
import org.hibernate.annotations.UuidGenerator;

/**
 * What the platform owes a host for one stay, and whether it may be sent yet.
 *
 * <p>The hold is the whole point. A listing that does not exist cannot survive a
 * guest arriving at it, so money that only moves after arrival is money a
 * fraudulent host never receives. Everything else in this module — fingerprints,
 * reports, identity checks — exists to decide whether this row is allowed to
 * reach {@link PayoutStatus#RELEASED}.
 */
@Entity
@Table(name = "payouts")
public class Payout {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false, updatable = false)
    private Booking booking;

    /** A house pays its owner; a hotel pays its organization, through its owner. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payee_user_id", nullable = false, updatable = false)
    private User payee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", updatable = false)
    private Organization organization;

    @Column(nullable = false, precision = 14, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(nullable = false, length = 3, updatable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PayoutStatus status = PayoutStatus.PENDING;

    @Column(name = "release_after", nullable = false, updatable = false)
    private Instant releaseAfter;

    @Column(name = "blocked_reason", length = 64)
    private String blockedReason;

    @Column(name = "released_at")
    private Instant releasedAt;

    @Column(name = "released_by")
    private UUID releasedBy;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "provider_ref", length = 128)
    private String providerRef;

    @Column(length = 500)
    private String note;

    /** Set when this payout joins a transfer run. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id")
    private PayoutBatch batch;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected Payout() {
    }

    public Payout(Booking booking, User payee, Organization organization, BigDecimal amount,
                  String currency, Instant releaseAfter) {
        this.booking = booking;
        this.payee = payee;
        this.organization = organization;
        this.amount = amount;
        this.currency = currency;
        this.releaseAfter = releaseAfter;
    }

    /** Holds the money and records why, for a human to work through. */
    public void block(String reason) {
        this.status = PayoutStatus.BLOCKED;
        this.blockedReason = reason;
    }

    /** Back to waiting: the reason for the block is gone. */
    public void unblock() {
        this.status = PayoutStatus.PENDING;
        this.blockedReason = null;
    }

    /** @param releasedBy the admin who overrode, or null when the sweep did it */
    public void release(UUID releasedBy) {
        this.status = PayoutStatus.RELEASED;
        this.blockedReason = null;
        this.releasedAt = Instant.now();
        this.releasedBy = releasedBy;
    }

    /** Joins a transfer run; the payout is still RELEASED until the run is sent. */
    public void assignTo(PayoutBatch batch) {
        this.batch = batch;
    }

    public void markPaid(String providerRef, String note) {
        this.status = PayoutStatus.PAID;
        this.paidAt = Instant.now();
        this.providerRef = providerRef;
        this.note = note;
    }

    /** The stay is off, so there is nothing to pay. */
    public void cancel(String reason) {
        this.status = PayoutStatus.CANCELLED;
        this.note = reason;
    }

    /** Whether the hold has expired, regardless of the other release conditions. */
    public boolean isDue(Instant now) {
        return !now.isBefore(releaseAfter);
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Booking getBooking() {
        return booking;
    }

    public User getPayee() {
        return payee;
    }

    public Organization getOrganization() {
        return organization;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public PayoutStatus getStatus() {
        return status;
    }

    public Instant getReleaseAfter() {
        return releaseAfter;
    }

    public String getBlockedReason() {
        return blockedReason;
    }

    public Instant getReleasedAt() {
        return releasedAt;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public String getProviderRef() {
        return providerRef;
    }

    public String getNote() {
        return note;
    }

    public PayoutBatch getBatch() {
        return batch;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
