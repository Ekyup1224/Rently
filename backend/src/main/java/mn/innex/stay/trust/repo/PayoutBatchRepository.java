package mn.innex.stay.trust.repo;

import java.util.UUID;

import mn.innex.stay.trust.domain.PayoutBatch;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayoutBatchRepository extends JpaRepository<PayoutBatch, UUID> {

    Page<PayoutBatch> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
