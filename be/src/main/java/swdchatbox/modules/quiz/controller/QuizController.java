package swdchatbox.modules.quiz.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import swdchatbox.modules.quiz.dto.request.QuizFilterRequest;
import swdchatbox.modules.quiz.dto.request.QuizGenerateRequest;
import swdchatbox.modules.quiz.dto.request.QuizSubmitRequest;
import swdchatbox.modules.quiz.dto.request.QuizUpdateRequest;
import swdchatbox.modules.quiz.dto.response.QuizAttemptResponse;
import swdchatbox.modules.quiz.dto.response.QuizResponse;
import swdchatbox.modules.quiz.dto.response.QuizSummaryResponse;
import swdchatbox.modules.quiz.enums.QuizStatus;
import swdchatbox.modules.quiz.service.QuizAiGenerationService;
import swdchatbox.modules.quiz.service.QuizService;
import swdchatbox.shared.dto.PageResponse;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/quizzes")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class QuizController {

    private final QuizService quizService;
    private final QuizAiGenerationService quizAiGenerationService;

    @Operation(summary = "Danh sách quiz theo môn học")
    @GetMapping
    public ResponseEntity<PageResponse<QuizSummaryResponse>> findAll(
            @RequestParam(required = false) UUID subjectId,
            @RequestParam(required = false) QuizStatus status,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDir,
            Authentication authentication
    ) {
        QuizFilterRequest filter = new QuizFilterRequest();
        filter.setSubjectId(subjectId);
        filter.setStatus(status);
        filter.setActive(active);
        filter.setKeyword(keyword);
        Pageable pageable = PageRequest.of(page, size, resolveSort(sortBy, sortDir));
        return ResponseEntity.ok(quizService.findAll(filter, pageable, email(authentication)));
    }

    @Operation(summary = "Chi tiết quiz", description = "forAttempt=true: sinh viên làm bài (ẩn đáp án). Giảng viên xem đầy đủ để chỉnh sửa.")
    @GetMapping("/{id}")
    public ResponseEntity<QuizResponse> findById(
            @PathVariable UUID id,
            @Parameter(description = "true khi sinh viên mở màn làm bài") @RequestParam(defaultValue = "false") boolean forAttempt,
            Authentication authentication
    ) {
        return ResponseEntity.ok(quizService.findById(id, email(authentication), forAttempt));
    }

    @Operation(summary = "AI sinh quiz từ tài liệu môn học", description = "Giảng viên gọi AI tạo quiz DRAFT. Sau đó dùng PUT /quizzes/{id} để sửa câu hỏi và đáp án đúng.")
    @PostMapping("/generate")
    public ResponseEntity<QuizResponse> generate(@Valid @RequestBody QuizGenerateRequest request, Authentication authentication) {
        return ResponseEntity.ok(quizAiGenerationService.generate(request, email(authentication)));
    }

    @Operation(summary = "Giảng viên chỉnh sửa quiz", description = "Sửa câu hỏi, đáp án đúng sau khi AI sinh. Chỉ hỗ trợ trắc nghiệm SINGLE/MULTIPLE.")
    @PutMapping("/{id}")
    public ResponseEntity<QuizResponse> update(@PathVariable UUID id, @Valid @RequestBody QuizUpdateRequest request, Authentication authentication) {
        return ResponseEntity.ok(quizService.update(id, request, email(authentication)));
    }

    @PostMapping("/{id}/publish")
    public ResponseEntity<QuizResponse> publish(@PathVariable UUID id, Authentication authentication) {
        return ResponseEntity.ok(quizService.publish(id, email(authentication)));
    }

    @PostMapping("/{id}/close")
    public ResponseEntity<QuizResponse> close(@PathVariable UUID id, Authentication authentication) {
        return ResponseEntity.ok(quizService.close(id, email(authentication)));
    }

    @PatchMapping("/{id}/toggle-active")
    public ResponseEntity<QuizResponse> toggleActive(@PathVariable UUID id, Authentication authentication) {
        return ResponseEntity.ok(quizService.toggleActive(id, email(authentication)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id, Authentication authentication) {
        quizService.delete(id, email(authentication));
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Sinh viên nộp bài", description = "Chấm tự động cho trắc nghiệm SINGLE/MULTIPLE.")
    @PostMapping("/{id}/submit")
    public ResponseEntity<QuizAttemptResponse> submit(@PathVariable UUID id, @Valid @RequestBody QuizSubmitRequest request, Authentication authentication) {
        return ResponseEntity.ok(quizService.submit(id, request, email(authentication)));
    }

    @GetMapping("/{id}/attempts")
    public ResponseEntity<List<QuizAttemptResponse>> myAttempts(@PathVariable UUID id, Authentication authentication) {
        return ResponseEntity.ok(quizService.getMyAttempts(id, email(authentication)));
    }

    @GetMapping("/{id}/attempts/{attemptId}")
    public ResponseEntity<QuizAttemptResponse> getAttempt(@PathVariable UUID id, @PathVariable UUID attemptId, Authentication authentication) {
        return ResponseEntity.ok(quizService.getAttempt(id, attemptId, email(authentication)));
    }

    private String email(Authentication authentication) {
        return authentication != null ? authentication.getName() : null;
    }

    private Sort resolveSort(String sortBy, String sortDir) {
        String prop = sortBy == null || sortBy.isBlank() ? "createdAt" : sortBy;
        return "asc".equalsIgnoreCase(sortDir) ? Sort.by(prop).ascending() : Sort.by(prop).descending();
    }
}
