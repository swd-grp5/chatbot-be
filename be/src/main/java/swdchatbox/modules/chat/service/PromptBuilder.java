package swdchatbox.modules.chat.service;

import org.springframework.stereotype.Component;
import swdchatbox.modules.ai.dto.LlmMessage;
import swdchatbox.modules.chat.entity.ChatMessage;
import swdchatbox.modules.chat.enums.MessageRole;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the prompt for the RAG pipeline.
 * Combines system instructions, retrieved document context, conversation
 * history,
 * and the user's current question into a structured prompt for the LLM.
 */
@Component
public class PromptBuilder {

    private static final String SYSTEM_PROMPT = """
            Bạn là trợ lý học tập chỉ trả lời dựa trên tài liệu người dùng đã gắn vào đoạn chat.

            ## Quy tắc BẮT BUỘC:
            1. CHỈ dùng nội dung trong phần "TÀI LIỆU THAM KHẢO". Không dùng kiến thức ngoài, \
            textbook, hay kiến thức nền của bạn.
            2. Nếu phần tham khảo KHÔNG có thông tin đủ để trả lời, nói rõ: \
            "Xin lỗi, trong các tài liệu đã chọn không có nội dung phù hợp để trả lời câu hỏi này." \
            Không được bịa thêm định nghĩa, phân loại, hay ví dụ không có trong đoạn tham khảo.
            3. Nếu người dùng yêu cầu tóm tắt / tổng quan một tài liệu, hãy tóm tắt dựa trên \
            các đoạn tham khảo của đúng tài liệu đó; nêu các ý chính, không nói là thiếu nội dung \
            chỉ vì câu hỏi chung chung.
            4. Mỗi ý lấy từ tài liệu phải có trích dẫn dạng [n] kèm tên file, ví dụ: [1] Present Require.
            5. Cuối câu trả lời, luôn có mục "Nguồn:" liệt kê từng [n] → tên tài liệu (và trang nếu có).
            6. Trả lời bằng tiếng Việt rõ ràng, mạch lạc. Thuật ngữ chuyên ngành giữ nguyên tiếng Anh nếu tài liệu dùng vậy.
            7. Không nêu tài liệu không nằm trong danh sách đã gắn / tham khảo.
            """;

    /**
     * Build the complete message list for the LLM.
     *
     * @param retrievedChunks          chunks retrieved from vector search, with their
     *                                 citation indices
     * @param conversationHistory      previous messages in the conversation
     * @param userQuestion             the current user question
     * @param historyLimit             max number of history messages to include
     * @param attachedDocumentTitles   titles of documents attached to this conversation
     * @return list of LlmMessage ready to send to the LLM
     */
    public List<LlmMessage> buildMessages(
            List<RetrievedChunk> retrievedChunks,
            List<ChatMessage> conversationHistory,
            String userQuestion,
            int historyLimit,
            List<String> attachedDocumentTitles) {
        List<LlmMessage> messages = new ArrayList<>();

        // 1. System instruction
        messages.add(LlmMessage.system(SYSTEM_PROMPT));

        // 1b. Explicit list of docs attached to this chat (for meta questions)
        messages.add(LlmMessage.system(buildAttachedDocsPrompt(attachedDocumentTitles)));

        // 2. Context from retrieved documents
        if (retrievedChunks != null && !retrievedChunks.isEmpty()) {
            String contextPrompt = buildContextPrompt(retrievedChunks);
            messages.add(LlmMessage.system(contextPrompt));
        } else {
            messages.add(LlmMessage.system(
                    "KHÔNG TÌM THẤY đoạn nội dung liên quan trong các tài liệu đã gắn. " +
                            "Nếu người dùng hỏi bạn đang được cung cấp tài liệu gì, chỉ liệt kê đúng danh sách đã gắn ở trên. " +
                            "Với câu hỏi nội dung khác, trả lời rằng không tìm thấy đoạn phù hợp trong tài liệu đã chọn. " +
                            "Không được tự bịa nội dung."));
        }

        // 3. Conversation history (limit to last N messages)
        if (conversationHistory != null && !conversationHistory.isEmpty()) {
            List<ChatMessage> limitedHistory = conversationHistory;
            if (conversationHistory.size() > historyLimit) {
                limitedHistory = conversationHistory.subList(
                        conversationHistory.size() - historyLimit,
                        conversationHistory.size());
            }

            for (ChatMessage msg : limitedHistory) {
                if (msg.getRole() == MessageRole.USER) {
                    messages.add(LlmMessage.user(msg.getContent()));
                } else if (msg.getRole() == MessageRole.ASSISTANT) {
                    messages.add(LlmMessage.assistant(msg.getContent()));
                }
            }
        }

        // 4. Current user question
        messages.add(LlmMessage.user(userQuestion));

        return messages;
    }

    private String buildAttachedDocsPrompt(List<String> attachedDocumentTitles) {
        StringBuilder sb = new StringBuilder();
        sb.append("## TÀI LIỆU ĐÃ GẮN VÀO ĐOẠN CHAT NÀY:\n");
        if (attachedDocumentTitles == null || attachedDocumentTitles.isEmpty()) {
            sb.append("(Chưa gắn tài liệu nào.)\n");
            sb.append("Khi người dùng hỏi bạn đang được cung cấp tài liệu gì, trả lời rõ là chưa có tài liệu nào được gắn vào đoạn chat này.\n");
        } else {
            for (String title : attachedDocumentTitles) {
                sb.append("- ").append(title).append("\n");
            }
            sb.append("Khi người dùng hỏi bạn đang được cung cấp tài liệu gì, CHỈ liệt kê đúng danh sách trên. ")
                    .append("KHÔNG được nêu tên hoặc nội dung tài liệu không nằm trong danh sách này.\n");
        }
        return sb.toString();
    }

    private String buildContextPrompt(List<RetrievedChunk> chunks) {
        StringBuilder sb = new StringBuilder();
        sb.append("## TÀI LIỆU THAM KHẢO:\n");
        sb.append("Chỉ được dùng các đoạn dưới đây. Mỗi [n] là một nguồn; khi trích dẫn hãy viết [n] kèm tên file.\n\n");

        for (RetrievedChunk chunk : chunks) {
            sb.append("[").append(chunk.citationIndex()).append("] ");

            if (chunk.documentTitle() != null && !chunk.documentTitle().isBlank()) {
                sb.append(chunk.documentTitle());
            } else {
                sb.append("(không rõ tên file)");
            }
            if (chunk.pageStart() != null) {
                sb.append(" (trang ").append(chunk.pageStart());
                if (chunk.pageEnd() != null && !chunk.pageEnd().equals(chunk.pageStart())) {
                    sb.append("-").append(chunk.pageEnd());
                }
                sb.append(")");
            }
            sb.append(":\n");
            sb.append(chunk.content()).append("\n\n");
        }

        return sb.toString();
    }

    /**
     * A chunk retrieved from vector search, enriched with document metadata.
     */
    public record RetrievedChunk(
            int citationIndex,
            String chunkId,
            String documentId,
            String documentTitle,
            String content,
            Integer pageStart,
            Integer pageEnd,
            Double score) {
    }
}
