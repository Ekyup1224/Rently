package mn.innex.stay.listing.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import mn.innex.stay.common.Money;
import mn.innex.stay.user.domain.User;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;
import mn.innex.stay.common.supply.Amenity;
import mn.innex.stay.common.supply.CancellationPolicy;
import mn.innex.stay.common.supply.SupplyStatus;

/**
 * A whole-place listing: house, apartment or ger.
 *
 * <p>Only an {@link SupplyStatus#APPROVED} listing is visible to guests, and
 * reaching that state requires the data a guest needs to make a decision —
 * enforced both by {@link #reviewReadinessProblems()} here and by a CHECK
 * constraint in the migration, so neither a code path nor a manual SQL edit can
 * publish a half-written listing.
 */
@Entity
@Table(name = "properties")
public class Property {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_user_id", nullable = false)
    private User owner;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "property_type", nullable = false, length = 32)
    private PropertyType propertyType;

    @Column(name = "max_guests", nullable = false)
    private int maxGuests = 1;

    @Column(nullable = false)
    private int bedrooms;

    @Column(nullable = false)
    private int beds = 1;

    @Column(nullable = false, precision = 3, scale = 1)
    private BigDecimal bathrooms = BigDecimal.ONE;

    @Column(name = "address_line", length = 255)
    private String addressLine;

    @Column(length = 120)
    private String district;

    @Column(nullable = false, length = 120)
    private String city;

    @Column(nullable = false, length = 2)
    private String country = "MN";

    private Double latitude;

    private Double longitude;

    /** JSON array of {@link Amenity} names. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<String> amenities = new ArrayList<>();

    @Column(name = "house_rules", columnDefinition = "text")
    private String houseRules;

    @Column(name = "check_in_from")
    private LocalTime checkInFrom;

    @Column(name = "check_out_by")
    private LocalTime checkOutBy;

    @Column(name = "base_price", nullable = false, precision = 14, scale = 2)
    private BigDecimal basePrice = Money.ZERO;

    @Column(name = "cleaning_fee", nullable = false, precision = 14, scale = 2)
    private BigDecimal cleaningFee = Money.ZERO;

    @Column(nullable = false, length = 3)
    private String currency = "MNT";

    @Column(name = "min_stay_nights", nullable = false)
    private int minStayNights = 1;

    @Column(name = "max_stay_nights")
    private Integer maxStayNights;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancellation_policy", nullable = false, length = 16)
    private CancellationPolicy cancellationPolicy = CancellationPolicy.MODERATE;

    /** When false, a booking request waits for the host to accept or decline. */
    @Column(name = "instant_book", nullable = false)
    private boolean instantBook;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private SupplyStatus status = SupplyStatus.DRAFT;

    @Column(name = "rejection_reason", columnDefinition = "text")
    private String rejectionReason;

    /**
     * Cached from published reviews. Null until the first one, which is not the
     * same as zero: an unrated place should say "new", not "nobody liked it".
     */
    @Column(name = "rating_average", precision = 3, scale = 2)
    private java.math.BigDecimal ratingAverage;

    @Column(name = "rating_count", nullable = false)
    private int ratingCount;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    @BatchSize(size = 30)
    @OneToMany(mappedBy = "property", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder asc, uploadedAt asc")
    private List<PropertyPhoto> photos = new ArrayList<>();

    protected Property() {
    }

    public Property(User owner, String title, PropertyType propertyType, String city) {
        this.owner = owner;
        this.title = title;
        this.propertyType = propertyType;
        this.city = city;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    /**
     * Checks the listing carries everything a guest needs before it can be
     * submitted for review.
     *
     * @return the reasons it is not ready, empty when it is
     */
    public List<String> reviewReadinessProblems() {
        List<String> problems = new ArrayList<>();
        if (description == null || description.isBlank()) {
            problems.add("A description is required");
        }
        if (latitude == null || longitude == null) {
            problems.add("A map location is required");
        }
        if (!Money.isPositive(basePrice)) {
            problems.add("A nightly price above zero is required");
        }
        if (photos.isEmpty()) {
            problems.add("At least one photo is required");
        }
        if (addressLine == null || addressLine.isBlank()) {
            problems.add("A street address is required");
        }
        return problems;
    }

    public Set<Amenity> amenitySet() {
        Set<Amenity> parsed = EnumSet.noneOf(Amenity.class);
        for (String name : amenities) {
            try {
                parsed.add(Amenity.parse(name));
            } catch (IllegalArgumentException ignored) {
                // A name that predates a rename should not break the whole listing.
            }
        }
        return parsed;
    }

    public void setAmenities(Set<Amenity> replacement) {
        Set<String> names = new LinkedHashSet<>();
        for (Amenity amenity : replacement) {
            names.add(amenity.name());
        }
        this.amenities = new ArrayList<>(names);
    }

    public void submitForReview() {
        this.status = SupplyStatus.PENDING_REVIEW;
        this.rejectionReason = null;
    }

    public void approve() {
        this.status = SupplyStatus.APPROVED;
        this.rejectionReason = null;
        if (publishedAt == null) {
            publishedAt = Instant.now();
        }
    }

    public void reject(String reason) {
        this.status = SupplyStatus.REJECTED;
        this.rejectionReason = reason;
    }

    public void addPhoto(PropertyPhoto photo) {
        photos.add(photo);
        // The first photo uploaded becomes the cover until the owner says otherwise.
        if (photos.size() == 1) {
            photo.markAsCover(true);
        }
    }

    public boolean isOwnedBy(UUID userId) {
        return owner != null && owner.getId().equals(userId);
    }

    public UUID getId() {
        return id;
    }

    public User getOwner() {
        return owner;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public PropertyType getPropertyType() {
        return propertyType;
    }

    public void setPropertyType(PropertyType propertyType) {
        this.propertyType = propertyType;
    }

    public int getMaxGuests() {
        return maxGuests;
    }

    public void setMaxGuests(int maxGuests) {
        this.maxGuests = maxGuests;
    }

    public int getBedrooms() {
        return bedrooms;
    }

    public void setBedrooms(int bedrooms) {
        this.bedrooms = bedrooms;
    }

    public int getBeds() {
        return beds;
    }

    public void setBeds(int beds) {
        this.beds = beds;
    }

    public BigDecimal getBathrooms() {
        return bathrooms;
    }

    public void setBathrooms(BigDecimal bathrooms) {
        this.bathrooms = bathrooms;
    }

    public String getAddressLine() {
        return addressLine;
    }

    public void setAddressLine(String addressLine) {
        this.addressLine = addressLine;
    }

    public String getDistrict() {
        return district;
    }

    public void setDistrict(String district) {
        this.district = district;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public Double getLatitude() {
        return latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLocation(Double latitude, Double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public List<String> getAmenities() {
        return amenities;
    }

    public String getHouseRules() {
        return houseRules;
    }

    public void setHouseRules(String houseRules) {
        this.houseRules = houseRules;
    }

    public LocalTime getCheckInFrom() {
        return checkInFrom;
    }

    public void setCheckInFrom(LocalTime checkInFrom) {
        this.checkInFrom = checkInFrom;
    }

    public LocalTime getCheckOutBy() {
        return checkOutBy;
    }

    public void setCheckOutBy(LocalTime checkOutBy) {
        this.checkOutBy = checkOutBy;
    }

    public BigDecimal getBasePrice() {
        return basePrice;
    }

    public void setBasePrice(BigDecimal basePrice) {
        this.basePrice = Money.of(basePrice);
    }

    public BigDecimal getCleaningFee() {
        return cleaningFee;
    }

    public void setCleaningFee(BigDecimal cleaningFee) {
        this.cleaningFee = Money.of(cleaningFee);
    }

    public String getCurrency() {
        return currency;
    }

    public int getMinStayNights() {
        return minStayNights;
    }

    public void setMinStayNights(int minStayNights) {
        this.minStayNights = minStayNights;
    }

    public Integer getMaxStayNights() {
        return maxStayNights;
    }

    public void setMaxStayNights(Integer maxStayNights) {
        this.maxStayNights = maxStayNights;
    }

    public CancellationPolicy getCancellationPolicy() {
        return cancellationPolicy;
    }

    public void setCancellationPolicy(CancellationPolicy cancellationPolicy) {
        this.cancellationPolicy = cancellationPolicy;
    }

    public boolean isInstantBook() {
        return instantBook;
    }

    public void setInstantBook(boolean instantBook) {
        this.instantBook = instantBook;
    }

    public SupplyStatus getStatus() {
        return status;
    }

    public void setStatus(SupplyStatus status) {
        this.status = status;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<PropertyPhoto> getPhotos() {
        return photos;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof Property property && id != null && id.equals(property.id);
    }

    @Override
    public int hashCode() {
        return id == null ? System.identityHashCode(this) : id.hashCode();
    }

    public java.math.BigDecimal getRatingAverage() {
        return ratingAverage;
    }

    public int getRatingCount() {
        return ratingCount;
    }
}
