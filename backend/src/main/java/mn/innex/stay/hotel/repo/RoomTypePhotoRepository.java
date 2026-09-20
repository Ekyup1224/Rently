package mn.innex.stay.hotel.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import mn.innex.stay.hotel.domain.RoomTypePhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoomTypePhotoRepository extends JpaRepository<RoomTypePhoto, UUID> {

    List<RoomTypePhoto> findByRoomTypeIdOrderBySortOrderAscUploadedAtAsc(UUID roomTypeId);

    Optional<RoomTypePhoto> findByStorageKey(String storageKey);

    long countByRoomTypeId(UUID roomTypeId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update RoomTypePhoto p set p.cover = false where p.roomType.id = :roomTypeId and p.cover = true")
    int clearCover(@Param("roomTypeId") UUID roomTypeId);
}
