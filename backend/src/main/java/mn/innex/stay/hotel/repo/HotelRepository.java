package mn.innex.stay.hotel.repo;

import java.util.Optional;
import java.util.UUID;

import mn.innex.stay.common.supply.SupplyStatus;
import mn.innex.stay.hotel.domain.Hotel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

public interface HotelRepository extends JpaRepository<Hotel, UUID> {

    /**
     * A hotel with its organization.
     *
     * <p>Photos and room types are deliberately <em>not</em> in the graph: a hotel
     * has three collections, and fetching more than one in a single query is
     * either rejected by Hibernate or produces a cartesian product. They are
     * batch-loaded inside the transaction by {@code HotelService} instead, which
     * costs a couple of extra queries and no row multiplication.
     */
    @EntityGraph(attributePaths = {"organization"})
    @Query("select h from Hotel h where h.id = :id")
    Optional<Hotel> findByIdWithDetails(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"organization"})
    Page<Hotel> findByOrganizationId(UUID organizationId, Pageable pageable);

    @EntityGraph(attributePaths = {"organization"})
    Page<Hotel> findByOrganizationIdAndStatus(UUID organizationId, SupplyStatus status,
                                              Pageable pageable);

    @EntityGraph(attributePaths = {"organization"})
    Page<Hotel> findByStatus(SupplyStatus status, Pageable pageable);

    /**
     * The unfiltered admin listing, with the same graph as the filtered one.
     *
     * <p>Not a crash here — the service initialises each hotel inside its own
     * transaction — but without the graph it is one query per row for the
     * organisation, which is the same mistake in a slower costume.
     */
    @Override
    @EntityGraph(attributePaths = {"organization"})
    Page<Hotel> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"organization"})
    Page<Hotel> findByIdIn(java.util.Collection<UUID> ids, Pageable pageable);

    long countByOrganizationId(UUID organizationId);

    /** How much supply is actually bookable: hotels on sale. */
    @Query("select count(e) from Hotel e where e.status = mn.innex.stay.common.supply.SupplyStatus.APPROVED")
    long countLiveHotels();

    /**
     * Writes back the cached rating. A direct update rather than a loaded entity,
     * because this runs from the review module and must not fight optimistic
     * locking with a host editing the same listing.
     */
    @Modifying
    @Query("update Hotel e set e.ratingAverage = :average, e.ratingCount = :count where e.id = :id")
    void updateRating(@Param("id") java.util.UUID id,
                      @Param("average") java.math.BigDecimal average,
                      @Param("count") int count);
}
