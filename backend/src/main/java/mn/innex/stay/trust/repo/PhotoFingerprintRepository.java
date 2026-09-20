package mn.innex.stay.trust.repo;

import java.util.List;
import java.util.UUID;

import mn.innex.stay.trust.domain.PhotoFingerprint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PhotoFingerprintRepository extends JpaRepository<PhotoFingerprint, UUID> {

    /**
     * Photos that look like this one but belong to somebody else.
     *
     * <p>Native, because the comparison is a Hamming distance: XOR the two hashes
     * and count the set bits. Postgres offers {@code bit_count} over {@code bit}
     * and {@code bytea} but not {@code bigint}, hence the cast to {@code bit(64)}.
     * Two images that differ by a crop, a filter or a re-encode land within a
     * handful of bits of each other, while unrelated photographs sit near 32.
     *
     * <p>The owner check is what makes the result meaningful. The same photo under
     * the same account is ordinary — a host relisting, a hotel using one set of
     * photography across its room types. Under a different account it is either
     * theft or two people claiming the same building, and both need a human.
     */
    @Query(value = """
            select * from photo_fingerprints f
            where f.owner_user_id <> :ownerUserId
              and bit_count(f.phash::bit(64) # (:phash)::bit(64)) <= :maxDistance
            order by bit_count(f.phash::bit(64) # (:phash)::bit(64))
            limit :limit
            """, nativeQuery = true)
    List<PhotoFingerprint> findSimilarByAnotherOwner(@Param("phash") long phash,
                                                     @Param("ownerUserId") UUID ownerUserId,
                                                     @Param("maxDistance") int maxDistance,
                                                     @Param("limit") int limit);

    @Modifying
    void deleteByPhotoId(UUID photoId);
}
