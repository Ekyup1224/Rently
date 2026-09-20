package mn.innex.stay.hotel.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import mn.innex.stay.hotel.domain.HotelPhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HotelPhotoRepository extends JpaRepository<HotelPhoto, UUID> {

    List<HotelPhoto> findByHotelIdOrderBySortOrderAscUploadedAtAsc(UUID hotelId);

    Optional<HotelPhoto> findByStorageKey(String storageKey);

    long countByHotelId(UUID hotelId);

    /** The partial unique index permits only one cover, so clear before setting. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update HotelPhoto p set p.cover = false where p.hotel.id = :hotelId and p.cover = true")
    int clearCover(@Param("hotelId") UUID hotelId);
}
