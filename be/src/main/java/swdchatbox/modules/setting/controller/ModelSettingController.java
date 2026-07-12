package swdchatbox.modules.setting.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import swdchatbox.modules.setting.dto.EffectiveAiConfig;
import swdchatbox.modules.setting.dto.request.CreateModelSettingRequest;
import swdchatbox.modules.setting.dto.request.UpdateModelSettingRequest;
import swdchatbox.modules.setting.dto.response.ModelSettingResponse;
import swdchatbox.modules.setting.service.ModelSettingService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/model-settings")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class ModelSettingController {

    private final ModelSettingService modelSettingService;

    @Operation(summary = "Danh sách cấu hình model AI", description = "Admin xem tất cả preset model đã lưu trong DB.")
    @GetMapping
    public ResponseEntity<List<ModelSettingResponse>> findAll() {
        return ResponseEntity.ok(modelSettingService.findAll());
    }

    @Operation(summary = "Cấu hình model đang active", description = "Setting active mà hệ thống dùng khi gọi AI (nếu chưa có sẽ 404).")
    @GetMapping("/active")
    public ResponseEntity<ModelSettingResponse> findActive() {
        return ResponseEntity.ok(modelSettingService.findActive());
    }

    @Operation(summary = "Config AI hiệu lực hiện tại", description = "Trả về config runtime: ưu tiên DB, fallback env nếu chưa có setting active.")
    @GetMapping("/effective")
    public ResponseEntity<EffectiveAiConfig> getEffective() {
        return ResponseEntity.ok(modelSettingService.resolveEffectiveConfig());
    }

    @Operation(summary = "Chi tiết cấu hình model")
    @GetMapping("/{id}")
    public ResponseEntity<ModelSettingResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(modelSettingService.findById(id));
    }

    @Operation(summary = "Tạo cấu hình model", description = "Admin tạo preset mới. Mặc định active=true và sẽ deactivate các preset khác.")
    @PostMapping
    public ResponseEntity<ModelSettingResponse> create(@Valid @RequestBody CreateModelSettingRequest request) {
        return ResponseEntity.ok(modelSettingService.create(request));
    }

    @Operation(summary = "Cập nhật cấu hình model")
    @PutMapping("/{id}")
    public ResponseEntity<ModelSettingResponse> update(
            @PathVariable UUID id,
            @RequestBody UpdateModelSettingRequest request) {
        return ResponseEntity.ok(modelSettingService.update(id, request));
    }

    @Operation(summary = "Kích hoạt cấu hình model", description = "Đặt preset này làm active; các preset khác sẽ bị tắt.")
    @PatchMapping("/{id}/activate")
    public ResponseEntity<ModelSettingResponse> activate(@PathVariable UUID id) {
        return ResponseEntity.ok(modelSettingService.activate(id));
    }

    @Operation(summary = "Xóa cấu hình model")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        modelSettingService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
