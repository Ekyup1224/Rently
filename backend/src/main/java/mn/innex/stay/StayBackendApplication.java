package mn.innex.stay;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Modular-monolith entry point.
 *
 * <p>One deployable, with module boundaries enforced by package structure rather
 * than by the network (build-spec section 3). Modules talk to each other only
 * through published service types — see each module's {@code package-info}.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
// Booking deadlines (unanswered requests, unpaid holds) and stay completion are
// swept by BookingLifecycleJob.
@EnableScheduling
public class StayBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(StayBackendApplication.class, args);
    }
}
