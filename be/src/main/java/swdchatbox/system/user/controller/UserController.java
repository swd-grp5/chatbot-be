package swdchatbox.system.user.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import swdchatbox.system.auth.service.AuthService;
import swdchatbox.system.common.dto.PageResponse;
import swdchatbox.system.user.dto.response.UserResponse;
import swdchatbox.system.user.entity.User;
import swdchatbox.system.user.mapper.UserMapper;
import swdchatbox.system.user.repository.UserRepository;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserRepository userRepository;
    private final AuthService authService;

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

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser(Authentication authentication) {
        String email = authentication.getName();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new swdchatbox.system.common.exception.AuthException("User not found"));

        return ResponseEntity.ok(UserMapper.toResponse(user));
    }
}
