package org.dacrewj.jira_ingester.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;

@Configuration
public class WebhookSecurityConfig implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(WebhookSecurityConfig.class);

    @Value("${app.webhook.secret:}")
    private String webhookSecret;

    @Value("${app.webhook.signature-header:X-Hub-Signature}")
    private String signatureHeader;

    @Value("${app.webhook.algorithm:HmacSHA256}")
    private String algorithm;

    @PostConstruct
    void logConfig() {
        if (!StringUtils.hasText(webhookSecret)) {
            log.warn("Webhook secret not configured; requests to webhook endpoints will be rejected with 500");
        } else {
            log.info("Webhook HMAC verification enabled using header '{}' and algorithm '{}'", signatureHeader, algorithm);
        }
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // HMAC must run first
        registry.addInterceptor(new HmacInterceptor()).addPathPatterns("/webhook/jira");
        // Then log the webhook safely after signature verification
        registry.addInterceptor(new WebhookLoggingInterceptor()).addPathPatterns("/webhook/jira");
    }

    class HmacInterceptor implements HandlerInterceptor {
        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
            try {
                if (!StringUtils.hasText(webhookSecret)) {
                    log.error("Webhook secret not configured");
                    response.sendError(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Webhook secret not configured");
                    return false;
                }

                String provided = request.getHeader(signatureHeader);
                if (!StringUtils.hasText(provided)) {
                    log.warn("Missing {} header", signatureHeader);
                    response.sendError(HttpStatus.UNAUTHORIZED.value(), "Missing signature header");
                    return false;
                }

                byte[] body = request.getInputStream().readAllBytes();

                // Compute HMAC
                Mac mac = Mac.getInstance(algorithm);
                mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), algorithm));
                byte[] digest = mac.doFinal(body);
                String expectedHex = toHex(digest);

                // Common format: "sha256=hexdigest"; accept raw hex too.
                String expectedPrefixed = "sha256=" + expectedHex;
                boolean match = constantTimeEquals(provided, expectedPrefixed) || constantTimeEquals(provided, expectedHex);

                if (!match) {
                    log.warn("Invalid HMAC signature. Received header: {}", provided);
                    response.sendError(HttpStatus.UNAUTHORIZED.value(), "Invalid signature");
                    return false;
                }

                // cache body for downstream
                request.setAttribute("cachedRequestBody", body);
                return true;
            } catch (Exception e) {
                log.error("Error during HMAC verification", e);
                try {
                    response.sendError(HttpStatus.INTERNAL_SERVER_ERROR.value(), "HMAC verification error");
                } catch (Exception ignored) {}
                return false;
            }
        }

        private String toHex(byte[] bytes) {
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        }

        private boolean constantTimeEquals(String a, String b) {
            if (a == null || b == null) return false;
            byte[] aBytes = a.getBytes(StandardCharsets.UTF_8);
            byte[] bBytes = b.getBytes(StandardCharsets.UTF_8);
            if (aBytes.length != bBytes.length) return false;
            int result = 0;
            for (int i = 0; i < aBytes.length; i++) {
                result |= aBytes[i] ^ bBytes[i];
            }
            return result == 0;
        }
    }

    @SuppressWarnings("InnerClassMayBeStatic")
	class WebhookLoggingInterceptor implements HandlerInterceptor {
        @Value("${app.webhook.log-dir:}")
        private String configuredLogDir;

        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
            try {
                // Obtain the body: prefer cached by HMAC interceptor
                byte[] body = (byte[]) request.getAttribute("cachedRequestBody");
                if (body == null) {
                    body = request.getInputStream().readAllBytes();
                    request.setAttribute("cachedRequestBody", body);
                }

                // Resolve log dir: env DACREW_LOG_DIR overrides property; fallback to "logs"
                String envDir = System.getenv("DACREW_LOG_DIR");
                String dir = (envDir != null && !envDir.isBlank()) ? envDir : (StringUtils.hasText(configuredLogDir) ? configuredLogDir : "logs");

                java.nio.file.Path logPath = java.nio.file.Path.of(dir);
                java.nio.file.Files.createDirectories(logPath);

                String timestamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"));
                java.nio.file.Path file = logPath.resolve("webhook-" + timestamp + ".log");

                StringBuilder sb = new StringBuilder();
                sb.append("Webhook received at: ").append(java.time.ZonedDateTime.now().toString()).append('\n');

                // Query params
                String query = request.getQueryString();
                if (query != null && !query.isBlank()) {
                    sb.append("Query parameters: ").append(query).append('\n');
                }

                // Pretty-print JSON if possible
                try {
                    com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(body);
                    sb.append("Webhook payload:\n").append(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(node)).append('\n');
                } catch (Exception ex) {
                    // Fallback to raw body
                    sb.append("Webhook payload (raw):\n").append(new String(body, java.nio.charset.StandardCharsets.UTF_8)).append('\n');
                }

                java.nio.file.Files.writeString(file, sb.toString(), java.nio.charset.StandardCharsets.UTF_8);
                log.info("Webhook logged to: {}", file);
            } catch (Exception e) {
                log.error("Failed to log webhook request", e);
                // Do not block request processing
            }
            return true;
        }
}

}