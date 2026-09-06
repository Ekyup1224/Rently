package mn.innex.stay.booking.repo;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import mn.innex.stay.booking.domain.CommissionRule;
import mn.innex.stay.booking.domain.CommissionScope;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommissionRuleRepository extends JpaRepository<CommissionRule, UUID> {

    /**
     * The rule in force for a scope at a point in time. Ordered newest-first so a
     * misconfiguration with two overlapping rules resolves to the more recent one
     * rather than failing.
     */
    @Query("""
            select r from CommissionRule r
            where r.scope = :scope
              and (:category is null or r.category = :category)
              and r.effectiveFrom <= :at
              and (r.effectiveTo is null or r.effectiveTo > :at)
            order by r.effectiveFrom desc
            """)
    List<CommissionRule> findEffective(@Param("scope") CommissionScope scope,
                                       @Param("category") String category,
                                       @Param("at") Instant at);

    @Query("""
            select r from CommissionRule r
            where r.scope = 'GLOBAL' and r.effectiveFrom <= :at
              and (r.effectiveTo is null or r.effectiveTo > :at)
            order by r.effectiveFrom desc
            limit 1
            """)
    Optional<CommissionRule> findEffectiveGlobal(@Param("at") Instant at);

    List<CommissionRule> findAllByOrderByEffectiveFromDesc();
}
