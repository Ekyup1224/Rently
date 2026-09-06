package mn.innex.stay.booking.repo;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import mn.innex.stay.booking.domain.Booking;
import mn.innex.stay.booking.domain.BookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

    @EntityGraph(attributePaths = {"property", "property.photos", "guest", "host"})
    @Query("select b from Booking b where b.id = :id")
    Optional<Booking> findByIdWithDetails(@Param("id") UUID id);

    Optional<Booking> findByReference(String reference);

    boolean existsByReference(String reference);

    @EntityGraph(attributePaths = {"property", "property.photos", "host"})
    Page<Booking> findByGuestId(UUID guestId, Pageable pageable);

    @EntityGraph(attributePaths = {"property", "property.photos", "host"})
    Page<Booking> findByGuestIdAndStatusIn(UUID guestId, Collection<BookingStatus> statuses,
                                           Pageable pageable);

    // property.photos is included because the reservation card renders a cover
    // image; without it, mapping the response throws once the session closes.
    @EntityGraph(attributePaths = {"property", "property.photos", "guest"})
    Page<Booking> findByHostId(UUID hostId, Pageable pageable);

    @EntityGraph(attributePaths = {"property", "property.photos", "guest"})
    Page<Booking> findByHostIdAndStatusIn(UUID hostId, Collection<BookingStatus> statuses,
                                          Pageable pageable);

    /**
     * Whether the dates are already taken. Advisory only — the authority is the
     * {@code bookings_no_property_overlap} exclusion constraint, which is what
     * actually holds under concurrency. This exists so the common case gets a
     * clear error instead of a constraint violation.
     */
    @Query("""
            select count(b) > 0 from Booking b
            where b.property.id = :propertyId
              and b.status in :occupying
              and b.checkIn < :checkOut and b.checkOut > :checkIn
              and (:excludeBookingId is null or b.id <> :excludeBookingId)
            """)
    boolean hasOverlap(@Param("propertyId") UUID propertyId,
                       @Param("checkIn") LocalDate checkIn,
                       @Param("checkOut") LocalDate checkOut,
                       @Param("occupying") Collection<BookingStatus> occupying,
                       @Param("excludeBookingId") UUID excludeBookingId);

    /** Occupied date ranges for a listing's public calendar. */
    @Query("""
            select b.checkIn, b.checkOut from Booking b
            where b.property.id = :propertyId
              and b.status in :occupying
              and b.checkOut > :from and b.checkIn < :toExclusive
            """)
    List<Object[]> findOccupiedRanges(@Param("propertyId") UUID propertyId,
                                      @Param("from") LocalDate from,
                                      @Param("toExclusive") LocalDate toExclusive,
                                      @Param("occupying") Collection<BookingStatus> occupying);

    /** Drives the expiry job: pending bookings whose deadline has passed. */
    @Query("""
            select b from Booking b
            where b.status in :statuses and b.expiresAt is not null and b.expiresAt < :now
            """)
    List<Booking> findExpired(@Param("statuses") Collection<BookingStatus> statuses,
                              @Param("now") Instant now);

    /** Bookings whose stay ends in the window, for host earnings aggregation. */
    List<Booking> findByHostIdAndStatusInAndCheckOutGreaterThanEqualAndCheckOutLessThan(
            UUID hostId, Collection<BookingStatus> statuses, LocalDate from, LocalDate toExclusive);

    /** Drives the completion job: stays whose checkout date has passed. */
    @Query("""
            select b from Booking b
            where b.status in :statuses and b.checkOut <= :today
            """)
    List<Booking> findCompletable(@Param("statuses") Collection<BookingStatus> statuses,
                                  @Param("today") LocalDate today);

    @Query("""
            select coalesce(sum(b.hostPayout), 0) from Booking b
            where b.host.id = :hostId and b.status in :statuses
              and b.checkOut >= :from and b.checkOut < :toExclusive
            """)
    java.math.BigDecimal sumHostPayout(@Param("hostId") UUID hostId,
                                       @Param("statuses") Collection<BookingStatus> statuses,
                                       @Param("from") LocalDate from,
                                       @Param("toExclusive") LocalDate toExclusive);
}
