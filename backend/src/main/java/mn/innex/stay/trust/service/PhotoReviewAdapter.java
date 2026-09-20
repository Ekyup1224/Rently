package mn.innex.stay.trust.service;

import java.util.UUID;

import mn.innex.stay.common.supply.PhotoReviewPort;
import mn.innex.stay.common.supply.SupplyKind;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Connects the galleries to fingerprinting.
 *
 * <p>Swallows failures on purpose. A photo that could not be checked is a gap in
 * the net, not a reason to reject an honest host's upload — and the same photo
 * is still in front of a reviewer before the listing can go live.
 */
@Component
public class PhotoReviewAdapter implements PhotoReviewPort {

    private static final Logger log = LoggerFactory.getLogger(PhotoReviewAdapter.class);

    private final PhotoFingerprintService fingerprints;

    public PhotoReviewAdapter(PhotoFingerprintService fingerprints) {
        this.fingerprints = fingerprints;
    }

    @Override
    public void photoPublished(UUID photoId, SupplyKind kind, UUID supplyId, UUID ownerUserId,
                               String storageKey) {
        try {
            fingerprints.fingerprint(photoId, kind, supplyId, ownerUserId, storageKey);
        } catch (RuntimeException ex) {
            log.error("Could not fingerprint photo {} on {} {}", photoId, kind, supplyId, ex);
        }
    }

    @Override
    public void photoRemoved(UUID photoId) {
        try {
            fingerprints.forget(photoId);
        } catch (RuntimeException ex) {
            log.error("Could not forget fingerprint for photo {}", photoId, ex);
        }
    }
}
