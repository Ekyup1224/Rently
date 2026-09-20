package mn.innex.stay.trust.domain;

import java.time.Instant;
import java.util.UUID;

import mn.innex.stay.common.supply.SupplyKind;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

/**
 * A perceptual hash of one published photo.
 *
 * <p>Stored separately from the three photo tables because the comparison that
 * matters runs across all of them at once: a house photo reappearing on a hotel
 * is exactly the case worth catching, and a per-table column could not see it.
 */
@Entity
@Table(name = "photo_fingerprints")
public class PhotoFingerprint {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "photo_id", nullable = false, updatable = false)
    private UUID photoId;

    @Enumerated(EnumType.STRING)
    @Column(name = "supply_kind", nullable = false, length = 16, updatable = false)
    private SupplyKind supplyKind;

    @Column(name = "supply_id", nullable = false, updatable = false)
    private UUID supplyId;

    @Column(name = "owner_user_id", nullable = false, updatable = false)
    private UUID ownerUserId;

    @Column(nullable = false, updatable = false)
    private long phash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PhotoFingerprint() {
    }

    public PhotoFingerprint(UUID photoId, SupplyKind supplyKind, UUID supplyId, UUID ownerUserId,
                            long phash) {
        this.photoId = photoId;
        this.supplyKind = supplyKind;
        this.supplyId = supplyId;
        this.ownerUserId = ownerUserId;
        this.phash = phash;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getPhotoId() {
        return photoId;
    }

    public SupplyKind getSupplyKind() {
        return supplyKind;
    }

    public UUID getSupplyId() {
        return supplyId;
    }

    public UUID getOwnerUserId() {
        return ownerUserId;
    }

    public long getPhash() {
        return phash;
    }
}
