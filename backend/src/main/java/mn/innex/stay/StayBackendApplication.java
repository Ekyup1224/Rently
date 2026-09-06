package mn.innex.stay;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Modular-monolith entry point.
 *
 * <p>One deployable, with module boundaries enforced by package structure rather
 * than by the network (build-spec section 3). Modules talk to each other only
 * through published service types — see each module's {@code package-info}.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class StayBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(StayBackendApplication.class, args);
    }
}
