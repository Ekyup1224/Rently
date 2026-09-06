package mn.innex.stay.user.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * First-run seeding.
 *
 * @param superAdmin the platform operator account created when no SUPER_ADMIN exists
 */
@ConfigurationProperties(prefix = "app.bootstrap")
public record BootstrapProperties(SuperAdmin superAdmin) {

    /**
     * @param enabled  set false in environments where the admin is provisioned another way
     * @param phone    E.164 or 8-digit Mongolian number
     * @param email    required: password sign-in goes by email, so an admin seeded
     *                 without one could only ever get in by SMS code
     * @param password initial password; the account is expected to change it at first sign-in
     */
    public record SuperAdmin(boolean enabled, String phone, String email, String password) {
    }
}
