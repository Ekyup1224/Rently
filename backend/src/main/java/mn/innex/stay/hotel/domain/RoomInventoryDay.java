package mn.innex.stay.hotel.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import mn.innex.stay.common.Money;
import org.hibernate.annotations.UuidGenerator;

/**
 * One night of one room type: how many rooms are for sale, how many are sold, and
 * what they cost.
 *
 * <p>{@code bookedCount} is <strong>maintained by a database trigger</strong> from
 * the bookings table, not by this application. Nothing in Java should ever write
 * it: it is derived state that exists so the
 * {@code ck_room_inventory_not_oversold} CHECK can make overselling impossible,
 * and hand-editing it would break the guarantee it exists to provide.
 *
 * <p>A night with no row at all is sellable at the room type's {@code totalRooms}
 * and base price, so a hotel can start selling without filling in a calendar.
 * Rows appear where the hotel deviates, or are materialized by the trigger when a
 * booking needs one to count against.
 */
@Entity
@Table(name = "room_inventory_day")
public class RoomInventoryDay {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_type_id", nullable = false)
    private RoomType roomType;

    @Column(name = "day", nullable = false)
    private LocalDate day;

    /** Rooms offered for sale this night. Cannot be set below what is already sold. */
    @Column(name = "available_count", nullable = false)
    private int availableCount;

    /**
     * Rooms sold this night. Read-only here — the trigger owns it.
     */
    @Column(name = "booked_count", nullable = false, insertable = false, updatable = false)
    private int bookedCount;

    @Column(name = "rate_override", precision = 14, scale = 2)
    private BigDecimal rateOverride;

    /**
     * Closes the night to new bookings while leaving existing ones alone, which is
     * why it cannot be a database constraint — the booking service enforces it.
     */
    @Column(name = "stop_sell", nullable = false)
    private boolean stopSell;

    @Column(name = "min_stay_nights")
    private Integer minStayNights;

    protected RoomInventoryDay() {
    }

    public RoomInventoryDay(RoomType roomType, LocalDate day, int availableCount) {
        this.roomType = roomType;
        this.day = day;
        this.availableCount = availableCount;
    }

    /** Rooms still sellable this night, ignoring {@link #isStopSell()}. */
    public int remainingCount() {
        return Math.max(0, availableCount - bookedCount);
    }

    /** Whether {@code rooms} more rooms could be sold for this night. */
    public boolean canSell(int rooms) {
        return !stopSell && remainingCount() >= rooms;
    }

    /** True when the row no longer differs from the room type's defaults. */
    public boolean matchesDefaults(int totalRooms) {
        return availableCount == totalRooms && rateOverride == null && !stopSell
                && minStayNights == null && bookedCount == 0;
    }

    public UUID getId() {
        return id;
    }

    public RoomType getRoomType() {
        return roomType;
    }

    public LocalDate getDay() {
        return day;
    }

    public int getAvailableCount() {
        return availableCount;
    }

    public void setAvailableCount(int availableCount) {
        this.availableCount = availableCount;
    }

    public int getBookedCount() {
        return bookedCount;
    }

    public BigDecimal getRateOverride() {
        return rateOverride;
    }

    public void setRateOverride(BigDecimal rateOverride) {
        this.rateOverride = rateOverride == null ? null : Money.of(rateOverride);
    }

    public boolean isStopSell() {
        return stopSell;
    }

    public void setStopSell(boolean stopSell) {
        this.stopSell = stopSell;
    }

    public Integer getMinStayNights() {
        return minStayNights;
    }

    public void setMinStayNights(Integer minStayNights) {
        this.minStayNights = minStayNights;
    }
}
