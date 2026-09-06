package mn.innex.stay.user.repo;

import java.util.Optional;
import java.util.UUID;

import mn.innex.stay.user.domain.Role;
import mn.innex.stay.user.domain.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

    /** Roles are fetched eagerly here: every auth path needs them to build the token claims. */
    @EntityGraph(attributePaths = {"roles", "roles.organization"})
    Optional<User> findByPhone(String phone);

    @EntityGraph(attributePaths = {"roles", "roles.organization"})
    Optional<User> findByEmail(String email);

    @EntityGraph(attributePaths = {"roles", "roles.organization"})
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdWithRoles(@Param("id") UUID id);

    boolean existsByPhone(String phone);

    boolean existsByEmail(String email);

    @Query("select count(r) > 0 from UserRole r where r.role = :role")
    boolean existsWithRole(@Param("role") Role role);
}
