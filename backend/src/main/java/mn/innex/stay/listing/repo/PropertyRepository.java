package mn.innex.stay.listing.repo;

import java.util.Optional;
import java.util.UUID;

import mn.innex.stay.listing.domain.Property;
import mn.innex.stay.listing.domain.PropertyStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
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
    Page<Property> findByOwnerIdAndStatus(UUID ownerId, PropertyStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"photos", "owner"})
    Page<Property> findByStatus(PropertyStatus status, Pageable pageable);

    long countByOwnerIdAndStatus(UUID ownerId, PropertyStatus status);
}
