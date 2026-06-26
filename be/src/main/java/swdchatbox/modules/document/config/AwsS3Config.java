package swdchatbox.modules.document.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import swdchatbox.modules.document.service.DocumentStorageProperties;

@Configuration
@ConditionalOnProperty(prefix = "app.storage.documents.s3", name = "bucket")
public class AwsS3Config {

    @Bean
    public S3Client s3Client(DocumentStorageProperties properties) {
        DocumentStorageProperties.S3 s3 = properties.getS3();
        return S3Client.builder()
                .region(Region.of(s3.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(s3.getAccessKeyId(), s3.getSecretAccessKey())
                ))
                .build();
    }
}
