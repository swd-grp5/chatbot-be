package swdchatbox.system.payment.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class VnpayStartupLogger {

    private final VnpayProperties vnpay;

    @PostConstruct
    void logConfig() {
        String tmnCode = vnpay.getTmnCode();
        String hashSecret = vnpay.getHashSecret();
        boolean tmnConfigured = tmnCode != null && !tmnCode.isBlank();
        boolean secretConfigured = hashSecret != null && !hashSecret.isBlank();

        if (!tmnConfigured || !secretConfigured) {
            log.error("VNPAY config incomplete: tmnCodeConfigured={} hashSecretConfigured={}",
                    tmnConfigured, secretConfigured);
            return;
        }

        log.info("VNPAY config loaded: tmnCode={} hashSecretLength={} returnUrl={}",
                tmnCode, hashSecret != null ? hashSecret.length() : 0, vnpay.getReturnUrl());
    }
}
