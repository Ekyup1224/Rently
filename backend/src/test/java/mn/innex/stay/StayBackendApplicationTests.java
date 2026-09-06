package mn.innex.stay;

import static org.assertj.core.api.Assertions.assertThat;

import mn.innex.stay.user.domain.Role;
import mn.innex.stay.user.repo.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Verifies the application context builds against a real database — which means
 * Flyway migrated cleanly and every JPA entity matches the migrated schema, since
 * {@code ddl-auto: validate} fails startup otherwise.
 */
class StayBackendApplicationTests extends IntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("context loads, migrations apply, entities match the schema")
    void contextLoads() {
        assertThat(userRepository.count()).isPositive();
    }

    @Test
    @DisplayName("a SUPER_ADMIN is seeded on first start")
    void seedsSuperAdmin() {
        assertThat(userRepository.existsWithRole(Role.SUPER_ADMIN)).isTrue();
    }
}
