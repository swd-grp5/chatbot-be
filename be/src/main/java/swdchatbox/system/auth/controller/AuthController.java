package swdchatbox.system.auth.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
import swdchatbox.system.auth.service.AuthService;
import swdchatbox.system.auth.service.EmailVerificationService;
import swdchatbox.system.auth.service.PasswordResetService;
import swdchatbox.system.user.dto.response.UserResponse;
import swdchatbox.system.user.entity.User;
import swdchatbox.system.user.repository.UserRepository;

import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final EmailVerificationService emailVerificationService;
    private final PasswordResetService passwordResetService;
    private final UserRepository userRepository;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request
    ) {
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request
    ) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/google")
    public ResponseEntity<AuthResponse> google(
            @Valid @RequestBody GoogleLoginRequest request
    ) {
        return ResponseEntity.ok(authService.loginWithGoogle(request));
    }

    @GetMapping("/verify-email")
    public ResponseEntity<?> verifyEmail(@RequestParam("token") String token) {
        emailVerificationService.verifyToken(token);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<?> resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        emailVerificationService.resendVerificationEmail(request.getEmail());
        return ResponseEntity.ok().build();
    }


    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.sendForgotPasswordEmail(request.getEmail());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request.getToken(), request.getNewPassword());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(Authentication authentication) {
        String email = authentication.getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return ResponseEntity.ok(authService.toUserResponse(user));
    }

    @PutMapping("/role/{userId}")
    public ResponseEntity<?> updateRole(
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateRoleRequest request
    ) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setRole(request.getRole());
        userRepository.save(user);
        return ResponseEntity.ok().build();
    }
}