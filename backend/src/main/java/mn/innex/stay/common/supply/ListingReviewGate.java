package mn.innex.stay.common.supply;

import java.util.UUID;

/**
 * Asks the trust module whether a listing is clear to be approved.
 *
 * <p>An open flag — a photo that belongs to someone else, or a guest saying the
 * place does not exist — has to stop approval, or the queue quietly launders the
 * suspicion away: the reviewer looking at the listing is often not the one who
 * would resolve the flag.
 */
public interface ListingReviewGate {

    /** @return true when something unresolved stands against this listing */
    boolean isBlocked(SupplyKind kind, UUID supplyId);
}
