package mn.innex.stay.trust.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Random;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The detector underneath the stolen-photo check.
 *
 * <p>Its value rests entirely on one gap: the same picture must stay close after
 * the manipulations a thief actually performs, while different pictures stay far
 * apart. If that gap closes, the flag either misses real theft or buries
 * reviewers in false alarms, so it is worth pinning with real image bytes rather
 * than trusting the algorithm's reputation.
 */
class PerceptualHashTest {

    @Test
    @DisplayName("the same photo re-encoded as JPEG is still recognisably the same")
    void survivesReEncoding() throws IOException {
        BufferedImage original = scene(800, 600, 7);

        long first = hash(png(original));
        long second = hash(jpeg(original));

        assertThat(PerceptualHash.distance(first, second))
                .as("a re-encode is the laziest way to disguise a stolen photo")
                .isLessThanOrEqualTo(10);
    }

    @Test
    @DisplayName("a resized copy is still recognisably the same")
    void survivesResizing() throws IOException {
        BufferedImage original = scene(800, 600, 3);

        long first = hash(png(original));
        long second = hash(png(resize(original, 320, 240)));

        assertThat(PerceptualHash.distance(first, second)).isLessThanOrEqualTo(10);
    }

    @Test
    @DisplayName("two different photographs are nowhere near each other")
    void differentImagesAreFarApart() throws IOException {
        long first = hash(png(scene(800, 600, 1)));
        long second = hash(png(scene(800, 600, 2)));

        assertThat(PerceptualHash.distance(first, second))
                .as("well clear of the threshold, or honest hosts get flagged")
                .isGreaterThan(16);
    }

    @Test
    @DisplayName("a featureless image has no hash, so it cannot match everything")
    void flatImageHasNoHash() throws IOException {
        // Every comparison is between neighbouring pixels, so a solid colour gives
        // all zeros — and two unrelated blank walls would look like the same photo.
        BufferedImage blank = new BufferedImage(400, 300, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = blank.createGraphics();
        try {
            graphics.setColor(new Color(90, 140, 110));
            graphics.fillRect(0, 0, 400, 300);
        } finally {
            graphics.dispose();
        }

        assertThat(PerceptualHash.of(new ByteArrayInputStream(png(blank))))
                .as("no detail means nothing to identify")
                .isEmpty();
    }

    @Test
    @DisplayName("a gentle gradient still has enough to work with")
    void faintDetailStillHashes() throws IOException {
        BufferedImage gradient = new BufferedImage(400, 300, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 300; y++) {
            for (int x = 0; x < 400; x++) {
                int shade = 60 + (x * 60) / 400;
                gradient.setRGB(x, y, new Color(shade, shade, shade).getRGB());
            }
        }

        assertThat(PerceptualHash.of(new ByteArrayInputStream(png(gradient))))
                .as("a real photograph of a plain wall still has shading")
                .isPresent();
    }

    @Test
    @DisplayName("bytes that are not an image yield no hash rather than an exception")
    void nonImageIsIgnored() throws IOException {
        assertThat(PerceptualHash.of(new ByteArrayInputStream("not an image".getBytes())))
                .isEmpty();
    }

    private static long hash(byte[] bytes) throws IOException {
        return PerceptualHash.of(new ByteArrayInputStream(bytes)).orElseThrow();
    }

    /** A deterministic pseudo-photograph: bands and blocks, distinct per seed. */
    private static BufferedImage scene(int width, int height, int seed) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Random random = new Random(seed);
        Graphics2D graphics = image.createGraphics();
        try {
            for (int y = 0; y < height; y += 40) {
                graphics.setColor(new Color(random.nextInt(256), random.nextInt(256),
                        random.nextInt(256)));
                graphics.fillRect(0, y, width, 40);
            }
            for (int i = 0; i < 12; i++) {
                graphics.setColor(new Color(random.nextInt(256), random.nextInt(256),
                        random.nextInt(256)));
                graphics.fillRect(random.nextInt(width - 100), random.nextInt(height - 100),
                        100, 100);
            }
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static BufferedImage resize(BufferedImage source, int width, int height) {
        BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = resized.createGraphics();
        try {
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return resized;
    }

    private static byte[] png(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private static byte[] jpeg(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", out);
        return out.toByteArray();
    }
}
