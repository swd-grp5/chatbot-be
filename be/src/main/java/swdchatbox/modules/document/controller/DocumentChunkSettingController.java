package swdchatbox.modules.document.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import swdchatbox.modules.document.dto.request.UpdateDocumentChunkSettingRequest;
import swdchatbox.modules.document.dto.response.DocumentChunkSettingResponse;
import swdchatbox.modules.document.service.DocumentChunkSettingService;

@RestController
@RequestMapping("/document-chunk-settings")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class DocumentChunkSettingController {

    private final DocumentChunkSettingService documentChunkSettingService;

    @Operation(summary = "Cấu hình chunk document hiện tại", description = "Trả về setting trong DB hoặc giá trị mặc định nếu chưa cấu hình.")
    @GetMapping
    public ResponseEntity<DocumentChunkSettingResponse> getCurrent() {
        return ResponseEntity.ok(documentChunkSettingService.getCurrent());
    }

    @Operation(summary = "Cập nhật cấu hình chunk document", description = "Admin chỉnh chunkSize và chunkOverlap dùng khi index tài liệu.")
    @PutMapping
    public ResponseEntity<DocumentChunkSettingResponse> update(
            @Valid @RequestBody UpdateDocumentChunkSettingRequest request) {
        return ResponseEntity.ok(documentChunkSettingService.update(request));
    }
}
