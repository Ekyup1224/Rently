package mn.innex.stay;

import java.util.ArrayList;
import java.util.List;

import mn.innex.stay.user.auth.SmsSender;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for tests that need the whole application: real Postgres (so Flyway
 * migrations and Hibernate's schema validation actually run) and real Redis (so
 * OTP storage and rate limiting are exercised rather than mocked).
 *
 * <p>Containers are static, so all subclasses share one Postgres and one Redis for
 * the whole test run.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(classes = IntegrationTest.TestInfrastructure.class)
public abstract class IntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:18-alpine"));

    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    static {
        POSTGRES.start();
        REDIS.start();
    }

    /**
     * Phone numbers are unique across the whole database, and every test class in
     * the run shares one Postgres, so they have to be unique across the run too.
     * One counter here rather than a hand-picked block per class: two classes
     * picking the same block fails far away from the mistake, in whichever test
     * happened to run second.
     */
    private static final java.util.concurrent.atomic.AtomicInteger PHONES =
            new java.util.concurrent.atomic.AtomicInteger(10_000_000);

    /** @return a Mongolian mobile number no other test is using */
    protected static String uniquePhone() {
        return "+9769" + PHONES.incrementAndGet();
    }

    /** Wires the containers in and swaps the SMS gateway for something inspectable. */
    @org.springframework.boot.test.context.TestConfiguration
    public static class TestInfrastructure {

        @Bean
        @ServiceConnection
        PostgreSQLContainer<?> postgresContainer() {
            return POSTGRES;
        }

        @Bean
        @ServiceConnection(name = "redis")
        GenericContainer<?> redisContainer() {
            return REDIS;
        }

        @Bean
        @Primary
        RecordingSmsSender recordingSmsSender() {
            return new RecordingSmsSender();
        }
    }

    /**
     * Captures outbound messages so a test can read back the code that was "sent".
     *
     * <p>The development configuration also returns the code from the request
     * endpoint, but a test should read it from here: that path is switched off in
     * any real deployment, and asserting through it would prove nothing about how
     * the code actually reaches a person.
     */
    public static class RecordingSmsSender implements SmsSender {

        private final List<Sent> sent = new ArrayList<>();

        @Override
        public synchronized void send(String e164Phone, String message) {
            sent.add(new Sent(e164Phone, message));
        }

        public synchronized void clear() {
            sent.clear();
        }

        /** @return the numeric code from the most recent message to this number */
        public synchronized String lastCodeFor(String e164Phone) {
            return sent.stream()
                    .filter(message -> message.phone().equals(e164Phone))
                    .reduce((first, second) -> second)
                    .map(message -> message.body().replaceAll("\\D.*$", ""))
                    .orElseThrow(() -> new AssertionError("No SMS was sent to " + e164Phone));
        }

        public synchronized int countFor(String e164Phone) {
            return (int) sent.stream().filter(message -> message.phone().equals(e164Phone)).count();
        }

        public record Sent(String phone, String body) {
        }
    }
}
