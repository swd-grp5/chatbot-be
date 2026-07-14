package swdchatbox.modules.document.service;

import org.springframework.stereotype.Service;
import swdchatbox.modules.document.entity.Document;
import swdchatbox.modules.document.entity.DocumentChunk;
import swdchatbox.modules.document.enums.ChunkType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class DocumentChunkingService {

    private static final int DEFAULT_CHUNK_SIZE = 1200;
    private static final int DEFAULT_CHUNK_OVERLAP = 200;

    // Khi vượt quá chunkSize, cho phép "co giãn" thêm tối đa bao nhiêu ký tự để
    // tìm được điểm kết thúc câu gần nhất trước khi buộc phải cắt cứng.
    private static final int BOUNDARY_SEARCH_WINDOW = 200;

    public List<DocumentChunk> chunk(Document document, String content) {
        List<DocumentChunk> chunks = new ArrayList<>();
        if (content == null || content.isBlank()) {
            return chunks;
        }

        int chunkSize = DEFAULT_CHUNK_SIZE;
        int overlap = Math.min(DEFAULT_CHUNK_OVERLAP, chunkSize / 2);
        int index = 0;
        int start = 0;

        while (start < content.length()) {
            int hardEnd = Math.min(content.length(), start + chunkSize);
            int end = findChunkEnd(content, start, hardEnd);

            String chunkContent = content.substring(start, end).trim();
            if (!chunkContent.isEmpty()) {
                chunks.add(DocumentChunk.builder()
                        .document(document)
                        .chunkIndex(index++)
                        .chunkType(ChunkType.TEXT)
                        .content(chunkContent)
                        .tokenCount(estimateTokenCount(chunkContent))
                        .startCharIndex(start)
                        .endCharIndex(end)
                        .metadataJson(toMetadataJson(Map.of(
                                "startCharIndex", start,
                                "endCharIndex", end,
                                "chunkSize", chunkContent.length()
                        )))
                        .build());
            }

            if (end >= content.length()) {
                break;
            }
            start = nextStart(content, start, end, overlap);
        }

        return chunks;
    }

    /**
     * Tìm điểm cắt "đẹp" cho chunk trong khoảng [start, hardEnd).
     * Ưu tiên theo thứ tự: cuối đoạn văn (xuống dòng) -> cuối câu (. ! ? …)
     * -> khoảng trắng (không cắt giữa chữ). Nếu không tìm được thì cắt cứng.
     */
    private int findChunkEnd(String content, int start, int hardEnd) {
        if (hardEnd >= content.length()) {
            return content.length();
        }

        // Cho phép nới ra một chút để không bỏ lỡ điểm kết câu ngay sau hardEnd.
        int searchEnd = Math.min(content.length(), hardEnd + BOUNDARY_SEARCH_WINDOW);
        int minEnd = start + (hardEnd - start) / 2; // tránh chunk quá ngắn

        // 1. Ưu tiên ranh giới đoạn văn (dòng trống / xuống dòng).
        int paragraphBreak = lastBoundary(content, minEnd, searchEnd, this::isParagraphBreak);
        if (paragraphBreak > start) {
            return paragraphBreak;
        }

        // 2. Ranh giới cuối câu.
        int sentenceBreak = lastBoundary(content, minEnd, searchEnd, this::isSentenceEnd);
        if (sentenceBreak > start) {
            return sentenceBreak;
        }

        // 3. Ranh giới khoảng trắng / separator (không cắt giữa từ).
        int wordBreak = lastBoundary(content, minEnd, hardEnd, this::isWordBreak);
        if (wordBreak > start) {
            return wordBreak;
        }

        // 4. Vẫn đang giữa từ tại hardEnd → lùi về đầu từ hiện tại.
        int backtracked = backtrackToWordStart(content, start, hardEnd);
        if (backtracked > start) {
            return backtracked;
        }

        // 5. Không có ranh giới hợp lý -> cắt cứng (tránh cắt giữa surrogate pair).
        return avoidSurrogateSplit(content, hardEnd);
    }

    /** Khoảng trắng + zero-width / NBSP thường gặp trong PDF. */
    private boolean isWordBreak(char ch) {
        return Character.isWhitespace(ch)
                || ch == '\u00A0' // NBSP
                || ch == '\u200B' // ZWSP
                || ch == '\u200C'
                || ch == '\u200D'
                || ch == '\uFEFF';
    }

    private boolean isWordChar(char ch) {
        return Character.isLetterOrDigit(ch);
    }

    /**
     * Nếu hardEnd nằm giữa một từ, trả về vị trí đầu từ đó (không cắt "values" → "v").
     */
    private int backtrackToWordStart(String content, int start, int hardEnd) {
        if (hardEnd <= start || hardEnd > content.length()) {
            return -1;
        }
        if (hardEnd < content.length()
                && isWordChar(content.charAt(hardEnd))
                && isWordChar(content.charAt(hardEnd - 1))) {
            int i = hardEnd;
            while (i > start && isWordChar(content.charAt(i - 1))) {
                i--;
            }
            return i > start ? i : -1;
        }
        return -1;
    }

    private int avoidSurrogateSplit(String content, int index) {
        if (index > 0 && index < content.length() && Character.isLowSurrogate(content.charAt(index))) {
            return index - 1;
        }
        return index;
    }

    /** Trả về vị trí (loại trừ) ngay sau ký tự ranh giới cuối cùng trong [from, to). */
    private int lastBoundary(String content, int from, int to, BoundaryMatcher matcher) {
        for (int i = to - 1; i >= from; i--) {
            if (matcher.matches(content.charAt(i))) {
                return i + 1;
            }
        }
        return -1;
    }

    private boolean isSentenceEnd(char ch) {
        return ch == '.' || ch == '!' || ch == '?' || ch == '\u2026' // …
                || ch == '\u3002' || ch == '\uFF01' || ch == '\uFF1F'; // 。！？ (full-width)
    }

    private boolean isParagraphBreak(char ch) {
        return ch == '\n' || ch == '\r';
    }

    /**
     * Tính điểm bắt đầu chunk kế tiếp, lùi lại theo overlap nhưng căn về đầu câu
     * gần nhất để phần overlap không bắt đầu giữa chừng một câu.
     */
    private int nextStart(String content, int start, int end, int overlap) {
        int target = Math.max(end - overlap, start + 1);
        // Lùi tiếp một chút để bắt đầu ngay sau một ranh giới câu nếu có.
        int boundary = lastBoundary(content, start + 1, target, ch -> isSentenceEnd(ch) || isParagraphBreak(ch));
        if (boundary > start && boundary < end) {
            return boundary;
        }
        return target;
    }

    @FunctionalInterface
    private interface BoundaryMatcher {
        boolean matches(char ch);
    }

    private int estimateTokenCount(String text) {
        return Math.max(1, (int) Math.ceil(text.length() / 4.0));
    }

    private String toMetadataJson(Map<String, Object> metadata) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(entry.getKey()).append('"').append(':');
            Object value = entry.getValue();
            if (value instanceof Number || value instanceof Boolean) {
                sb.append(value);
            } else {
                sb.append('"').append(String.valueOf(value).replace("\"", "\\\"")).append('"');
            }
        }
        sb.append('}');
        return sb.toString();
    }
}
