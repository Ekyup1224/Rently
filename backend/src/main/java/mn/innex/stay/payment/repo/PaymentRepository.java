package mn.innex.stay.payment.repo;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import mn.innex.stay.payment.domain.Payment;
import mn.innex.stay.payment.domain.PaymentIntent;
import mn.innex.stay.payment.domain.PaymentProvider;
import mn.innex.stay.payment.domain.PaymentRecordStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    Optional<Payment> findByProviderAndProviderRef(PaymentProvider provider, String providerRef);

    List<Payment> findByBookingIdOrderByCreatedAtDesc(UUID bookingId);

    /** The settled charge for a booking, which a refund reverses. */
    Optional<Payment> findFirstByBookingIdAndIntentAndStatusOrderByPaidAtDesc(
            UUID bookingId, PaymentIntent intent, PaymentRecordStatus status);

    List<Payment> findByBookingIdAndIntentAndStatus(
            UUID bookingId, PaymentIntent intent, PaymentRecordStatus status);

    /**
     * The oversight query: every transaction, narrowed by whatever the operator
     * happens to know. Each filter is optional, so the same query serves "show me
     * today's failures" and "find this booking reference".
     *
     * @param query matches a booking reference or a provider reference — the two
     *              things anyone chasing a payment actually has to hand. Cast
     *              explicitly: a null String parameter reaches Postgres untyped,
     *              and {@code lower()} then fails on a bytea it cannot read.
     */
    @Query("""
            select p from Payment p
            join fetch p.booking b
            where (:status is null or p.status = :status)
              and (:provider is null or p.provider = :provider)
              and (:intent is null or p.intent = :intent)
              and (cast(:from as instant) is null or p.createdAt >= :from)
              and (cast(:to as instant) is null or p.createdAt < :to)
              and (cast(:query as string) is null
                   or lower(b.reference) like lower(concat('%', cast(:query as string), '%'))
                   or lower(coalesce(p.providerRef, ''))
                       like lower(concat('%', cast(:query as string), '%')))
            """)
    Page<Payment> search(@Param("status") PaymentRecordStatus status,
                         @Param("provider") PaymentProvider provider,
                         @Param("intent") PaymentIntent intent,
                         @Param("from") Instant from,
                         @Param("to") Instant to,
                         @Param("query") String query,
                         Pageable pageable);

    @EntityGraph(attributePaths = {"booking", "booking.guest"})
    @Query("select p from Payment p where p.id = :id")
    Optional<Payment> findByIdWithBooking(@Param("id") UUID id);

    /**
     * Money in, money back, and how many charges settled, over a period.
     *
     * <p>Counted from settled payments rather than from bookings: a booking that
     * was never paid is not revenue, and a refund has to come back off the top.
     */
    @Query("""
            select
                coalesce(sum(case when p.intent = mn.innex.stay.payment.domain.PaymentIntent.CHARGE
                                  then p.amount else 0 end), 0),
                coalesce(sum(case when p.intent = mn.innex.stay.payment.domain.PaymentIntent.REFUND
                                  then p.amount else 0 end), 0),
                count(case when p.intent = mn.innex.stay.payment.domain.PaymentIntent.CHARGE
                           then 1 end)
            from Payment p
            where p.status = mn.innex.stay.payment.domain.PaymentRecordStatus.SUCCEEDED
              and p.paidAt >= :from and p.paidAt < :to
            """)
    List<Object[]> sumSettled(@Param("from") Instant from, @Param("to") Instant to);
}
