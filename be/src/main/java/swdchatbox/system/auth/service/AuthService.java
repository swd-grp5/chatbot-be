package swdchatbox.system.auth.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import swdchatbox.security.JwtService;
import swdchatbox.system.auth.dto.request.GoogleLoginRequest;
import swdchatbox.system.auth.dto.request.LoginRequest;
import swdchatbox.system.auth.dto.request.RegisterRequest;
import swdchatbox.system.auth.dto.response.AuthResponse;
import swdchatbox.system.common.exception.AuthException;
import swdchatbox.system.role.RoleCodes;
import swdchatbox.system.role.service.RoleService;
import swdchatbox.system.user.dto.response.UserResponse;
import swdchatbox.system.user.entity.User;
import swdchatbox.system.user.enums.AuthProvider;
import swdchatbox.system.user.mapper.UserMapper;
import swdchatbox.system.user.repository.UserRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailVerificationService emailVerificationService;
    private final RoleService roleService;

    @Value("${app.google.client-id}")
    private String googleClientId;

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new AuthException("This email is already registered");
        }

        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new AuthException("Passwords do not match");
        }

        User user = User.builder()
                .fullName(request.getFullName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(roleService.findRoleByCode(RoleCodes.STUDENT))
                .provider(AuthProvider.LOCAL)
                .emailVerified(false)
                .isActive(false)
                .build();

        userRepository.save(user);
        emailVerificationService.sendVerificationEmail(user);

        return AuthResponse.builder()
                .token(null)
                .user(toUserResponse(user))
                .message("Đăng ký thành công. Vui lòng kiểm tra email để xác minh tài khoản.")
                .build();
    }

    public AuthResponse login(LoginRequest request) {
        log.info("Login attempt for email={}", request.getEmail());

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> {
                    log.warn("Login failed: email not registered email={}", request.getEmail());
                    return new AuthException("This email is not registered");
                });

        if (user.getProvider() != null && user.getProvider() != AuthProvider.LOCAL) {
            log.warn("Login failed: account uses non-local provider email={} provider={}", request.getEmail(), user.getProvider());
            throw new AuthException("This account uses Google sign-in. Please use Google login.");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            log.warn("Login failed: incorrect password email={}", request.getEmail());
            throw new AuthException("Incorrect password");
        }

        if (!Boolean.TRUE.equals(user.getIsActive())) {
            log.warn("Login failed: account inactive email={}", request.getEmail());
            throw new AuthException("Your account is not activated yet. Please verify your email.");
        }

        String token = jwtService.generateToken(user, request.getRememberMe());
        log.info("Login success for email={}", request.getEmail());

        return AuthResponse.builder()
                .token(token)
                .user(toUserResponse(user))
                .build();
    }

    public AuthResponse loginWithGoogle(GoogleLoginRequest request) {
        if (googleClientId == null || googleClientId.isBlank()) {
            throw new AuthException("Google client ID is not configured");
        }

        GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                new NetHttpTransport(),
                new GsonFactory()
        ).setAudience(List.of(googleClientId)).build();

        GoogleIdToken idToken;
        try {
            idToken = verifier.verify(request.getIdToken());
        } catch (Exception e) {
            throw new AuthException("Invalid Google token");
        }

        if (idToken == null) {
            throw new RuntimeException("Invalid Google token");
        }

        GoogleIdToken.Payload payload = idToken.getPayload();
        String email = payload.getEmail();
        String name = (String) payload.get("name");
        Boolean emailVerified = payload.getEmailVerified();

        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            user = User.builder()
                    .fullName(name != null ? name : email)
                    .email(email)
                    .password(null)
                    .role(roleService.findRoleByCode(RoleCodes.STUDENT))
                    .provider(AuthProvider.GOOGLE)
                    .emailVerified(emailVerified != null && emailVerified)
                    .isActive(true)
                    .build();
        } else {
            user.setProvider(AuthProvider.GOOGLE);
            user.setPassword(null);
            user.setIsActive(true);
            if (emailVerified != null && emailVerified) {
                user.setEmailVerified(true);
            }
            if (name != null && !name.isBlank()) {
                user.setFullName(name);
            }
        }

        userRepository.save(user);

        String token = jwtService.generateToken(user, request.getRememberMe());

        return AuthResponse.builder()
                .token(token)
                .user(toUserResponse(user))
                .build();
    }

    public UserResponse toUserResponse(User user) {
        return UserMapper.toResponse(user);
    }
}