package swdchatbox.modules.quiz.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import swdchatbox.modules.quiz.dto.request.BankQuestionCreateRequest;
import swdchatbox.modules.quiz.dto.request.BankQuestionFilterRequest;
import swdchatbox.modules.quiz.dto.request.BankQuestionGenerateRequest;
import swdchatbox.modules.quiz.dto.request.BankQuestionUpdateRequest;
import swdchatbox.modules.quiz.dto.response.BankQuestionResponse;
import swdchatbox.modules.quiz.enums.MultipleChoiceMode;
import swdchatbox.modules.quiz.service.BankQuestionService;
import swdchatbox.modules.quiz.service.QuizAiGenerationService;
import swdchatbox.shared.dto.PageResponse;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/question-bank")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class BankQuestionController {

    private final BankQuestionService bankQuestionService;
    private final QuizAiGenerationService quizAiGenerationService;

    @Operation(summary = "Danh sách câu hỏi trong ngân hàng",
            description = "Giảng viên xem câu hỏi của môn mình phụ trách; admin xem tất cả. Lọc theo môn, loại, chế độ, trạng thái, nguồn AI.")
    @GetMapping
    public ResponseEntity<PageResponse<BankQuestionResponse>> findAll(
            @RequestParam(required = false) UUID subjectId,
            @RequestParam(required = false) UUID questionTypeId,
            @RequestParam(required = false) MultipleChoiceMode mode,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) Boolean aiGenerated,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDir,
            Authentication authentication
    ) {
        BankQuestionFilterRequest filter = new BankQuestionFilterRequest();
        filter.setSubjectId(subjectId);
        filter.setQuestionTypeId(questionTypeId);
        filter.setMode(mode);
        filter.setActive(active);
        filter.setAiGenerated(aiGenerated);
        filter.setKeyword(keyword);
        Pageable pageable = PageRequest.of(page, size, resolveSort(sortBy, sortDir));
        return ResponseEntity.ok(bankQuestionService.findAll(filter, pageable, email(authentication)));
    }

    @Operation(summary = "Chi tiết câu hỏi trong ngân hàng")
    @GetMapping("/{id}")
    public ResponseEntity<BankQuestionResponse> findById(@PathVariable UUID id, Authentication authentication) {
        return ResponseEntity.ok(bankQuestionService.findById(id, email(authentication)));
    }

    @Operation(summary = "Giảng viên thêm câu hỏi vào ngân hàng", description = "Soạn tay 1 câu hỏi thẳng vào ngân hàng (aiGenerated=false).")
    @PostMapping
    public ResponseEntity<BankQuestionResponse> create(@Valid @RequestBody BankQuestionCreateRequest request, Authentication authentication) {
        return ResponseEntity.ok(bankQuestionService.create(request, email(authentication)));
    }

    @Operation(summary = "AI sinh câu hỏi vào ngân hàng",
            description = "AI đọc tài liệu môn học và sinh N câu hỏi trắc nghiệm lưu thẳng vào ngân hàng (aiGenerated=true), không tạo quiz.")
    @PostMapping("/generate")
    public ResponseEntity<List<BankQuestionResponse>> generate(@Valid @RequestBody BankQuestionGenerateRequest request, Authentication authentication) {
        return ResponseEntity.ok(quizAiGenerationService.generateIntoBank(request, email(authentication)));
    }

    @Operation(summary = "Chỉnh sửa câu hỏi trong ngân hàng")
    @PutMapping("/{id}")
    public ResponseEntity<BankQuestionResponse> update(@PathVariable UUID id, @Valid @RequestBody BankQuestionUpdateRequest request, Authentication authentication) {
        return ResponseEntity.ok(bankQuestionService.update(id, request, email(authentication)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id, Authentication authentication) {
        bankQuestionService.delete(id, email(authentication));
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/toggle-active")
    public ResponseEntity<BankQuestionResponse> toggleActive(@PathVariable UUID id, Authentication authentication) {
        return ResponseEntity.ok(bankQuestionService.toggleActive(id, email(authentication)));
    }

    private String email(Authentication authentication) {
        return authentication != null ? authentication.getName() : null;
    }

    private Sort resolveSort(String sortBy, String sortDir) {
        String prop = sortBy == null || sortBy.isBlank() ? "createdAt" : sortBy;
        return "asc".equalsIgnoreCase(sortDir) ? Sort.by(prop).ascending() : Sort.by(prop).descending();
    }
}
