package mn.innex.stay.trust.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;

import mn.innex.stay.listing.storage.ObjectStorage;
import mn.innex.stay.trust.domain.FlagType;
import mn.innex.stay.trust.domain.PhotoFingerprint;
import mn.innex.stay.common.supply.SupplyKind;
import mn.innex.stay.trust.repo.PhotoFingerprintRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fingerprints every published photo and raises a flag when one turns up under
 * two different accounts.
 *
 * <p>This catches the cheapest scam there is: register, take photographs from a
 * real listing, publish, collect. It is a signal for a human, never a verdict —
 * hosts do relist their own places, hotel groups do share photography, and a
 * determined thief can re-crop past any perceptual hash. What it reliably does
 * is stop the lazy version, and give a reviewer the matching listing to compare
 * against instead of a hunch.
 *
 * <p>Never throws into the upload path. A photo that cannot be fingerprinted is
 * a photo without a fingerprint, not a failed upload: refusing someone's
 * legitimate photo because an image decoder tripped is a worse outcome than
 * missing one comparison.
 */
@Service
public class PhotoFingerprintService {

    private static final Logger log = LoggerFactory.getLogger(PhotoFingerprintService.class);

    /**
     * Bits of difference still counted as the same picture. Ten is the usual
     * working value for a 64-bit dHash: it survives re-encoding, resizing and
     * mild cropping, while unrelated photographs sit around 32 apart.
     */
    private static final int MAX_DISTANCE = 10;

    /** Enough matches to show a reviewer the pattern; the rest add nothing. */
    private static final int MAX_MATCHES = 5;

    private final PhotoFingerprintRepository fingerprints;
    private final ObjectStorage storage;
    private final ListingFlagService flags;

    public PhotoFingerprintService(PhotoFingerprintRepository fingerprints, ObjectStorage storage,
                                   ListingFlagService flags) {
        this.fingerprints = fingerprints;
        this.storage = storage;
        this.flags = flags;
    }

    /**
     * Hashes a freshly confirmed photo, stores it, and flags the listing when the
     * image already belongs to someone else.
     *
     * @param ownerUserId the account publishing it, which is what makes a match
     *                    suspicious rather than ordinary
     */
    @Transactional
    public void fingerprint(UUID photoId, SupplyKind kind, UUID supplyId, UUID ownerUserId,
                            String storageKey) {
        OptionalLong hash = hashOf(storageKey);
        if (hash.isEmpty()) {
            return;
        }

        List<PhotoFingerprint> matches = fingerprints.findSimilarByAnotherOwner(
                hash.getAsLong(), ownerUserId, MAX_DISTANCE, MAX_MATCHES);
        fingerprints.save(new PhotoFingerprint(photoId, kind, supplyId, ownerUserId,
                hash.getAsLong()));

        if (matches.isEmpty()) {
            return;
        }

        log.warn("Photo {} on {} {} matches {} photo(s) published by another account",
                photoId, kind, supplyId, matches.size());
        flags.raise(kind, supplyId, FlagType.DUPLICATE_PHOTO, null, null,
                "A photo here also appears on %d listing(s) owned by another account"
                        .formatted(matches.size()),
                evidence(photoId, hash.getAsLong(), matches));
    }

    /** Forgets a deleted photo, so removing it clears the evidence trail with it. */
    @Transactional
    public void forget(UUID photoId) {
        fingerprints.deleteByPhotoId(photoId);
    }

    private OptionalLong hashOf(String storageKey) {
        try {
            return storage.read(storageKey)
                    .map(bytes -> {
                        try (var stream = new ByteArrayInputStream(bytes)) {
                            return PerceptualHash.of(stream);
                        } catch (IOException ex) {
                            log.warn("Could not read image {} for fingerprinting", storageKey, ex);
                            return OptionalLong.empty();
                        }
                    })
                    .orElseGet(OptionalLong::empty);
        } catch (RuntimeException ex) {
            // Storage being unavailable must not cost someone their upload.
            log.warn("Could not fetch {} for fingerprinting", storageKey, ex);
            return OptionalLong.empty();
        }
    }

    private Map<String, Object> evidence(UUID photoId, long hash, List<PhotoFingerprint> matches) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("photoId", photoId.toString());
        details.put("matches", matches.stream()
                .map(match -> Map.of(
                        "photoId", match.getPhotoId().toString(),
                        "supplyKind", match.getSupplyKind().name(),
                        "supplyId", match.getSupplyId().toString(),
                        "ownerUserId", match.getOwnerUserId().toString(),
                        "distance", PerceptualHash.distance(hash, match.getPhash())))
                .toList());
        return details;
    }
}
