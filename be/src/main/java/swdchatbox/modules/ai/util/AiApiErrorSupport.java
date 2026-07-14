package swdchatbox.modules.ai.util;

import org.springframework.web.client.RestClientResponseException;

/**
 * Formats AI API failures with provider/model/endpoint/status so logs are actionable.
 */
public final class AiApiErrorSupport {

    private static final int MAX_BODY_LENGTH = 500;

    private AiApiErrorSupport() {
    }

    public static void logFailure(
            org.slf4j.Logger log,
            String operation,
            String provider,
            String model,
            String endpoint,
            Exception e) {
        String status = statusOf(e);
        String body = responseBodyOf(e);
        log.error(
                "[ai] op={} provider={} model={} endpoint={} status={} errorType={} message={} body={}",
                operation,
                provider,
                model,
                endpoint,
                status,
                e.getClass().getSimpleName(),
                safeMessage(e),
                body,
                e);
    }

    public static RuntimeException wrap(
            String operation,
            String provider,
            String model,
            String endpoint,
            Exception e) {
        String status = statusOf(e);
        String detail = safeMessage(e);
        String body = responseBodyOf(e);
        String message = "[ai] op=" + operation
                + " provider=" + provider
                + " model=" + model
                + " endpoint=" + endpoint
                + " status=" + status
                + " error=" + detail;
        if (!"n/a".equals(body)) {
            message += " body=" + body;
        }
        return new RuntimeException(message, e);
    }

    private static String statusOf(Throwable e) {
        RestClientResponseException http = findHttp(e);
        if (http != null) {
            return String.valueOf(http.getStatusCode().value());
        }
        return "n/a";
    }

    private static String responseBodyOf(Throwable e) {
        RestClientResponseException http = findHttp(e);
        if (http == null) {
            return "n/a";
        }
        String body = http.getResponseBodyAsString();
        if (body == null || body.isBlank()) {
            return "n/a";
        }
        String compact = body.replaceAll("\\s+", " ").trim();
        if (compact.length() > MAX_BODY_LENGTH) {
            return compact.substring(0, MAX_BODY_LENGTH) + "...";
        }
        return compact;
    }

    private static RestClientResponseException findHttp(Throwable e) {
        Throwable current = e;
        while (current != null) {
            if (current instanceof RestClientResponseException http) {
                return http;
            }
            current = current.getCause();
        }
        return null;
    }

    private static String safeMessage(Throwable e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            return e.getClass().getSimpleName();
        }
        // Never leak API keys that may appear in request URLs embedded in RestClient errors.
        return message.replaceAll("(?i)([?&]key=)[^&\\s]+", "$1***")
                .replaceAll("(?i)(Bearer\\s+)\\S+", "$1***");
    }
}
