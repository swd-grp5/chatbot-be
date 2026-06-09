package swdchatbox.system.subscription.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import swdchatbox.system.subscription.dto.response.StudentSubscriptionResponse;
import swdchatbox.system.subscription.entity.SubscriptionPlan;
import swdchatbox.system.subscription.service.StudentSubscriptionService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/subscriptions")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class StudentSubscriptionController {

    private final StudentSubscriptionService subscriptionService;

    // ==========================================
    // 🟢 ENDPOINTS DÀNH CHO STUDENT (NGƯỜI DÙNG)
    // ==========================================

    @Operation(summary = "Lấy thông tin gói cước hiện tại của tôi", description = "Lấy thông tin gói VIP đang kích hoạt của user. Nếu chưa mua gói hoặc hết hạn, hệ thống tự động trả về thông tin gói 'Free' cấu hình sẵn trong Database.")
    @GetMapping("/current")
    public ResponseEntity<SubscriptionPlan> getCurrentPlan(Authentication authentication) {
        return ResponseEntity.ok(subscriptionService.getCurrentUserPlan(authentication.getName()));
    }

    @Operation(summary = "Đăng ký gói cước VIP hệ thống", description = "Học sinh thực hiện mua và kích hoạt một gói cước (Basic/Premium/VIP) thông qua planId. Hệ thống sẽ tự động tính toán thời gian hết hạn.")
    @PostMapping("/subscribe/{planId}")
    public ResponseEntity<StudentSubscriptionResponse> subscribe(
            @Parameter(description = "ID của gói cước cần đăng ký") @PathVariable UUID planId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(subscriptionService.subscribe(planId, authentication.getName()));
    }

    @Operation(summary = "Hủy gói cước VIP hiện tại", description = "Học sinh chủ động hủy gói cước đang sử dụng. Trạng thái gói sẽ chuyển sang inactive (Hệ thống sẽ tự hạ cấp về gói Free mặc định ở các tính năng chat).")
    @PostMapping("/unsubscribe")
    public ResponseEntity<Void> unsubscribe(Authentication authentication) {
        subscriptionService.unsubscribe(authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Xem lịch sử mua gói cước của tôi", description = "Hiển thị danh sách tất cả các gói cước học sinh này từng mua trong quá khứ, sắp xếp theo thời gian đăng ký mới nhất.")
    @GetMapping("/my-history")
    public ResponseEntity<List<StudentSubscriptionResponse>> getMyHistory(Authentication authentication) {
        return ResponseEntity.ok(subscriptionService.findMySubscriptionHistory(authentication.getName()));
    }

    // ==========================================
    // 👑 ENDPOINTS DÀNH CHO ADMIN QUẢN LÝ (CRUD + SORT)
    // ==========================================

    @Operation(summary = "[Admin] Lấy danh sách gói kèm Phân trang & Sắp xếp (Sort)", description = "Yêu cầu quyền ADMIN. Lấy danh sách toàn bộ gói cước, hỗ trợ phân trang và sắp xếp linh hoạt (Ví dụ: sắp xếp theo giá tăng/giảm dần, theo ngày tạo,...).")
    @GetMapping("/admin/plans")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<SubscriptionPlan>> getAllPlansForAdmin(
            @Parameter(description = "Số trang (bắt đầu từ 0)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Số lượng bản ghi trên một trang") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Cột cần sắp xếp (ví dụ: price, name, createdAt)") @RequestParam(defaultValue = "price") String sortBy,
            @Parameter(description = "Hướng sắp xếp (asc: tăng dần, desc: giảm dần)") @RequestParam(defaultValue = "asc") String sortDir
    ) {
        Page<SubscriptionPlan> plans = subscriptionService.getAllPlansForAdmin(page, size, sortBy, sortDir);
        return ResponseEntity.ok(plans);
    }

    @Operation(summary = "[Admin] Tạo mới một gói cước", description = "Yêu cầu quyền ADMIN. Thêm một tùy chọn gói cước mới vào hệ thống (ví dụ: Gói VIP Thử Nghiệm). Tên gói không được trùng lặp.")
    @PostMapping("/admin/plans")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SubscriptionPlan> createPlan(@RequestBody SubscriptionPlan plan) {
        return ResponseEntity.ok(subscriptionService.createPlan(plan));
    }

    @Operation(summary = "[Admin] Cập nhật thông tin gói cước", description = "Yêu cầu quyền ADMIN. Chỉnh sửa các thông số của gói cước như giá tiền, giới hạn câu hỏi, thời hạn hoặc bật/tắt trạng thái kinh doanh của gói.")
    @PutMapping("/admin/plans/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SubscriptionPlan> updatePlan(
            @Parameter(description = "ID của gói cước cần sửa") @PathVariable UUID id,
            @RequestBody SubscriptionPlan plan
    ) {
        return ResponseEntity.ok(subscriptionService.updatePlan(id, plan));
    }

    @Operation(summary = "[Admin] Xóa một gói cước", description = "Yêu cầu quyền ADMIN. Xóa hoàn toàn cấu hình gói cước ra khỏi bảng `subscription_plans` theo ID.")
    @DeleteMapping("/admin/plans/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deletePlan(
            @Parameter(description = "ID của gói cước cần xóa") @PathVariable UUID id
    ) {
        subscriptionService.deletePlan(id);
        return ResponseEntity.noContent().build();
    }
}