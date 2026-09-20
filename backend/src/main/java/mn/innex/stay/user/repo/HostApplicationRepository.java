package mn.innex.stay.user.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import mn.innex.stay.user.domain.HostApplication;
import mn.innex.stay.user.domain.HostApplicationStatus;
import mn.innex.stay.user.domain.Role;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HostApplicationRepository extends JpaRepository<HostApplication, UUID> {

    List<HostApplication> findByUserIdOrderByCreatedAtDesc(UUID userId);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"user"})
    org.springframework.data.domain.Page<HostApplication> findByStatus(
            HostApplicationStatus status, org.springframework.data.domain.Pageable pageable);

    /**
     * The unfiltered queue. Overridden only to carry the same fetch graph as
     * {@link #findByStatus}: the response names the applicant, and without this
     * the "all" tab would load each one separately.
     */
    @Override
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"user"})
    org.springframework.data.domain.Page<HostApplication> findAll(
            org.springframework.data.domain.Pageable pageable);

    Optional<HostApplication> findByUserIdAndRequestedRoleAndStatus(
            UUID userId, Role requestedRole, HostApplicationStatus status);
}
