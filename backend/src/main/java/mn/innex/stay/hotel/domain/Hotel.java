package mn.innex.stay.hotel.domain;

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
import mn.innex.stay.common.supply.Amenity;
import mn.innex.stay.common.supply.CancellationPolicy;
import mn.innex.stay.common.supply.SupplyStatus;
import mn.innex.stay.user.domain.Organization;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

/**
 * A hotel: a building owned by an organization, selling room types.
 *
 * <p>Owned by an {@link Organization} rather than a person, because a hotel is a
 * business: staff come and go, payouts go to a company account, and the manager
 * who signed up is not necessarily the one on duty. That is also why
 * {@code HOTEL_MANAGER} grants are organization-scoped.
 */
@Entity
@Table(name = "hotels")
public class Hotel {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(nullable = false, length = 180)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    /** 1–5, or null when the hotel is unrated — which is not the same as zero. */
    @Column(name = "star_rating")
    private Integer starRating;

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

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<String> amenities = new ArrayList<>();

    @Column(columnDefinition = "text")
    private String policies;

    @Column(name = "check_in_from")
    private LocalTime checkInFrom;

    @Column(name = "check_out_by")
    private LocalTime checkOutBy;

    @Column(nullable = false, length = 3)
    private String currency = "MNT";

    @Enumerated(EnumType.STRING)
    @Column(name = "cancellation_policy", nullable = false, length = 16)
    private CancellationPolicy cancellationPolicy = CancellationPolicy.MODERATE;

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
    @OneToMany(mappedBy = "hotel", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder asc, uploadedAt asc")
    private List<HotelPhoto> photos = new ArrayList<>();

    @BatchSize(size = 30)
    @OneToMany(mappedBy = "hotel", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder asc, name asc")
    private List<RoomType> roomTypes = new ArrayList<>();

    protected Hotel() {
    }

    public Hotel(Organization organization, String name, String city) {
        this.organization = organization;
        this.name = name;
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
     * What still blocks submitting for review.
     *
     * <p>A hotel additionally needs at least one sellable room type: unlike a
     * house, the hotel itself is not the thing being booked.
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
        if (addressLine == null || addressLine.isBlank()) {
            problems.add("A street address is required");
        }
        if (photos.isEmpty()) {
            problems.add("At least one photo is required");
        }
        if (roomTypes.stream().noneMatch(RoomType::isSellable)) {
            problems.add("At least one active room type with a price is required");
        }
        return problems;
    }

    public Set<Amenity> amenitySet() {
        Set<Amenity> parsed = EnumSet.noneOf(Amenity.class);
        for (String name : amenities) {
            try {
                parsed.add(Amenity.parse(name));
            } catch (IllegalArgumentException ignored) {
                // A renamed amenity should not break the whole hotel.
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

    public void addPhoto(HotelPhoto photo) {
        photos.add(photo);
        if (photos.size() == 1) {
            photo.markAsCover(true);
        }
    }

    public void addRoomType(RoomType roomType) {
        roomTypes.add(roomType);
    }

    /** Whether this hotel belongs to the given organization. */
    public boolean belongsTo(UUID organizationId) {
        return organization != null && organization.getId().equals(organizationId);
    }

    public UUID getId() {
        return id;
    }

    public Organization getOrganization() {
        return organization;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getStarRating() {
        return starRating;
    }

    public void setStarRating(Integer starRating) {
        this.starRating = starRating;
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

    public String getPolicies() {
        return policies;
    }

    public void setPolicies(String policies) {
        this.policies = policies;
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

    public String getCurrency() {
        return currency;
    }

    public CancellationPolicy getCancellationPolicy() {
        return cancellationPolicy;
    }

    public void setCancellationPolicy(CancellationPolicy cancellationPolicy) {
        this.cancellationPolicy = cancellationPolicy;
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

    public List<HotelPhoto> getPhotos() {
        return photos;
    }

    public List<RoomType> getRoomTypes() {
        return roomTypes;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof Hotel hotel && id != null && id.equals(hotel.id);
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
