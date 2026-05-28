package swdchatbox.system.auth.service;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import swdchatbox.system.auth.entity.PasswordResetToken;
import swdchatbox.system.auth.repository.PasswordResetTokenRepository;
import swdchatbox.system.user.entity.User;
import swdchatbox.system.user.enums.AuthProvider;
import swdchatbox.system.user.repository.UserRepository;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final PasswordResetTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final JavaMailSender mailSender;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.mail.from}")
    private String from;

    @Value("${app.mail.from-name}")
    private String fromName;

    @Value("${app.mail.reply-to:}")
    private String replyTo;

    @Value("${app.mail.verify-base-url}")
    private String baseUrl;

    public void sendForgotPasswordEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Email not found"));

        if (user.getProvider() != null && user.getProvider() != AuthProvider.LOCAL) {
            throw new RuntimeException("Tài khoản này đăng nhập bằng Google, không dùng reset password.");
        }

        String token = UUID.randomUUID().toString().replace("-", "");
        PasswordResetToken prt = PasswordResetToken.builder()
                .token(token)
                .user(user)
                .expiresAt(LocalDateTime.now().plusDays(1))
                .build();
        tokenRepository.save(prt);

        String resetLink = baseUrl + "/api/auth/reset-password?token=" + token;
        String subject = "Đặt lại mật khẩu " + fromName;
        String html = buildResetPasswordHtml(user.getFullName(), resetLink);

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    mimeMessage,
                    MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
                    StandardCharsets.UTF_8.name()
            );
            helper.setTo(user.getEmail());
            helper.setFrom(from, fromName);
            if (replyTo != null && !replyTo.isBlank()) {
                helper.setReplyTo(replyTo);
            }
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(mimeMessage);
        } catch (Exception e) {
            throw new RuntimeException("Failed to send reset password email");
        }
    }

    public void resetPassword(String token, String newPassword) {
        PasswordResetToken prt = tokenRepository.findByToken(token)
                .orElseThrow(() -> new RuntimeException("Invalid reset token"));

        if (prt.getUsedAt() != null) {
            throw new RuntimeException("Reset token already used");
        }
        if (prt.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Reset token expired");
        }

        User user = prt.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setProvider(AuthProvider.LOCAL);
        userRepository.save(user);

        prt.setUsedAt(LocalDateTime.now());
        tokenRepository.save(prt);
    }

    private String buildResetPasswordHtml(String fullName, String resetLink) {
        String name = (fullName == null || fullName.isBlank()) ? "bạn" : fullName;
        return """
                <!doctype html>
                <html lang="vi">
                <head>
                  <meta charset="UTF-8" />
                  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                  <title>Đặt lại mật khẩu</title>
                </head>
                <body style="margin:0;padding:0;background:#f4f6f8;font-family:Arial,Helvetica,sans-serif;color:#111827;">
                  <div style="max-width:640px;margin:0 auto;padding:24px;">
                    <div style="background:#ffffff;border-radius:14px;overflow:hidden;border:1px solid #e5e7eb;">
                      <div style="padding:20px 24px;background:linear-gradient(135deg,#f97316,#ef4444);color:#ffffff;">
                        <div style="font-size:18px;font-weight:700;">%s</div>
                        <div style="opacity:0.9;margin-top:6px;">Đặt lại mật khẩu tài khoản của bạn</div>
                      </div>
                      <div style="padding:24px;">
                        <div style="font-size:18px;font-weight:700;margin-bottom:10px;">Xin chào, %s</div>
                        <div style="line-height:1.6;color:#374151;">
                          Nhấn nút bên dưới để đặt lại mật khẩu. Liên kết có hiệu lực trong <b>24 giờ</b>.
                        </div>
                        <div style="margin:22px 0;">
                          <a href="%s"
                             style="display:inline-block;background:#ef4444;color:#ffffff;text-decoration:none;
                                    padding:12px 18px;border-radius:10px;font-weight:700;">
                            Đặt lại mật khẩu
                          </a>
                        </div>
                        <div style="font-size:12px;color:#6b7280;line-height:1.5;">
                          Nếu bạn không yêu cầu đặt lại mật khẩu, bạn có thể bỏ qua email này.
                        </div>
                        <hr style="border:none;border-top:1px solid #e5e7eb;margin:18px 0;">
                        <div style="font-size:12px;color:#6b7280;line-height:1.5;">
                          Nếu nút không bấm được, copy link sau vào trình duyệt:
                          <div style="word-break:break-all;color:#111827;margin-top:6px;">%s</div>
                        </div>
                      </div>
                      <div style="padding:14px 24px;background:#f9fafb;border-top:1px solid #e5e7eb;color:#6b7280;font-size:12px;">
                        © %d %s
                      </div>
                    </div>
                  </div>
                </body>
                </html>
                """.formatted(
                fromName,
                escapeHtml(name),
                resetLink,
                resetLink,
                java.time.Year.now().getValue(),
                fromName
        );
    }

    private String escapeHtml(String s) {
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}

