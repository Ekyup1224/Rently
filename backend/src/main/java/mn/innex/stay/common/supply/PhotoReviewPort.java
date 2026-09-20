package mn.innex.stay.common.supply;

import java.util.UUID;

/**
 * How a gallery tells the trust module that a photo was published, without
 * depending on it.
 *
 * <p>The trust module reads storage and listings to do its work, so the arrow
 * already runs trust → supply. This port is how the call gets back, and it is
 * why an upload is never blocked by a fingerprinting failure: the gallery hands
 * the fact over and carries on.
 */
public interface PhotoReviewPort {

    /**
     * A photo is now live on a listing.
     *
     * @param ownerUserId the account that published it, which is what makes a
     *                    match with someone else's photo suspicious
     */
    void photoPublished(UUID photoId, SupplyKind kind, UUID supplyId, UUID ownerUserId,
                        String storageKey);

    /** A photo was deleted; forget what was recorded about it. */
    void photoRemoved(UUID photoId);
}
