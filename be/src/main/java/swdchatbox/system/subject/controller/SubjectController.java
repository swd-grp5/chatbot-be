package swdchatbox.system.subject.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import swdchatbox.system.common.dto.PageResponse;
import swdchatbox.system.subject.dto.request.SubjectRequest;
import swdchatbox.system.subject.dto.response.SubjectResponse;
import swdchatbox.system.subject.service.SubjectService;

import java.util.UUID;

@RestController
@RequestMapping("/api/subjects")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class SubjectController {

    private final SubjectService subjectService;

    @GetMapping
    public ResponseEntity<PageResponse<SubjectResponse>> findAll(Pageable pageable) {
        return ResponseEntity.ok(subjectService.findAll(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SubjectResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(subjectService.findById(id));
    }

    @PostMapping
    public ResponseEntity<SubjectResponse> create(@Valid @RequestBody SubjectRequest request) {
        return ResponseEntity.ok(subjectService.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SubjectResponse> update(@PathVariable UUID id, @Valid @RequestBody SubjectRequest request) {
        return ResponseEntity.ok(subjectService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        subjectService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/toggle-active")
    public ResponseEntity<SubjectResponse> toggleActive(@PathVariable UUID id) {
        return ResponseEntity.ok(subjectService.toggleActive(id));
    }
}
