package mn.innex.stay.listing.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import mn.innex.stay.listing.domain.PropertyPhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PropertyPhotoRepository extends JpaRepository<PropertyPhoto, UUID> {

    List<PropertyPhoto> findByPropertyIdOrderBySortOrderAscUploadedAtAsc(UUID propertyId);

    Optional<PropertyPhoto> findByStorageKey(String storageKey);

    long countByPropertyId(UUID propertyId);

    /**
     * Clears the cover flag across a listing. Called before setting a new cover,
     * because the partial unique index permits only one.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update PropertyPhoto p set p.cover = false where p.property.id = :propertyId and p.cover = true")
    int clearCover(@Param("propertyId") UUID propertyId);
}
