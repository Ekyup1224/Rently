package mn.innex.stay.hotel.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import mn.innex.stay.hotel.domain.HotelStaffAssignment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HotelStaffRepository extends JpaRepository<HotelStaffAssignment, UUID> {

    @EntityGraph(attributePaths = {"user"})
    List<HotelStaffAssignment> findByHotelId(UUID hotelId);

    Optional<HotelStaffAssignment> findByHotelIdAndUserId(UUID hotelId, UUID userId);

    /** The hotels a staff account is assigned to, for scoping their view. */
    @org.springframework.data.jpa.repository.Query(
            "select a.hotel.id from HotelStaffAssignment a where a.user.id = :userId")
    List<UUID> findHotelIdsForUser(@org.springframework.data.repository.query.Param("userId") UUID userId);

    boolean existsByHotelIdAndUserId(UUID hotelId, UUID userId);
}
