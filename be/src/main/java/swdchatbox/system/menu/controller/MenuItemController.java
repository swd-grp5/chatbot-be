package swdchatbox.system.menu.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import swdchatbox.system.menu.dto.request.CreateMenuItemRequest;
import swdchatbox.system.menu.dto.request.UpdateMenuItemRequest;
import swdchatbox.system.menu.dto.response.MenuItemResponse;
import swdchatbox.system.menu.service.MenuItemService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/menu-items")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class MenuItemController {

    private final MenuItemService menuItemService;

    @Operation(summary = "Lấy menu items theo group")
    @GetMapping("/by-group/{menuGroupId}")
    public ResponseEntity<List<MenuItemResponse>> findByGroupId(@PathVariable UUID menuGroupId) {
        return ResponseEntity.ok(menuItemService.findByGroupId(menuGroupId));
    }

    @Operation(summary = "Lấy chi tiết menu item")
    @GetMapping("/{id}")
    public ResponseEntity<MenuItemResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(menuItemService.findById(id));
    }

    @Operation(summary = "Tạo menu item")
    @PostMapping
    public ResponseEntity<MenuItemResponse> create(@Valid @RequestBody CreateMenuItemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(menuItemService.create(request));
    }

    @Operation(summary = "Cập nhật menu item", description = "Chỉ gửi các field muốn đổi. Có thể chuyển item sang group khác.")
    @PutMapping("/{id}")
    public ResponseEntity<MenuItemResponse> update(
            @PathVariable UUID id,
            @RequestBody UpdateMenuItemRequest request
    ) {
        return ResponseEntity.ok(menuItemService.update(id, request));
    }

    @Operation(summary = "Xóa menu item")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        menuItemService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Bật/tắt menu item")
    @PatchMapping("/{id}/toggle-active")
    public ResponseEntity<MenuItemResponse> toggleActive(@PathVariable UUID id) {
        return ResponseEntity.ok(menuItemService.toggleActive(id));
    }
}
