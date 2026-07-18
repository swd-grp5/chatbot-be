package swdchatbox.modules.chat.dto.response;

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
    /** Full-ish excerpt for the sidebar "ĐOẠN TRÍCH". */
    private String quotedText;
    /** Single line/sentence within quotedText for FE to bold/highlight on click. */
    private String highlightText;
    private Integer pageStart;
    private Integer pageEnd;
    private Double score;
}
