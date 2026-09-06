package mn.innex.stay.listing.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.PutBucketPolicyRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * Creates the media bucket and opens the photo prefix for anonymous reads, so a
 * fresh MinIO container is usable without manual setup.
 *
 * <p>Development only. In production the bucket, its policy and its CDN belong to
 * infrastructure, and S3's Block Public Access will refuse this policy anyway —
 * which is why a failure here is logged rather than fatal.
 */
@Component
@ConditionalOnProperty(name = "app.storage.s3.auto-create-bucket", havingValue = "true")
public class S3BucketInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(S3BucketInitializer.class);

    private final S3ObjectStorage storage;

    public S3BucketInitializer(S3ObjectStorage storage) {
        this.storage = storage;
    }

    @Override
    public void run(ApplicationArguments args) {
        String bucket = storage.properties().bucket();
        try {
            if (bucketExists(bucket)) {
                log.debug("Media bucket {} already present", bucket);
            } else {
                storage.client().createBucket(CreateBucketRequest.builder().bucket(bucket).build());
                log.info("Created media bucket {}", bucket);
            }
            applyPublicReadPolicy(bucket);
        } catch (RuntimeException ex) {
            // The app is still usable: uploads work, only the public URLs will 403.
            log.warn("Could not prepare media bucket {} — listing photos may not be readable. "
                    + "Create the bucket manually or disable app.storage.s3.auto-create-bucket.",
                    bucket, ex);
        }
    }

    private boolean bucketExists(String bucket) {
        try {
            storage.client().headBucket(HeadBucketRequest.builder().bucket(bucket).build());
            return true;
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) {
                return false;
            }
            throw ex;
        }
    }

    private void applyPublicReadPolicy(String bucket) {
        String policy = """
                {
                  "Version": "2012-10-17",
                  "Statement": [
                    {
                      "Effect": "Allow",
                      "Principal": {"AWS": ["*"]},
                      "Action": ["s3:GetObject"],
                      "Resource": ["arn:aws:s3:::%s/%s*"]
                    }
                  ]
                }
                """.formatted(bucket, S3Properties.PHOTO_PREFIX);
        storage.client().putBucketPolicy(PutBucketPolicyRequest.builder()
                .bucket(bucket)
                .policy(policy)
                .build());
        log.info("Opened {}{} for public reads", bucket, "/" + S3Properties.PHOTO_PREFIX);
    }
}
