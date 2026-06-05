package swdchatbox.system.subscription.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import swdchatbox.system.subscription.dto.response.StudentSubscriptionResponse;
import swdchatbox.system.subscription.service.StudentSubscriptionService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/subscriptions")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class StudentSubscriptionController {

    private final StudentSubscriptionService subscriptionService;

    @Operation(summary = "Đăng ký theo dõi môn học", description = "FE dùng để cho student subscribe một subject. SubjectId truyền trên URL.")
    @PostMapping("/subjects/{subjectId}")
    public ResponseEntity<StudentSubscriptionResponse> subscribe(
            @Parameter(description = "ID môn học cần theo dõi") @PathVariable UUID subjectId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(subscriptionService.subscribe(subjectId, authentication.getName()));
    }

    @Operation(summary = "Hủy theo dõi môn học", description = "FE dùng khi student muốn hủy subscribe subject. Sau khi xóa trả về HTTP 204.")
    @DeleteMapping("/subjects/{subjectId}")
    public ResponseEntity<Void> unsubscribe(
            @Parameter(description = "ID môn học cần hủy theo dõi") @PathVariable UUID subjectId,
            Authentication authentication
    ) {
        subscriptionService.unsubscribe(subjectId, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Lấy danh sách môn học đã theo dõi của tôi", description = "FE dùng để hiển thị danh sách subscription của user đang đăng nhập. Có thể lọc theo active.")
    @GetMapping("/me")
    public ResponseEntity<List<StudentSubscriptionResponse>> mySubscriptions(
            @Parameter(description = "Lọc theo trạng thái active") @RequestParam(required = false) Boolean active,
            Authentication authentication
    ) {
        return ResponseEntity.ok(subscriptionService.findMySubscriptions(authentication.getName(), active));
    }
}

