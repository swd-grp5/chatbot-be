package swdchatbox.modules.chat.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import swdchatbox.modules.chat.dto.request.CreateConversationRequest;
import swdchatbox.modules.chat.dto.request.SendMessageRequest;
import swdchatbox.modules.chat.dto.response.ChatAnswerResponse;
import swdchatbox.modules.chat.dto.response.ConversationResponse;
import swdchatbox.modules.chat.dto.response.MessageResponse;
import swdchatbox.modules.chat.service.ChatService;
import swdchatbox.shared.exception.ResourceNotFoundException;
import swdchatbox.modules.user.entity.User;
import swdchatbox.modules.user.repository.UserRepository;

import java.util.UUID;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Chat", description = "Chat & Q&A with RAG pipeline")
public class ChatController {

    private final ChatService chatService;
    private final UserRepository userRepository;

    // ───────────────── Conversations ─────────────────

    @PostMapping("/conversations")
    @Operation(summary = "Create a new conversation")
    public ResponseEntity<ConversationResponse> createConversation(
            @Valid @RequestBody CreateConversationRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getCurrentUser(userDetails);
        return ResponseEntity.ok(chatService.createConversation(request, user));
    }

    @GetMapping("/conversations")
    @Operation(summary = "Get all conversations for the current user")
    public ResponseEntity<Page<ConversationResponse>> getConversations(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        User user = getCurrentUser(userDetails);
        Pageable pageable = PageRequest.of(page, size, Sort.by("updatedAt").descending());
        return ResponseEntity.ok(chatService.getConversations(user.getId(), pageable));
    }

    @GetMapping("/conversations/{id}")
    @Operation(summary = "Get conversation details")
    public ResponseEntity<ConversationResponse> getConversation(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getCurrentUser(userDetails);
        return ResponseEntity.ok(chatService.getConversation(id, user.getId()));
    }

    @DeleteMapping("/conversations/{id}")
    @Operation(summary = "Delete (deactivate) a conversation")
    public ResponseEntity<Void> deleteConversation(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getCurrentUser(userDetails);
        chatService.deleteConversation(id, user.getId());
        return ResponseEntity.noContent().build();
    }

    // ───────────────── Messages ─────────────────

    @PostMapping("/conversations/{id}/messages")
    @Operation(summary = "Send a message and get AI answer (RAG pipeline)")
    public ResponseEntity<ChatAnswerResponse> sendMessage(
            @PathVariable UUID id,
            @Valid @RequestBody SendMessageRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getCurrentUser(userDetails);
        return ResponseEntity.ok(chatService.sendMessage(id, request, user));
    }

    @GetMapping("/conversations/{id}/messages")
    @Operation(summary = "Get message history for a conversation")
    public ResponseEntity<Page<MessageResponse>> getMessages(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        User user = getCurrentUser(userDetails);
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").ascending());
        return ResponseEntity.ok(chatService.getMessages(id, user.getId(), pageable));
    }

    // ───────────────── Helper ─────────────────

    private User getCurrentUser(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
