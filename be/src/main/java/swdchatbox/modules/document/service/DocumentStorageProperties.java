package swdchatbox.modules.document.service;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.storage.documents")
public class DocumentStorageProperties {
    private String basePath;
    private String checksumAlgorithm;
    private S3 s3 = new S3();

    @Getter
    @Setter
    public static class S3 {
        private String bucket;
        private String region;
        private String accessKeyId;
        private String secretAccessKey;
        private String keyPrefix = "documents";
    }
}
