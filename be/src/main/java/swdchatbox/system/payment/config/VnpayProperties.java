package swdchatbox.system.payment.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.vnpay")
public class VnpayProperties {

    private String tmnCode;

    private String hashSecret;

    private String payUrl = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html";

    private String returnUrl = "http://localhost:8080/api/payments/vnpay/return";

    private String version = "2.1.0";

    private String command = "pay";

    private String orderType = "other";

    private String locale = "vn";

    private String currencyCode = "VND";

    private int expireMinutes = 15;
}
