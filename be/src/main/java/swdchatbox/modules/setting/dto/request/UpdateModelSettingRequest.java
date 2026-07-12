package swdchatbox.modules.setting.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateModelSettingRequest {

    private String provider;

    private String chatModel;

    private String embeddingModel;

    private String apiKey;

    private Double temperature;

    private Integer topK;

    private Integer maxTokens;

    private Boolean active;
}
