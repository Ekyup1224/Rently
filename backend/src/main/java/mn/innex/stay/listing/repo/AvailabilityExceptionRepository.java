package mn.innex.stay.listing.repo;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import mn.innex.stay.listing.domain.AvailabilityException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AvailabilityExceptionRepository extends JpaRepository<AvailabilityException, UUID> {

    /** Half-open range: {@code from} inclusive, {@code toExclusive} exclusive, matching a stay's nights. */
    @Query("""
            select a from AvailabilityException a
            where a.property.id = :propertyId and a.day >= :from and a.day < :toExclusive
            order by a.day
            """)
    List<AvailabilityException> findInRange(@Param("propertyId") UUID propertyId,
                                            @Param("from") LocalDate from,
                                            @Param("toExclusive") LocalDate toExclusive);

    Optional<AvailabilityException> findByPropertyIdAndDay(UUID propertyId, LocalDate day);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from AvailabilityException a
            where a.property.id = :propertyId and a.day >= :from and a.day < :toExclusive
            """)
    int deleteInRange(@Param("propertyId") UUID propertyId,
                      @Param("from") LocalDate from,
                      @Param("toExclusive") LocalDate toExclusive);

    @Query("""
            select count(a) > 0 from AvailabilityException a
            where a.property.id = :propertyId and a.blocked = true
              and a.day >= :from and a.day < :toExclusive
            """)
    boolean hasBlockedDay(@Param("propertyId") UUID propertyId,
                          @Param("from") LocalDate from,
                          @Param("toExclusive") LocalDate toExclusive);
}
