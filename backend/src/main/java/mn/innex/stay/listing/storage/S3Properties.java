package mn.innex.stay.listing.storage;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Object storage settings. MinIO in development, S3 in production; the only
 * differences are the endpoint and path-style addressing.
 *
 * @param publicBaseUrl    prefix for serving photos, e.g. a CDN origin. Photos
 *                         are public content, so they are served directly rather
 *                         than through presigned reads.
 * @param presignTtl       lifetime of an upload URL; short, because the browser
 *                         uses it immediately
 * @param autoCreateBucket dev only — creates the bucket and opens the photo
 *                         prefix for anonymous reads. In production the bucket
 *                         and its policy belong to infrastructure, not the app.
 */
@ConfigurationProperties(prefix = "app.storage.s3")
public record S3Properties(
        String endpoint,
        String region,
        String bucket,
        String accessKey,
        String secretKey,
        boolean pathStyleAccess,
        String publicBaseUrl,
        Duration presignTtl,
        long maxPhotoBytes,
        boolean autoCreateBucket) {

    /** Prefix under which listing photos live. */
    public static final String PHOTO_PREFIX = "properties/";
}
