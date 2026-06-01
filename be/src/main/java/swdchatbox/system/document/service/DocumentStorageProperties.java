package swdchatbox.system.document.service;

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
}
