package mn.innex.stay.trust.service;

import java.util.UUID;

import mn.innex.stay.common.supply.ListingReviewGate;
import mn.innex.stay.common.supply.SupplyKind;
import org.springframework.stereotype.Component;

/** Answers the approval gate from the open-flag queue. */
@Component
public class ListingReviewGateAdapter implements ListingReviewGate {

    private final ListingFlagService flags;

    public ListingReviewGateAdapter(ListingFlagService flags) {
        this.flags = flags;
    }

    @Override
    public boolean isBlocked(SupplyKind kind, UUID supplyId) {
        return flags.hasOpenFlag(kind, supplyId);
    }
}
