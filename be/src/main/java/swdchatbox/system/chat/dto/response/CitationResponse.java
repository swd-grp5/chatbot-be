package swdchatbox.system.chat.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class CitationResponse {

    private UUID id;
    private Integer citationIndex;
    private UUID documentId;
    private String documentTitle;
    private UUID chunkId;
    private String quotedText;
    private Integer pageStart;
    private Integer pageEnd;
    private Double score;
}
