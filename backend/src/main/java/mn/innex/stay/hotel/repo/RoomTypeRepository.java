package mn.innex.stay.hotel.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import mn.innex.stay.hotel.domain.RoomType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoomTypeRepository extends JpaRepository<RoomType, UUID> {

    @EntityGraph(attributePaths = {"photos", "hotel", "hotel.organization"})
    @Query("select r from RoomType r where r.id = :id")
    Optional<RoomType> findByIdWithDetails(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"photos", "hotel", "hotel.organization"})
    List<RoomType> findByHotelIdOrderBySortOrderAscNameAsc(UUID hotelId);

    long countByHotelId(UUID hotelId);

    /** Rooms a hotel can sell on any given night, before per-day adjustments. */
    @Query("""
            select coalesce(sum(r.totalRooms), 0) from RoomType r
            where r.hotel.id = :hotelId
              and r.status = mn.innex.stay.hotel.domain.RoomTypeStatus.ACTIVE
            """)
    int sumRoomsOnSale(@Param("hotelId") UUID hotelId);
}
