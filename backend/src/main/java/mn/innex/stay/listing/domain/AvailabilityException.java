package mn.innex.stay.listing.domain;

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
 * One day on which a listing deviates from its defaults: blocked off, priced
 * differently, or requiring a longer stay.
 *
 * <p>This is the spec's {@code AvailabilityDay} and {@code PricingOverride}
 * collapsed into one table, because they are the same idea. Days with no row are
 * available at the listing's base price, so a calendar costs rows only where the
 * owner has actually said something.
 *
 * <p>Dates taken by bookings are deliberately absent: those are derived from the
 * bookings table so the two can never disagree.
 */
@Entity
@Table(name = "property_availability")
public class AvailabilityException {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "property_id", nullable = false)
    private Property property;

    @Column(name = "day", nullable = false)
    private LocalDate day;

    @Column(name = "is_blocked", nullable = false)
    private boolean blocked;

    @Column(name = "price_override", precision = 14, scale = 2)
    private BigDecimal priceOverride;

    @Column(name = "min_stay_nights")
    private Integer minStayNights;

    protected AvailabilityException() {
    }

    public AvailabilityException(Property property, LocalDate day) {
        this.property = property;
        this.day = day;
    }

    /** True when the row no longer says anything and should be deleted rather than stored. */
    public boolean isEmpty() {
        return !blocked && priceOverride == null && minStayNights == null;
    }

    public UUID getId() {
        return id;
    }

    public Property getProperty() {
        return property;
    }

    public LocalDate getDay() {
        return day;
    }

    public boolean isBlocked() {
        return blocked;
    }

    public void setBlocked(boolean blocked) {
        this.blocked = blocked;
    }

    public BigDecimal getPriceOverride() {
        return priceOverride;
    }

    public void setPriceOverride(BigDecimal priceOverride) {
        this.priceOverride = priceOverride == null ? null : Money.of(priceOverride);
    }

    public Integer getMinStayNights() {
        return minStayNights;
    }

    public void setMinStayNights(Integer minStayNights) {
        this.minStayNights = minStayNights;
    }
}
