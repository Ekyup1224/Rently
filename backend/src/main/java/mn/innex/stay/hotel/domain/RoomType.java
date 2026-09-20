package mn.innex.stay.hotel.domain;

import java.math.BigDecimal;
import java.time.Instant;
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
import mn.innex.stay.common.supply.Amenity;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

/**
 * A category of interchangeable room, and the unit a hotel guest actually books.
 *
 * <p>{@code totalRooms} is how many the hotel physically has, and is the default
 * availability for any night with no inventory row. Nightly availability can be
 * lower — rooms out of service, allotments held back for a tour operator — which
 * is what {@link RoomInventoryDay} records.
 */
@Entity
@Table(name = "room_types")
public class RoomType {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hotel_id", nullable = false)
    private Hotel hotel;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    /** Guests per room, not per booking: two rooms of capacity 2 sleep four. */
    @Column(nullable = false)
    private int capacity = 2;

    @Column(name = "bed_config", length = 160)
    private String bedConfig;

    @Column(name = "size_sqm")
    private Integer sizeSqm;

    @Column(name = "base_price", nullable = false, precision = 14, scale = 2)
    private BigDecimal basePrice = Money.ZERO;

    @Column(name = "total_rooms", nullable = false)
    private int totalRooms = 1;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<String> amenities = new ArrayList<>();

    @Column(name = "min_stay_nights", nullable = false)
    private int minStayNights = 1;

    @Column(name = "max_stay_nights")
    private Integer maxStayNights;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RoomTypeStatus status = RoomTypeStatus.ACTIVE;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    @BatchSize(size = 30)
    @OneToMany(mappedBy = "roomType", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder asc, uploadedAt asc")
    private List<RoomTypePhoto> photos = new ArrayList<>();

    protected RoomType() {
    }

    public RoomType(Hotel hotel, String name, int capacity, int totalRooms, BigDecimal basePrice) {
        this.hotel = hotel;
        this.name = name;
        this.capacity = capacity;
        this.totalRooms = totalRooms;
        this.basePrice = Money.of(basePrice);
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

    /** Whether this room type can currently be sold at all. */
    public boolean isSellable() {
        return status == RoomTypeStatus.ACTIVE && Money.isPositive(basePrice) && totalRooms > 0;
    }

    /** Maximum guests across {@code rooms} rooms of this type. */
    public int capacityFor(int rooms) {
        return capacity * rooms;
    }

    public Set<Amenity> amenitySet() {
        Set<Amenity> parsed = EnumSet.noneOf(Amenity.class);
        for (String name : amenities) {
            try {
                parsed.add(Amenity.parse(name));
            } catch (IllegalArgumentException ignored) {
                // Tolerate a renamed amenity rather than breaking the room type.
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

    public void addPhoto(RoomTypePhoto photo) {
        photos.add(photo);
        if (photos.size() == 1) {
            photo.markAsCover(true);
        }
    }

    public UUID getId() {
        return id;
    }

    public Hotel getHotel() {
        return hotel;
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

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public String getBedConfig() {
        return bedConfig;
    }

    public void setBedConfig(String bedConfig) {
        this.bedConfig = bedConfig;
    }

    public Integer getSizeSqm() {
        return sizeSqm;
    }

    public void setSizeSqm(Integer sizeSqm) {
        this.sizeSqm = sizeSqm;
    }

    public BigDecimal getBasePrice() {
        return basePrice;
    }

    public void setBasePrice(BigDecimal basePrice) {
        this.basePrice = Money.of(basePrice);
    }

    public int getTotalRooms() {
        return totalRooms;
    }

    public void setTotalRooms(int totalRooms) {
        this.totalRooms = totalRooms;
    }

    public List<String> getAmenities() {
        return amenities;
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

    public RoomTypeStatus getStatus() {
        return status;
    }

    public void setStatus(RoomTypeStatus status) {
        this.status = status;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<RoomTypePhoto> getPhotos() {
        return photos;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof RoomType roomType && id != null && id.equals(roomType.id);
    }

    @Override
    public int hashCode() {
        return id == null ? System.identityHashCode(this) : id.hashCode();
    }
}
