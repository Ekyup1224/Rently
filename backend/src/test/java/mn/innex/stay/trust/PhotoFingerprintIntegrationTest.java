package mn.innex.stay.trust;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

import javax.imageio.ImageIO;

import mn.innex.stay.IntegrationTest;
import mn.innex.stay.common.supply.SupplyKind;
import mn.innex.stay.listing.storage.ObjectStorage;
import mn.innex.stay.trust.domain.FlagStatus;
import mn.innex.stay.trust.domain.FlagType;
import mn.innex.stay.trust.repo.ListingFlagRepository;
import mn.innex.stay.trust.service.PhotoFingerprintService;
import mn.innex.stay.user.domain.Role;
import mn.innex.stay.user.domain.User;
import mn.innex.stay.user.repo.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

/**
 * Catching a photo that belongs to somebody else.
 *
 * <p>The case being defended against: register an account, publish photographs
 * lifted from a real listing, take bookings for a house that was never yours.
 * What must not happen alongside it is an honest host being flagged for using
 * their own photographs twice.
 */
@Import(PhotoFingerprintIntegrationTest.InMemoryStorage.class)
class PhotoFingerprintIntegrationTest extends IntegrationTest {

    @Autowired
    private PhotoFingerprintService fingerprints;

    @Autowired
    private ListingFlagRepository flags;

    @Autowired
    private InMemoryStorage storage;

    @Autowired
    private UserRepository userRepository;

    /** owner_user_id is a real foreign key, so fingerprints need real accounts. */

    @Test
    @DisplayName("the same photo published by a second account flags that listing")
    void stolenPhotoIsFlagged() throws IOException {
        byte[] photo = photograph(11);
        UUID thief = someone();
        UUID stolenListing = UUID.randomUUID();

        fingerprints.fingerprint(UUID.randomUUID(), SupplyKind.PROPERTY, UUID.randomUUID(),
                someone(), storage.put(photo));
        fingerprints.fingerprint(UUID.randomUUID(), SupplyKind.PROPERTY, stolenListing, thief,
                storage.put(photo));

        var raised = flags.findBySupplyKindAndSupplyIdAndStatus(SupplyKind.PROPERTY,
                stolenListing, FlagStatus.OPEN);
        assertThat(raised).hasSize(1);
        assertThat(raised.getFirst().getType()).isEqualTo(FlagType.DUPLICATE_PHOTO);
        // The reviewer needs the other listing to compare against, not just a warning.
        assertThat(raised.getFirst().getDetails()).containsKey("matches");
    }

    @Test
    @DisplayName("a re-encoded copy is caught too, since that is the obvious dodge")
    void reEncodedCopyIsFlagged() throws IOException {
        BufferedImage original = scene(11);
        UUID stolenListing = UUID.randomUUID();

        fingerprints.fingerprint(UUID.randomUUID(), SupplyKind.PROPERTY, UUID.randomUUID(),
                someone(), storage.put(encode(original, "png")));
        fingerprints.fingerprint(UUID.randomUUID(), SupplyKind.HOTEL, stolenListing,
                someone(), storage.put(encode(original, "jpg")));

        assertThat(flags.findBySupplyKindAndSupplyIdAndStatus(SupplyKind.HOTEL, stolenListing,
                FlagStatus.OPEN)).hasSize(1);
    }

    @Test
    @DisplayName("one owner reusing their own photograph is not suspicious")
    void sameOwnerIsNotFlagged() throws IOException {
        byte[] photo = photograph(12);
        UUID owner = someone();
        UUID secondListing = UUID.randomUUID();

        fingerprints.fingerprint(UUID.randomUUID(), SupplyKind.PROPERTY, UUID.randomUUID(),
                owner, storage.put(photo));
        fingerprints.fingerprint(UUID.randomUUID(), SupplyKind.PROPERTY, secondListing, owner,
                storage.put(photo));

        assertThat(flags.findBySupplyKindAndSupplyIdAndStatus(SupplyKind.PROPERTY, secondListing,
                FlagStatus.OPEN))
                .as("hosts relist their own places, and hotels share photography")
                .isEmpty();
    }

    @Test
    @DisplayName("a different photograph raises nothing")
    void unrelatedPhotoIsNotFlagged() throws IOException {
        UUID listing = UUID.randomUUID();

        fingerprints.fingerprint(UUID.randomUUID(), SupplyKind.PROPERTY, UUID.randomUUID(),
                someone(), storage.put(photograph(13)));
        fingerprints.fingerprint(UUID.randomUUID(), SupplyKind.PROPERTY, listing,
                someone(), storage.put(photograph(14)));

        assertThat(flags.findBySupplyKindAndSupplyIdAndStatus(SupplyKind.PROPERTY, listing,
                FlagStatus.OPEN)).isEmpty();
    }

    @Test
    @DisplayName("an unreadable file costs the host nothing")
    void undecodableUploadIsIgnored() {
        String key = storage.put("this is not an image".getBytes());

        // No exception, no flag: a check that cannot run must not block an upload.
        fingerprints.fingerprint(UUID.randomUUID(), SupplyKind.PROPERTY, UUID.randomUUID(),
                someone(), key);
    }

    private UUID someone() {
        User user = User.createWithPhone(uniquePhone(), "mn");
        user.markPhoneVerified();
        user.grantRole(Role.CLIENT, null, null);
        return userRepository.saveAndFlush(user).getId();
    }

    private static byte[] photograph(int seed) throws IOException {
        return encode(scene(seed), "png");
    }

    private static BufferedImage scene(int seed) {
        BufferedImage image = new BufferedImage(640, 480, BufferedImage.TYPE_INT_RGB);
        Random random = new Random(seed);
        Graphics2D graphics = image.createGraphics();
        try {
            for (int y = 0; y < 480; y += 30) {
                graphics.setColor(new Color(random.nextInt(256), random.nextInt(256),
                        random.nextInt(256)));
                graphics.fillRect(0, y, 640, 30);
            }
            for (int i = 0; i < 10; i++) {
                graphics.setColor(new Color(random.nextInt(256), random.nextInt(256),
                        random.nextInt(256)));
                graphics.fillRect(random.nextInt(500), random.nextInt(350), 120, 120);
            }
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static byte[] encode(BufferedImage image, String format) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, format, out);
        return out.toByteArray();
    }

    /** Storage without a bucket: the fingerprinter only ever reads bytes back. */
    @TestConfiguration
    public static class InMemoryStorage implements ObjectStorage {

        private final Map<String, byte[]> objects = new HashMap<>();

        @Bean
        @Primary
        ObjectStorage inMemoryObjectStorage() {
            return this;
        }

        String put(byte[] bytes) {
            String key = "properties/test/" + UUID.randomUUID() + ".png";
            objects.put(key, bytes);
            return key;
        }

        @Override
        public PresignedUpload presignUpload(String key, String contentType, Duration ttl) {
            return new PresignedUpload("http://localhost/upload", key, Instant.now().plus(ttl));
        }

        @Override
        public Optional<StoredObject> describe(String key) {
            return Optional.ofNullable(objects.get(key))
                    .map(bytes -> new StoredObject(key, "image/png", bytes.length));
        }

        @Override
        public Optional<byte[]> read(String key) {
            return Optional.ofNullable(objects.get(key));
        }

        @Override
        public void delete(String key) {
            objects.remove(key);
        }

        @Override
        public String publicUrl(String key) {
            return "http://localhost/" + key;
        }
    }
}
