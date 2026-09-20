package mn.innex.stay.trust.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import mn.innex.stay.common.ApiException;
import mn.innex.stay.common.audit.AuditAction;
import mn.innex.stay.common.audit.AuditService;
import mn.innex.stay.trust.domain.FlagStatus;
import mn.innex.stay.trust.domain.FlagType;
import mn.innex.stay.trust.domain.ListingFlag;
import mn.innex.stay.common.supply.SupplyKind;
import mn.innex.stay.common.supply.SupplySuspensionPort;
import mn.innex.stay.trust.repo.ListingFlagRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The queue a reviewer works through, and the two things an open flag does:
 * it stops the listing being approved, and it stops its money moving.
 *
 * <p>Machine suspicion and guest complaints land in the same queue deliberately.
 * They raise the same question — is this listing real? — and answering it once
 * should settle both.
 */
@Service
public class ListingFlagService {

    private static final Logger log = LoggerFactory.getLogger(ListingFlagService.class);

    private final ListingFlagRepository flags;
    private final PayoutService payouts;
    private final ObjectProvider<SupplySuspensionPort> suspension;
    private final AuditService auditService;

    public ListingFlagService(ListingFlagRepository flags, @Lazy PayoutService payouts,
                              ObjectProvider<SupplySuspensionPort> suspension,
                              AuditService auditService) {
        this.flags = flags;
        this.payouts = payouts;
        this.suspension = suspension;
        this.auditService = auditService;
    }

    /**
     * Records a concern and freezes anything owed on the listing.
     *
     * <p>Freezing first and asking later is the right way round: the money can
     * always be released after review, but it cannot be recalled once sent.
     */
    @Transactional
    public ListingFlag raise(SupplyKind kind, UUID supplyId, FlagType type, UUID raisedBy,
                             UUID bookingId, String summary, Map<String, Object> details) {
        ListingFlag flag = flags.save(
                new ListingFlag(kind, supplyId, type, raisedBy, bookingId, summary, details));
        int frozen = payouts.freezeForSupply(supplyId, "listing_flagged");
        log.warn("Flag {} raised on {} {} ({}), froze {} payout(s)",
                flag.getId(), kind, supplyId, type, frozen);
        return flag;
    }

    /** True while something on this listing is unresolved, which blocks approval. */
    @Transactional(readOnly = true)
    public boolean hasOpenFlag(SupplyKind kind, UUID supplyId) {
        return flags.existsBySupplyKindAndSupplyIdAndStatus(kind, supplyId, FlagStatus.OPEN);
    }

    @Transactional(readOnly = true)
    public Page<ListingFlag> list(List<FlagStatus> statuses, Pageable pageable) {
        return flags.findByStatusIn(
                statuses == null || statuses.isEmpty() ? List.of(FlagStatus.OPEN) : statuses,
                pageable);
    }

    /**
     * Decides a flag.
     *
     * @param outcome {@link FlagStatus#DISMISSED} releases the hold on the
     *                listing's payouts once nothing else is open against it;
     *                {@link FlagStatus#UPHELD} leaves them frozen and takes the
     *                listing off sale
     */
    @Transactional
    public ListingFlag resolve(UUID adminId, UUID flagId, FlagStatus outcome, String note,
                               String ip) {
        if (outcome != FlagStatus.DISMISSED && outcome != FlagStatus.UPHELD) {
            throw ApiException.badRequest("invalid_outcome",
                    "A flag is either dismissed or upheld");
        }
        ListingFlag flag = flags.findByIdAndStatus(flagId, FlagStatus.OPEN)
                .orElseThrow(() -> ApiException.notFound("flag_not_found",
                        "No open flag with that id"));

        flag.resolve(outcome, adminId, note);
        flags.save(flag);

        if (outcome == FlagStatus.DISMISSED
                && !hasOpenFlag(flag.getSupplyKind(), flag.getSupplyId())) {
            // Only the last open flag unfreezes the money; clearing one of three
            // proves nothing.
            payouts.unfreezeForSupply(flag.getSupplyId());
        } else if (outcome == FlagStatus.UPHELD) {
            // Freezing the money is not enough on its own. A listing confirmed to
            // be fraudulent that stays bookable keeps taking guests, and each of
            // them loses a trip whether or not anyone is ever paid for it.
            SupplySuspensionPort port = suspension.getIfAvailable();
            if (port != null) {
                port.suspend(flag.getSupplyKind(), flag.getSupplyId(), adminId,
                        "Suspended after review: " + flag.getSummary(), ip);
            }
        }

        auditService.record(adminId, AuditAction.LISTING_FLAG_RESOLVED, "ListingFlag", flagId,
                Map.of("outcome", outcome.name(), "type", flag.getType().name(),
                        "supplyId", flag.getSupplyId().toString()), ip);
        return flag;
    }
}
