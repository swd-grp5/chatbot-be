package swdchatbox.shared.crypto;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.security")
@Getter
@Setter
public class SecretEncryptionProperties {

    private String encryptionKey = "";
}
