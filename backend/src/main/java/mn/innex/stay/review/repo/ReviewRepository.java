package mn.innex.stay.review.repo;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import mn.innex.stay.review.domain.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewRepository extends JpaRepository<Review, UUID> {

    Optional<Review> findByBookingIdAndAuthorId(UUID bookingId, UUID authorId);

    /**
     * Both reviews of one stay, with the people attached.
     *
     * <p>The graph is not an optimisation: the controller turns these into DTOs
     * after the transaction has closed, so a lazy author is a 500 rather than a
     * second query.
     */
    @EntityGraph(attributePaths = {"author", "booking"})
    List<Review> findByBookingId(UUID bookingId);

    /** One review, ready to be returned. Same reason as above. */
    @EntityGraph(attributePaths = {"author", "booking"})
    @Query("select r from Review r where r.id = :id")
    Optional<Review> findByIdWithDetails(@Param("id") UUID id);

    /** The other side's review of the same stay, whoever wrote this one. */
    @Query("select r from Review r where r.booking.id = :bookingId and r.author.id <> :authorId")
    List<Review> findCounterparts(@Param("bookingId") UUID bookingId,
                                  @Param("authorId") UUID authorId);

    /** What a listing page shows: published, visible reviews of the place itself. */
    @EntityGraph(attributePaths = "author")
    @Query("""
            select r from Review r
            where r.supplyId = :supplyId
              and r.visible = true
              and r.status = mn.innex.stay.review.domain.ReviewStatus.PUBLISHED
            order by r.createdAt desc
            """)
    Page<Review> findVisibleForSupply(@Param("supplyId") UUID supplyId, Pageable pageable);

    /**
     * Reviews whose blind period has expired with nothing written back.
     *
     * <p>Publishing these is what stops one side's silence burying the other's
     * review for ever.
     */
    @Query("select r from Review r where r.visible = false and r.createdAt < :cutoff")
    List<Review> findPublishable(@Param("cutoff") Instant cutoff);

    /**
     * The average and count a listing caches.
     *
     * <p>Recomputed rather than incremented: an average nudged on every write
     * drifts, and hiding a review has to take it back out of the figure.
     */
    @Query("""
            select coalesce(avg(cast(r.rating as double)), 0), count(r)
            from Review r
            where r.supplyId = :supplyId
              and r.visible = true
              and r.status = mn.innex.stay.review.domain.ReviewStatus.PUBLISHED
              and r.subject = mn.innex.stay.review.domain.ReviewSubject.SUPPLY
            """)
    List<Object[]> ratingFor(@Param("supplyId") UUID supplyId);

    @EntityGraph(attributePaths = {"author", "booking"})
    Page<Review> findByStatusIn(java.util.Collection<mn.innex.stay.review.domain.ReviewStatus> statuses,
                                Pageable pageable);
}
