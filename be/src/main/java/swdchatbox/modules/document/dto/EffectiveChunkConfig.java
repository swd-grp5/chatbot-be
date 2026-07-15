package swdchatbox.modules.document.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class EffectiveChunkConfig {

    private int chunkSize;
    private int chunkOverlap;
    private boolean fromDatabase;
}
