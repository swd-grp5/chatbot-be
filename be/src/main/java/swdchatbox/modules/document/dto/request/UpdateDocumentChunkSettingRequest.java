package swdchatbox.modules.document.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateDocumentChunkSettingRequest {

    @NotNull
    @Min(100)
    @Max(20_000)
    private Integer chunkSize;

    @NotNull
    @Min(0)
    @Max(5_000)
    private Integer chunkOverlap;
}
