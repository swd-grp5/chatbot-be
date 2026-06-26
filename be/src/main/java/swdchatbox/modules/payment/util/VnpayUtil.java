package swdchatbox.modules.payment.util;

import jakarta.servlet.http.HttpServletRequest;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Tiện ích ký/checksum cho cổng VNPAY (chuẩn v2.1.0).
 * VNPAY sử dụng HMAC-SHA512 với khoá là vnp_HashSecret và dữ liệu là chuỗi
 * query đã được URL-encode (US_ASCII), sắp xếp theo thứ tự alphabet của tên tham số.
 */
public final class VnpayUtil {

    private static final SecureRandom RANDOM = new SecureRandom();

    private VnpayUtil() {
    }

    /**
     * Tạo chuỗi dữ liệu hash từ các tham số (đã URL-encode value), bỏ qua các giá trị rỗng.
     * Tham số đã được sắp xếp tăng dần theo tên trước khi đưa vào đây.
     */
    public static String buildHashData(Map<String, String> sortedParams) {
        StringBuilder hashData = new StringBuilder();
        List<String> fieldNames = new ArrayList<>(sortedParams.keySet());
        for (String fieldName : fieldNames) {
            String value = sortedParams.get(fieldName);
            if (value == null || value.isEmpty()) {
                continue;
            }
            if (hashData.length() > 0) {
                hashData.append('&');
            }
            hashData.append(fieldName)
                    .append('=')
                    .append(URLEncoder.encode(value, StandardCharsets.US_ASCII));
        }
        return hashData.toString();
    }

    /**
     * Tạo chuỗi query để redirect sang VNPAY (URL-encode cả tên tham số và giá trị).
     */
    public static String buildQuery(Map<String, String> sortedParams) {
        StringBuilder query = new StringBuilder();
        List<String> fieldNames = new ArrayList<>(sortedParams.keySet());
        for (String fieldName : fieldNames) {
            String value = sortedParams.get(fieldName);
            if (value == null || value.isEmpty()) {
                continue;
            }
            if (query.length() > 0) {
                query.append('&');
            }
            query.append(URLEncoder.encode(fieldName, StandardCharsets.US_ASCII))
                    .append('=')
                    .append(URLEncoder.encode(value, StandardCharsets.US_ASCII));
        }
        return query.toString();
    }

    public static String hmacSHA512(String key, String data) {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("VNPAY hash secret is not configured");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            byte[] result = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(2 * result.length);
            for (byte b : result) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Cannot compute VNPAY HMAC-SHA512 signature", e);
        }
    }

    /**
     * Lấy IP của client để gửi sang VNPAY (vnp_IpAddr).
     */
    public static String resolveIpAddress(HttpServletRequest request) {
        if (request == null) {
            return "127.0.0.1";
        }
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank()) {
            return ip.split(",")[0].trim();
        }
        ip = request.getRemoteAddr();
        return (ip == null || ip.isBlank()) ? "127.0.0.1" : ip;
    }

    /**
     * Sinh số ngẫu nhiên dùng cho một phần mã giao dịch (vnp_TxnRef).
     */
    public static String randomNumber(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }
}
