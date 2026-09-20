package mn.innex.stay.trust.service;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

import javax.imageio.ImageIO;

/**
 * A 64-bit difference hash of an image.
 *
 * <p>The image is reduced to a 9x8 grey thumbnail and each pixel compared with
 * the one to its right: 8 comparisons per row, 8 rows, one bit each. What
 * survives is the coarse pattern of light and dark, which is what a person
 * recognises in a photograph and what a thief cannot remove without changing the
 * picture into a different one.
 *
 * <p>Deliberately not cryptographic. Resizing, re-encoding, a filter or a modest
 * crop move a handful of bits; unrelated photographs differ by around half of
 * them. That gap is the whole detector — see
 * {@link mn.innex.stay.trust.repo.PhotoFingerprintRepository#findSimilarByAnotherOwner}.
 *
 * <p>An image with no variation has no hash. Every comparison here is between
 * neighbouring pixels, so a blank wall, an overexposed sky or a solid-colour
 * placeholder produces all zeros — and would then "match" every other
 * featureless image ever uploaded. Refusing to fingerprint those is the honest
 * answer: they carry nothing to identify.
 */
final class PerceptualHash {

    /** 8 comparisons need 9 columns. */
    private static final int WIDTH = 9;
    private static final int HEIGHT = 8;

    /**
     * Least difference between the lightest and darkest part of the thumbnail,
     * out of 255, for the image to say anything at all. Well below any real
     * photograph, and above the rounding noise of a re-encoded flat colour.
     */
    private static final int MIN_CONTRAST = 12;

    private PerceptualHash() {
    }

    /**
     * @return the hash, or empty when the bytes are not a readable image, or when
     *         the image has too little variation to identify anything
     */
    static java.util.OptionalLong of(InputStream imageStream) throws IOException {
        BufferedImage source = ImageIO.read(imageStream);
        if (source == null) {
            return java.util.OptionalLong.empty();
        }

        BufferedImage thumbnail = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = thumbnail.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.drawImage(source, 0, 0, WIDTH, HEIGHT, null);
        } finally {
            graphics.dispose();
        }

        int darkest = 255;
        int lightest = 0;
        int[][] grey = new int[HEIGHT][WIDTH];
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                int value = luminance(thumbnail.getRGB(x, y));
                grey[y][x] = value;
                darkest = Math.min(darkest, value);
                lightest = Math.max(lightest, value);
            }
        }
        if (lightest - darkest < MIN_CONTRAST) {
            return java.util.OptionalLong.empty();
        }

        long hash = 0L;
        int bit = 0;
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 1; x < WIDTH; x++) {
                if (grey[y][x - 1] > grey[y][x]) {
                    hash |= 1L << bit;
                }
                bit++;
            }
        }
        return java.util.OptionalLong.of(hash);
    }

    /** How many bits two hashes differ by; 0 is identical, ~32 is unrelated. */
    static int distance(long first, long second) {
        return Long.bitCount(first ^ second);
    }

    /** Rec. 601 luma: green carries most of the perceived brightness. */
    private static int luminance(int rgb) {
        int red = (rgb >> 16) & 0xFF;
        int green = (rgb >> 8) & 0xFF;
        int blue = rgb & 0xFF;
        return (red * 299 + green * 587 + blue * 114) / 1000;
    }
}
