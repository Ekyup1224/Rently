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

    Optional<HostApplication> findByUserIdAndRequestedRoleAndStatus(
            UUID userId, Role requestedRole, HostApplicationStatus status);
}
