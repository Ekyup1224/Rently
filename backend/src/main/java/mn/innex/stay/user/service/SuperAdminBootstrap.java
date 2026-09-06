package mn.innex.stay.user.service;

import java.util.Map;

import mn.innex.stay.common.Emails;
import mn.innex.stay.common.PhoneNumbers;
import mn.innex.stay.common.audit.AuditAction;
import mn.innex.stay.common.audit.AuditService;
import mn.innex.stay.user.domain.Role;
import mn.innex.stay.user.domain.User;
import mn.innex.stay.user.repo.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the first SUPER_ADMIN so a fresh database is usable. Runs only when no
 * SUPER_ADMIN grant exists anywhere, which makes it a no-op on every subsequent
 * start and safe to leave enabled.
 *
 * <p>The seeded account is created already phone-verified and active — there is
 * no one to approve it and no SMS gateway in a fresh environment.
 */
@Component
public class SuperAdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SuperAdminBootstrap.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final BootstrapProperties properties;

    public SuperAdminBootstrap(UserRepository userRepository, PasswordEncoder passwordEncoder,
                               AuditService auditService, BootstrapProperties properties) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        BootstrapProperties.SuperAdmin config = properties.superAdmin();
        if (config == null || !config.enabled()) {
            return;
        }
        if (userRepository.existsWithRole(Role.SUPER_ADMIN)) {
            return;
        }

        String phone = PhoneNumbers.normalize(config.phone());
        User admin = userRepository.findByPhone(phone).orElseGet(() -> User.createWithPhone(phone, "mn"));
        admin.setFullName("Platform Administrator");
        admin.setEmail(Emails.normalize(config.email()));
        admin.setPasswordHash(passwordEncoder.encode(config.password()));
        admin.markPhoneVerified();
        // Seeded rather than self-registered, so both identifiers count as confirmed.
        admin.markEmailVerified();
        admin.grantRole(Role.CLIENT, null, null);
        admin.grantRole(Role.SUPER_ADMIN, null, null);
        userRepository.save(admin);

        auditService.record(admin.getId(), AuditAction.USER_ROLE_GRANTED, "User", admin.getId(),
                Map.of("role", Role.SUPER_ADMIN.name(), "via", "bootstrap"), null);

        log.warn("Seeded SUPER_ADMIN for {} — sign in and change this password immediately",
                PhoneNumbers.mask(phone));
    }
}
