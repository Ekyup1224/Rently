package mn.innex.stay.trust.repo;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import mn.innex.stay.trust.domain.Payout;
import mn.innex.stay.trust.domain.PayoutStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PayoutRepository extends JpaRepository<Payout, UUID> {

    Optional<Payout> findByBookingId(UUID bookingId);

    /** The release sweep: everything whose hold has expired. */
    @Query("""
            select p from Payout p
            where p.status = mn.innex.stay.trust.domain.PayoutStatus.PENDING
              and p.releaseAfter <= :now
            order by p.releaseAfter
            """)
    List<Payout> findDue(@Param("now") Instant now);

    /**
     * Everything still owed on one listing: what a report freezes, and what
     * clearing that report has to unfreeze again. Paid and cancelled rows are
     * past either.
     *
     * <p>The joins are spelled out because JPQL turns a path like
     * {@code booking.roomType.hotel.id} into an inner join: written that way the
     * query silently drops every house payout, since a house booking has no room
     * type.
     */
    @Query("""
            select p from Payout p
            join p.booking b
            left join b.property prop
            left join b.roomType rt
            left join rt.hotel h
            where p.status in (mn.innex.stay.trust.domain.PayoutStatus.PENDING,
                               mn.innex.stay.trust.domain.PayoutStatus.RELEASED,
                               mn.innex.stay.trust.domain.PayoutStatus.BLOCKED)
              and (prop.id = :supplyId or rt.id = :supplyId or h.id = :supplyId)
            """)
    List<Payout> findUnsettledForSupply(@Param("supplyId") UUID supplyId);

    @EntityGraph(attributePaths = {"booking", "booking.property", "booking.roomType",
            "booking.roomType.hotel", "payee", "organization"})
    Page<Payout> findByPayeeId(UUID payeeId, Pageable pageable);

    @EntityGraph(attributePaths = {"booking", "booking.property", "booking.roomType",
            "booking.roomType.hotel", "payee", "organization"})
    Page<Payout> findByOrganizationId(UUID organizationId, Pageable pageable);

    @EntityGraph(attributePaths = {"booking", "booking.property", "booking.roomType",
            "booking.roomType.hotel", "payee", "organization"})
    Page<Payout> findByStatusIn(Collection<PayoutStatus> statuses, Pageable pageable);

    @EntityGraph(attributePaths = {"booking", "booking.property", "booking.roomType",
            "booking.roomType.hotel", "payee", "organization"})
    @Query("select p from Payout p where p.id = :id")
    Optional<Payout> findByIdWithDetails(@Param("id") UUID id);

    /** Released, unbatched money: what the next transfer run is made of. */
    @Query("""
            select p from Payout p
            join fetch p.payee
            where p.status = mn.innex.stay.trust.domain.PayoutStatus.RELEASED
              and p.batch is null
            order by p.releasedAt
            """)
    List<Payout> findReleasedWithoutBatch();

    @EntityGraph(attributePaths = {"booking", "payee", "organization"})
    List<Payout> findByBatchId(UUID batchId);
}
