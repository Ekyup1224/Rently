package mn.innex.stay.user.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import mn.innex.stay.user.domain.Organization;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationRepository extends JpaRepository<Organization, UUID> {

    Optional<Organization> findByRegistrationNo(String registrationNo);

    boolean existsByRegistrationNo(String registrationNo);

    List<Organization> findByOwnerUserId(UUID ownerUserId);
}
