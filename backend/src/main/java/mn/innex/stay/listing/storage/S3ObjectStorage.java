package mn.innex.stay.listing.storage;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/** S3-compatible implementation, used against MinIO locally and S3 in production. */
@Component
@EnableConfigurationProperties(S3Properties.class)
public class S3ObjectStorage implements ObjectStorage {

    private static final Logger log = LoggerFactory.getLogger(S3ObjectStorage.class);

    private final S3Properties properties;
    private final S3Client client;
    private final S3Presigner presigner;

    public S3ObjectStorage(S3Properties properties) {
        this.properties = properties;
        StaticCredentialsProvider credentials = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(properties.accessKey(), properties.secretKey()));
        Region region = Region.of(properties.region());
        // Path-style addressing: MinIO does not do virtual-host buckets, and S3
        // accepts it, so one configuration works for both.
        S3Configuration serviceConfiguration = S3Configuration.builder()
                .pathStyleAccessEnabled(properties.pathStyleAccess())
                .build();

        this.client = S3Client.builder()
                .endpointOverride(URI.create(properties.endpoint()))
                .credentialsProvider(credentials)
                .region(region)
                .serviceConfiguration(serviceConfiguration)
                .build();
        this.presigner = S3Presigner.builder()
                .endpointOverride(URI.create(properties.endpoint()))
                .credentialsProvider(credentials)
                .region(region)
                .serviceConfiguration(serviceConfiguration)
                .build();
    }

    @Override
    public PresignedUpload presignUpload(String key, String contentType, Duration ttl) {
        Duration lifetime = ttl == null ? properties.presignTtl() : ttl;
        PutObjectRequest put = PutObjectRequest.builder()
                .bucket(properties.bucket())
                .key(key)
                .contentType(contentType)
                .build();
        String url = presigner.presignPutObject(PutObjectPresignRequest.builder()
                        .signatureDuration(lifetime)
                        .putObjectRequest(put)
                        .build())
                .url()
                .toString();
        return new PresignedUpload(url, key, Instant.now().plus(lifetime));
    }

    @Override
    public Optional<StoredObject> describe(String key) {
        try {
            HeadObjectResponse head = client.headObject(HeadObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(key)
                    .build());
            return Optional.of(new StoredObject(key, head.contentType(), head.contentLength()));
        } catch (NoSuchKeyException ignored) {
            return Optional.empty();
        } catch (S3Exception ex) {
            // A 404 from HeadObject can surface as a generic S3Exception depending
            // on the implementation, so treat "not found" as absence, not failure.
            if (ex.statusCode() == 404) {
                return Optional.empty();
            }
            throw ex;
        }
    }

    @Override
    public void delete(String key) {
        try {
            client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(key)
                    .build());
        } catch (S3Exception ex) {
            // Storage cleanup must not fail the user's request; the row is already gone.
            log.warn("Could not delete object {} from bucket {}", key, properties.bucket(), ex);
        }
    }

    @Override
    public String publicUrl(String key) {
        String base = properties.publicBaseUrl();
        if (base == null || base.isBlank()) {
            return properties.endpoint() + "/" + properties.bucket() + "/" + key;
        }
        return base.endsWith("/") ? base + key : base + "/" + key;
    }

    S3Client client() {
        return client;
    }

    S3Properties properties() {
        return properties;
    }
}
