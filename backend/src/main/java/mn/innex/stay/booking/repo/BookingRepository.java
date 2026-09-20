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

    /**
     * A booking with everything a response needs, for either supply type. Both
     * branches are fetched because the graph cannot depend on the row's type.
     */
    @EntityGraph(attributePaths = {
            "property", "property.owner", "roomType", "roomType.hotel",
            "roomType.hotel.organization", "organization", "guest", "host"})
    @Query("select b from Booking b where b.id = :id")
    Optional<Booking> findByIdWithDetails(@Param("id") UUID id);

    Optional<Booking> findByReference(String reference);

    boolean existsByReference(String reference);

    @EntityGraph(attributePaths = {
            "property", "roomType", "roomType.hotel", "host"})
    Page<Booking> findByGuestId(UUID guestId, Pageable pageable);

    @EntityGraph(attributePaths = {
            "property", "roomType", "roomType.hotel", "host"})
    Page<Booking> findByGuestIdAndStatusIn(UUID guestId, Collection<BookingStatus> statuses,
                                           Pageable pageable);

    // Photo collections are loaded separately by BookingService: only one
    // collection may be fetched per query, and a booking can reach three.
    @EntityGraph(attributePaths = {
            "property", "roomType", "roomType.hotel", "guest"})
    Page<Booking> findByHostId(UUID hostId, Pageable pageable);

    @EntityGraph(attributePaths = {
            "property", "roomType", "roomType.hotel", "guest"})
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

    /** Reservations at one hotel, for the front desk. */
    @EntityGraph(attributePaths = {"roomType", "roomType.hotel", "guest"})
    @Query("""
            select b from Booking b
            where b.roomType.hotel.id = :hotelId
              and (:statuses is null or b.status in :statuses)
            """)
    Page<Booking> findByHotel(@Param("hotelId") UUID hotelId,
                              @Param("statuses") Collection<BookingStatus> statuses,
                              Pageable pageable);

    /** Reservations across several hotels, for staff assigned to more than one. */
    @EntityGraph(attributePaths = {"roomType", "roomType.hotel", "guest"})
    @Query("""
            select b from Booking b
            where b.roomType.hotel.id in :hotelIds
              and (:statuses is null or b.status in :statuses)
            """)
    Page<Booking> findByHotels(@Param("hotelIds") Collection<UUID> hotelIds,
                               @Param("statuses") Collection<BookingStatus> statuses,
                               Pageable pageable);

    /** Room revenue and reservation count for a hotel over a period, for reporting. */
    @Query("""
            select coalesce(sum(b.nightlySubtotal), 0), count(b)
            from Booking b
            where b.roomType.hotel.id = :hotelId
              and b.status in :statuses
              and b.checkOut > :from and b.checkIn < :toExclusive
            """)
    List<Object[]> sumHotelRoomRevenue(@Param("hotelId") UUID hotelId,
                                       @Param("statuses") Collection<BookingStatus> statuses,
                                       @Param("from") LocalDate from,
                                       @Param("toExclusive") LocalDate toExclusive);

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

    /**
     * Commission earned, stays booked and stays cancelled in a period.
     *
     * <p>Counted by when the booking was created rather than when the stay
     * happens: this answers "how did we do last month", and a stay booked in
     * March for August is March's result.
     */
    @Query("""
            select
                coalesce(sum(case when b.status in (mn.innex.stay.booking.domain.BookingStatus.CONFIRMED,
                                                    mn.innex.stay.booking.domain.BookingStatus.CHECKED_IN,
                                                    mn.innex.stay.booking.domain.BookingStatus.CHECKED_OUT,
                                                    mn.innex.stay.booking.domain.BookingStatus.COMPLETED)
                                  then b.hostCommission else 0 end), 0),
                count(case when b.status in (mn.innex.stay.booking.domain.BookingStatus.CONFIRMED,
                                             mn.innex.stay.booking.domain.BookingStatus.CHECKED_IN,
                                             mn.innex.stay.booking.domain.BookingStatus.CHECKED_OUT,
                                             mn.innex.stay.booking.domain.BookingStatus.COMPLETED)
                           then 1 end),
                count(case when b.status in (mn.innex.stay.booking.domain.BookingStatus.CANCELLED_BY_GUEST,
                                             mn.innex.stay.booking.domain.BookingStatus.CANCELLED_BY_HOST)
                           then 1 end)
            from Booking b
            where b.createdAt >= :from and b.createdAt < :to
            """)
    List<Object[]> sumCommissionAndCount(@Param("from") java.time.Instant from,
                                         @Param("to") java.time.Instant to);
}
