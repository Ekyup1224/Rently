package mn.innex.stay.common.supply;

import java.util.UUID;

/**
 * Takes a listing off sale when a flag against it is upheld.
 *
 * <p>Freezing the money is not enough on its own: a listing confirmed to be
 * fraudulent that stays bookable keeps collecting guests, and each one is a
 * ruined trip even if nobody is ever paid for it. The flag queue therefore has
 * to be able to reach into the supply modules, which is what this port is for.
 */
public interface SupplySuspensionPort {

    /**
     * Suspends a listing. Implementations must be safe to call for a listing that
     * is already suspended or gone.
     *
     * @param actorId the admin whose decision this was, for the audit trail
     */
    void suspend(SupplyKind kind, UUID supplyId, UUID actorId, String reason, String ip);
}
