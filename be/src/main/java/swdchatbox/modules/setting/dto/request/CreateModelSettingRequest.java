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

    private Double temperature;

    private Integer topK;

    private Integer maxTokens;

    /** When true (default), this setting becomes the active one used by AI. */
    private Boolean active;
}
