package mn.innex.stay.listing.repo;

import java.util.Optional;
import java.util.UUID;

import mn.innex.stay.listing.domain.Property;
import mn.innex.stay.common.supply.SupplyStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

public interface PropertyRepository extends JpaRepository<Property, UUID>,
        JpaSpecificationExecutor<Property> {

    /** Photos are fetched with the listing: no caller of this ever wants it without them. */
    @EntityGraph(attributePaths = {"photos", "owner"})
    @Query("select p from Property p where p.id = :id")
    Optional<Property> findByIdWithPhotos(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"photos", "owner"})
    Page<Property> findByOwnerId(UUID ownerId, Pageable pageable);

    @EntityGraph(attributePaths = {"photos", "owner"})
    Page<Property> findByOwnerIdAndStatus(UUID ownerId, SupplyStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"photos", "owner"})
    Page<Property> findByStatus(SupplyStatus status, Pageable pageable);

    /**
     * The unfiltered admin listing, with the same graph as every other page query.
     *
     * <p>Overridden rather than inherited because the inherited one fetches photos
     * lazily, and the response serialises them after the session has closed — so
     * the admin's "all listings" view failed with a LazyInitializationException
     * the moment any listing had a photo, while the status-filtered view beside it
     * worked.
     */
    @Override
    @EntityGraph(attributePaths = {"photos", "owner"})
    Page<Property> findAll(Pageable pageable);

    long countByOwnerIdAndStatus(UUID ownerId, SupplyStatus status);

    /** How much supply is actually bookable: houses and apartments on sale. */
    @Query("select count(e) from Property e where e.status = mn.innex.stay.common.supply.SupplyStatus.APPROVED")
    long countLiveListings();

    /**
     * Writes back the cached rating. A direct update rather than a loaded entity,
     * because this runs from the review module and must not fight optimistic
     * locking with a host editing the same listing.
     */
    @Modifying
    @Query("update Property e set e.ratingAverage = :average, e.ratingCount = :count where e.id = :id")
    void updateRating(@Param("id") java.util.UUID id,
                      @Param("average") java.math.BigDecimal average,
                      @Param("count") int count);
}
