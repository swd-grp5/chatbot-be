package swdchatbox.system.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import swdchatbox.system.auth.dto.request.GoogleLoginRequest;
import swdchatbox.system.auth.dto.request.ForgotPasswordRequest;
import swdchatbox.system.auth.dto.request.LoginRequest;
import swdchatbox.system.auth.dto.request.RegisterRequest;
import swdchatbox.system.auth.dto.request.ResendVerificationRequest;
import swdchatbox.system.auth.dto.request.ResetPasswordRequest;
import swdchatbox.system.auth.dto.request.UpdateRoleRequest;
import swdchatbox.system.auth.dto.response.AuthResponse;
import swdchatbox.system.auth.dto.response.ResendVerificationResponse;
import swdchatbox.system.auth.service.AuthService;
import swdchatbox.system.auth.service.EmailVerificationService;
import swdchatbox.system.auth.service.PasswordResetService;
import swdchatbox.system.common.exception.ResourceNotFoundException;
import swdchatbox.system.role.repository.RoleRepository;
import swdchatbox.system.user.dto.response.UserResponse;
import swdchatbox.system.user.entity.User;
import swdchatbox.system.user.repository.UserRepository;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@SecurityRequirements
public class AuthController {

    private final AuthService authService;
    private final EmailVerificationService emailVerificationService;
    private final PasswordResetService passwordResetService;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    @Operation(summary = "Đăng ký tài khoản", description = "Dùng cho FE khi người dùng tạo tài khoản mới. Gửi đầy đủ thông tin theo `RegisterRequest`.")
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request
    ) {
        return ResponseEntity.ok(authService.register(request));
    }

    @Operation(summary = "Đăng nhập", description = "Dùng để xác thực người dùng bằng email/password. FE gửi payload theo `LoginRequest`.")
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request
    ) {
        return ResponseEntity.ok(authService.login(request));
    }

    @Operation(summary = "Đăng nhập bằng Google", description = "FE gửi token hoặc dữ liệu Google login theo `GoogleLoginRequest` để hệ thống tạo/đăng nhập tài khoản.")
    @PostMapping("/google")
    public ResponseEntity<AuthResponse> google(
            @Valid @RequestBody GoogleLoginRequest request
    ) {
        return ResponseEntity.ok(authService.loginWithGoogle(request));
    }

    @Operation(summary = "Xác thực email", description = "FE mở link xác thực email có kèm `token` trong query string. Ví dụ: `/auth/verify-email?token=...`.")
    @GetMapping("/verify-email")
    public ResponseEntity<?> verifyEmail(@Parameter(description = "Token xác thực email") @RequestParam("token") String token) {
        emailVerificationService.verifyToken(token);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Gửi lại email xác thực", description = "Dùng khi người dùng chưa nhận được email xác thực. FE gửi email theo `ResendVerificationRequest`.")
    @PostMapping("/resend-verification")
    public ResponseEntity<ResendVerificationResponse> resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        try {
            emailVerificationService.resendVerificationEmail(request.getEmail());
            log.info("Resend verification email succeeded for {}", request.getEmail());
            return ResponseEntity.ok(
                    ResendVerificationResponse.builder()
                            .success(true)
                            .message("Verification email has been sent successfully")
                            .build()
            );
        } catch (RuntimeException e) {
            log.error("Resend verification email failed for {}: {}", request.getEmail(), e.getMessage());
            return ResponseEntity.status(400).body(
                    ResendVerificationResponse.builder()
                            .success(false)
                            .message(e.getMessage())
                            .build()
            );
        }
    }


    @Operation(summary = "Quên mật khẩu", description = "FE gửi email để hệ thống gửi link/reset token đặt lại mật khẩu.")
    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.sendForgotPasswordEmail(request.getEmail());
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Đặt lại mật khẩu", description = "FE gửi token và mật khẩu mới để hoàn tất reset password.")
    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request.getToken(), request.getNewPassword());
        return ResponseEntity.ok().build();
    }

    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Lấy thông tin tài khoản hiện tại", description = "FE dùng API này để lấy profile của user đang đăng nhập. Cần gửi access token.")
    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(Authentication authentication) {
        String email = authentication.getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new swdchatbox.system.common.exception.AuthException("User not found"));
        return ResponseEntity.ok(authService.toUserResponse(user));
    }

    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Cập nhật role người dùng", description = "API nội bộ/admin dùng để đổi role cho một user theo `userId`. Cần quyền ADMIN.")
    @PutMapping("/role/{userId}")
    public ResponseEntity<?> updateRole(
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateRoleRequest request
    ) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new swdchatbox.system.common.exception.AuthException("User not found"));
        user.setRole(roleRepository.findById(request.getRoleId())
                .orElseThrow(() -> new ResourceNotFoundException("Role not found")));
        userRepository.save(user);
        return ResponseEntity.ok().build();
    }
}