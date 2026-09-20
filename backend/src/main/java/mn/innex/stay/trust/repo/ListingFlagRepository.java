package mn.innex.stay.trust.repo;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import mn.innex.stay.trust.domain.FlagStatus;
import mn.innex.stay.trust.domain.ListingFlag;
import mn.innex.stay.common.supply.SupplyKind;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ListingFlagRepository extends JpaRepository<ListingFlag, UUID> {

    Page<ListingFlag> findByStatusIn(Collection<FlagStatus> statuses, Pageable pageable);

    boolean existsBySupplyKindAndSupplyIdAndStatus(SupplyKind supplyKind, UUID supplyId,
                                                   FlagStatus status);

    List<ListingFlag> findBySupplyKindAndSupplyIdAndStatus(SupplyKind supplyKind, UUID supplyId,
                                                           FlagStatus status);

    /** Any open flag anywhere on this listing, whichever kind raised it. */
    List<ListingFlag> findBySupplyIdInAndStatus(Collection<UUID> supplyIds, FlagStatus status);

    Optional<ListingFlag> findByIdAndStatus(UUID id, FlagStatus status);
}
