package mn.innex.stay.hotel.repo;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import mn.innex.stay.hotel.domain.RoomInventoryDay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoomInventoryDayRepository extends JpaRepository<RoomInventoryDay, UUID> {

    /** Half-open range, matching how nights are counted. */
    @Query("""
            select i from RoomInventoryDay i
            where i.roomType.id = :roomTypeId and i.day >= :from and i.day < :toExclusive
            order by i.day
            """)
    List<RoomInventoryDay> findInRange(@Param("roomTypeId") UUID roomTypeId,
                                       @Param("from") LocalDate from,
                                       @Param("toExclusive") LocalDate toExclusive);

    /** One query for the whole date-by-room-type matrix. */
    @Query("""
            select i from RoomInventoryDay i
            where i.roomType.id in :roomTypeIds and i.day >= :from and i.day < :toExclusive
            order by i.roomType.id, i.day
            """)
    List<RoomInventoryDay> findInRangeForRoomTypes(@Param("roomTypeIds") Collection<UUID> roomTypeIds,
                                                   @Param("from") LocalDate from,
                                                   @Param("toExclusive") LocalDate toExclusive);

    Optional<RoomInventoryDay> findByRoomTypeIdAndDay(UUID roomTypeId, LocalDate day);

    /**
     * Deletes rows in a range, returning those nights to the room type's defaults.
     * Rows with sold rooms are kept: deleting them would lose the count the
     * oversell guarantee depends on.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from RoomInventoryDay i
            where i.roomType.id = :roomTypeId and i.day >= :from and i.day < :toExclusive
              and i.bookedCount = 0
            """)
    int deleteResettableInRange(@Param("roomTypeId") UUID roomTypeId,
                                @Param("from") LocalDate from,
                                @Param("toExclusive") LocalDate toExclusive);

    /**
     * How far the stored rows depart from the room types' default capacity, and how
     * many room-nights are sold.
     *
     * <p>Capacity cannot be summed from these rows alone: a night with no row is
     * still sellable at {@code total_rooms}, so summing {@code available_count}
     * would count only the nights somebody happened to edit or book. The caller
     * therefore starts from nights x total_rooms and applies this delta.
     *
     * <p>The delta covers room types on sale only — an inactive one offers nothing
     * — while sold counts every row, because a booking taken while a room type was
     * still on sale remains a real, paid room-night.
     */
    @Query("""
            select coalesce(sum(case when i.roomType.status = mn.innex.stay.hotel.domain.RoomTypeStatus.ACTIVE
                                     then i.availableCount - i.roomType.totalRooms else 0 end), 0),
                   coalesce(sum(i.bookedCount), 0)
            from RoomInventoryDay i
            where i.roomType.hotel.id = :hotelId and i.day >= :from and i.day < :toExclusive
            """)
    List<Object[]> sumCapacityDeltaAndSold(@Param("hotelId") UUID hotelId,
                                           @Param("from") LocalDate from,
                                           @Param("toExclusive") LocalDate toExclusive);
}
