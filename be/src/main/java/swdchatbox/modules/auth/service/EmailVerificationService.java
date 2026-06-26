package swdchatbox.modules.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import swdchatbox.modules.auth.entity.EmailVerificationToken;
import swdchatbox.modules.auth.repository.EmailVerificationTokenRepository;
import swdchatbox.modules.user.entity.User;
import swdchatbox.modules.user.enums.AuthProvider;
import swdchatbox.modules.user.repository.UserRepository;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private static final String RESEND_API_URL = "https://api.resend.com/emails";

    private final EmailVerificationTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Value("${app.mail.from:}")
    private String from;

    @Value("${app.mail.from-name:SWDChatBox}")
    private String fromName;

    @Value("${app.mail.reply-to:}")
    private String replyTo;

    @Value("${app.mail.verify-base-url:http://localhost:8080}")
    private String verifyBaseUrl;

    @Value("${app.mail.resend.api-key:}")
    private String resendApiKey;

    @Transactional
    public void sendVerificationEmail(User user) {
        validateMailConfig();

        String token = UUID.randomUUID().toString().replace("-", "");

        EmailVerificationToken evt = EmailVerificationToken.builder()
                .token(token)
                .user(user)
                .expiresAt(LocalDateTime.now().plusDays(1))
                .build();

        tokenRepository.save(evt);

        String verifyLink = verifyBaseUrl + "/auth/verify-email?token=" + token;
        String subject = "Xác minh tài khoản " + fromName;
        String html = buildVerifyEmailHtml(user.getFullName(), verifyLink);

        try {
            sendViaResend(user.getEmail(), subject, html);
            log.info("Verification email sent successfully to {}", user.getEmail());
        } catch (Exception e) {
            log.error("Unexpected error when sending verification email. From: {}, To: {}", from, user.getEmail(), e);
            throw new RuntimeException("Failed to send verification email: " + e.getMessage(), e);
        }
    }

    @Transactional
    public void verifyToken(String token) {
        EmailVerificationToken evt = tokenRepository.findByToken(token)
                .orElseThrow(() -> new RuntimeException("Invalid verification token"));

        if (evt.getUsedAt() != null) {
            throw new RuntimeException("Verification token already used");
        }

        if (evt.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Verification token expired");
        }

        User user = evt.getUser();
        user.setEmailVerified(true);
        user.setIsActive(true);
        userRepository.save(user);

        evt.setUsedAt(LocalDateTime.now());
        tokenRepository.save(evt);
    }

    @Transactional
    public void resendVerificationEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Email not found"));

        if (user.getProvider() != null && user.getProvider() != AuthProvider.LOCAL) {
            throw new RuntimeException("Tài khoản này đăng nhập bằng Google, không cần xác minh email.");
        }

        if (Boolean.TRUE.equals(user.getEmailVerified())) {
            throw new RuntimeException("Email already verified");
        }

        sendVerificationEmail(user);
    }

    private void sendViaResend(String to, String subject, String html) throws Exception {
        if (resendApiKey == null || resendApiKey.isBlank()) {
            throw new RuntimeException("Mail config error: app.mail.resend.api-key is empty. Check RESEND_API_KEY.");
        }

        if (from == null || from.isBlank()) {
            throw new RuntimeException("Mail config error: app.mail.from is empty. Check SPRING_MAIL_FROM.");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("from", fromName + " <" + from + ">");
        payload.put("to", new String[] { to });
        payload.put("subject", subject);
        payload.put("html", html);

        if (replyTo != null && !replyTo.isBlank()) {
            payload.put("reply_to", replyTo);
        }

        String requestBody = objectMapper.writeValueAsString(payload);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(RESEND_API_URL))
                .header("Authorization", "Bearer " + resendApiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RuntimeException("Resend API error: HTTP " + response.statusCode() + " - " + response.body());
        }
    }

    private void validateMailConfig() {
        if (from == null || from.isBlank()) {
            throw new RuntimeException("Mail config error: app.mail.from is empty. Check MAIL_FROM.");
        }

        if (resendApiKey == null || resendApiKey.isBlank()) {
            throw new RuntimeException("Mail config error: app.mail.resend.api-key is empty. Check RESEND_API_KEY.");
        }

        if (fromName == null || fromName.isBlank()) {
            fromName = "SWDChatBox";
        }

        if (verifyBaseUrl == null || verifyBaseUrl.isBlank()) {
            verifyBaseUrl = "http://localhost:8080";
        }

        log.info("Mail config loaded. From: {}, FromName: {}, VerifyBaseUrl: {}", from, fromName, verifyBaseUrl);
    }

    private String buildVerifyEmailHtml(String fullName, String verifyLink) {
        String name = (fullName == null || fullName.isBlank()) ? "bạn" : fullName;

        return """
                <!doctype html>
                <html lang="vi">
                <head>
                  <meta charset="UTF-8" />
                  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                  <title>Xác minh tài khoản</title>
                </head>
                <body style="margin:0;padding:0;background:#f4f6f8;font-family:Arial,Helvetica,sans-serif;color:#111827;">
                  <div style="max-width:640px;margin:0 auto;padding:24px;">
                    <div style="background:#ffffff;border-radius:14px;overflow:hidden;border:1px solid #e5e7eb;">
                      <div style="padding:20px 24px;background:linear-gradient(135deg,#0ea5e9,#22c55e);color:#ffffff;">
                        <div style="font-size:18px;font-weight:700;">%s</div>
                        <div style="opacity:0.9;margin-top:6px;">Xác minh email để kích hoạt tài khoản của bạn</div>
                      </div>

                      <div style="padding:24px;">
                        <div style="font-size:18px;font-weight:700;margin-bottom:10px;">Xin chào, %s</div>
                        <div style="line-height:1.6;color:#374151;">
                          Cảm ơn bạn đã đăng ký tài khoản. Vui lòng nhấn nút bên dưới để xác minh email của bạn.
                          Liên kết có hiệu lực trong <b>24 giờ</b>.
                        </div>

                        <div style="margin:22px 0;">
                          <a href="%s"
                             style="display:inline-block;background:#22c55e;color:#ffffff;text-decoration:none;
                                    padding:12px 18px;border-radius:10px;font-weight:700;">
                            Xác minh ngay
                          </a>
                        </div>

                        <div style="font-size:12px;color:#6b7280;line-height:1.5;">
                          Nếu bạn không đăng ký tài khoản này, bạn có thể bỏ qua email này.
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
                escapeHtml(fromName),
                escapeHtml(name),
                verifyLink,
                verifyLink,
                Year.now().getValue(),
                escapeHtml(fromName)
        );
    }

    private String escapeHtml(String s) {
        if (s == null) {
            return "";
        }

        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
