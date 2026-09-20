package mn.innex.stay.trust.repo;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import mn.innex.stay.trust.domain.KycSubmission;
import mn.innex.stay.user.domain.KycStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KycSubmissionRepository extends JpaRepository<KycSubmission, UUID> {

    Optional<KycSubmission> findFirstByUserIdAndStatusOrderByCreatedAtDesc(UUID userId,
                                                                           KycStatus status);

    Optional<KycSubmission> findFirstByUserIdOrderByCreatedAtDesc(UUID userId);

    @EntityGraph(attributePaths = "user")
    Page<KycSubmission> findByStatusIn(Collection<KycStatus> statuses, Pageable pageable);
}
