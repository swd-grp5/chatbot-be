package swdchatbox.system.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import swdchatbox.system.common.dto.PageResponse;
import swdchatbox.system.user.dto.response.UserResponse;
import swdchatbox.system.user.entity.User;
import swdchatbox.system.user.mapper.UserMapper;
import swdchatbox.system.user.repository.UserRepository;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserRepository userRepository;

    @Operation(summary = "Lấy danh sách người dùng", description = "FE dùng để hiển thị bảng users có phân trang. Spring sẽ tự map params page/size/sort qua Pageable.")
    @GetMapping
    public ResponseEntity<PageResponse<UserResponse>> findAll(Pageable pageable) {
        var page = userRepository.findAll(pageable);
        return ResponseEntity.ok(PageResponse.<UserResponse>builder()
                .content(page.getContent().stream().map(UserMapper::toResponse).toList())
                .page(page.getNumber())
                .pageSize(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .empty(page.isEmpty())
                .build());
    }

  
}
