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
            Bạn là trợ lý học tập thông minh cho môn "Software Modeling and Design" \
            (dựa trên textbook "Software Modeling and Design: UML, Use Cases, Patterns, and Software Architectures").

            ## Quy tắc BẮT BUỘC:
            1. CHỈ trả lời dựa trên tài liệu được gắn vào đoạn chat và phần "TÀI LIỆU THAM KHẢO" bên dưới.
            2. Nếu câu hỏi nằm NGOÀI phạm vi tài liệu đã gắn, trả lời: "Xin lỗi, câu hỏi này nằm ngoài phạm vi tài liệu đã chọn. Tôi chỉ có thể trả lời các câu hỏi liên quan đến nội dung đã được cung cấp."
            3. LUÔN trích dẫn nguồn bằng format [1], [2], [3]... tương ứng với số thứ tự tài liệu tham khảo (trừ khi đang liệt kê danh sách tài liệu đã gắn).
            4. Trả lời bằng tiếng Việt rõ ràng, mạch lạc. Nếu có thuật ngữ chuyên ngành, giữ nguyên tiếng Anh kèm giải thích.
            5. Nếu có thể, đưa ra ví dụ minh họa từ tài liệu.
            6. Trả lời đầy đủ nhưng súc tích, tập trung vào nội dung chính.
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
                            "Với câu hỏi nội dung khác, thông báo không tìm thấy đoạn phù hợp trong tài liệu đã chọn."));
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
        sb.append("## TÀI LIỆU THAM KHẢO:\n\n");

        for (RetrievedChunk chunk : chunks) {
            sb.append("[").append(chunk.citationIndex()).append("] ");

            if (chunk.documentTitle() != null) {
                sb.append("**").append(chunk.documentTitle()).append("**");
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
