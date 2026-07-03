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
import org.springframework.web.bind.annotation.*;
import swdchatbox.modules.quiz.dto.request.QuestionTypeFilterRequest;
import swdchatbox.modules.quiz.dto.request.QuestionTypeRequest;
import swdchatbox.modules.quiz.dto.response.QuestionTypeResponse;
import swdchatbox.modules.quiz.service.QuestionTypeService;
import swdchatbox.shared.dto.PageResponse;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/question-types")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class QuestionTypeController {

    private final QuestionTypeService questionTypeService;

    @Operation(summary = "Danh sách loại câu hỏi")
    @GetMapping
    public ResponseEntity<PageResponse<QuestionTypeResponse>> findAll(
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDir
    ) {
        QuestionTypeFilterRequest filter = new QuestionTypeFilterRequest();
        filter.setActive(active);
        filter.setKeyword(keyword);
        Pageable pageable = PageRequest.of(page, size, resolveSort(sortBy, sortDir));
        return ResponseEntity.ok(questionTypeService.findAll(filter, pageable));
    }

    @Operation(summary = "Loại câu hỏi đang active", description = "FE dùng dropdown khi giảng viên tạo quiz.")
    @GetMapping("/active")
    public ResponseEntity<List<QuestionTypeResponse>> findAllActive() {
        return ResponseEntity.ok(questionTypeService.findAllActive());
    }

    @GetMapping("/{id}")
    public ResponseEntity<QuestionTypeResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(questionTypeService.findById(id));
    }

    @PostMapping
    public ResponseEntity<QuestionTypeResponse> create(@Valid @RequestBody QuestionTypeRequest request) {
        return ResponseEntity.ok(questionTypeService.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<QuestionTypeResponse> update(@PathVariable UUID id, @Valid @RequestBody QuestionTypeRequest request) {
        return ResponseEntity.ok(questionTypeService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        questionTypeService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/toggle-active")
    public ResponseEntity<QuestionTypeResponse> toggleActive(@PathVariable UUID id) {
        return ResponseEntity.ok(questionTypeService.toggleActive(id));
    }

    private Sort resolveSort(String sortBy, String sortDir) {
        String prop = sortBy == null || sortBy.isBlank() ? "sortOrder" : sortBy;
        return "desc".equalsIgnoreCase(sortDir) ? Sort.by(prop).descending() : Sort.by(prop).ascending();
    }
}
