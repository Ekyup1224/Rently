package mn.innex.stay.payment.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import mn.innex.stay.payment.domain.Payment;
import mn.innex.stay.payment.domain.PaymentIntent;
import mn.innex.stay.payment.domain.PaymentProvider;
import mn.innex.stay.payment.domain.PaymentRecordStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    Optional<Payment> findByProviderAndProviderRef(PaymentProvider provider, String providerRef);

    List<Payment> findByBookingIdOrderByCreatedAtDesc(UUID bookingId);

    /** The settled charge for a booking, which a refund reverses. */
    Optional<Payment> findFirstByBookingIdAndIntentAndStatusOrderByPaidAtDesc(
            UUID bookingId, PaymentIntent intent, PaymentRecordStatus status);

    List<Payment> findByBookingIdAndIntentAndStatus(
            UUID bookingId, PaymentIntent intent, PaymentRecordStatus status);
}
