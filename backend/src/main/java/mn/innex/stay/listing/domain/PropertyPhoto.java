package mn.innex.stay.listing.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

/**
 * Metadata for one listing photo. The bytes live in object storage; this row
 * records where, so the API never proxies image traffic.
 *
 * <p>A row exists only after the upload is confirmed against storage, so a
 * photo record always points at an object that actually exists.
 */
@Entity
@Table(name = "property_photos")
public class PropertyPhoto {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "property_id", nullable = false)
    private Property property;

    /** Object key within the media bucket, e.g. {@code properties/{id}/{uuid}.jpg}. */
    @Column(name = "storage_key", nullable = false, length = 512)
    private String storageKey;

    @Column(name = "content_type", nullable = false, length = 64)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    private Integer width;

    private Integer height;

    @Column(name = "alt_text", length = 255)
    private String altText;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "is_cover", nullable = false)
    private boolean cover;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant uploadedAt;

    protected PropertyPhoto() {
    }

    public PropertyPhoto(Property property, String storageKey, String contentType,
                         long sizeBytes, String altText, int sortOrder) {
        this.property = property;
        this.storageKey = storageKey;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.altText = altText;
        this.sortOrder = sortOrder;
    }

    @PrePersist
    void onCreate() {
        uploadedAt = Instant.now();
    }

    public void markAsCover(boolean cover) {
        this.cover = cover;
    }

    public UUID getId() {
        return id;
    }

    public Property getProperty() {
        return property;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public Integer getWidth() {
        return width;
    }

    public Integer getHeight() {
        return height;
    }

    public void setDimensions(Integer width, Integer height) {
        this.width = width;
        this.height = height;
    }

    public String getAltText() {
        return altText;
    }

    public void setAltText(String altText) {
        this.altText = altText;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public boolean isCover() {
        return cover;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }
}
