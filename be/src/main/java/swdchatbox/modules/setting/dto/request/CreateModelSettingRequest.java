package swdchatbox.modules.setting.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateModelSettingRequest {

    @NotBlank
    private String provider;

    @NotBlank
    private String chatModel;

    @NotBlank
    private String embeddingModel;

    private String apiKey;

    private Double temperature;

    private Integer topK;

    private Integer maxTokens;

    private Boolean active;
}
