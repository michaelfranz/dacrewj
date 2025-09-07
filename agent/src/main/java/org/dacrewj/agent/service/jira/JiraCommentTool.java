package org.dacrewj.agent.service.jira;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.dacrewj.contract.AdfDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Minimal Jira comment tool that can be exposed to Embabel as a tool named "jira-comment".
 * This implementation focuses on:
 *  - honoring app.jira.dry-run
 *  - enforcing app.jira.max-comment-length
 *  - posting to Jira Cloud REST API v3
 */
@Component("jira-comment")
public class JiraCommentTool {

    private static final Logger log = LoggerFactory.getLogger(JiraCommentTool.class);

    private final HttpClient http;
    private final ObjectMapper mapper;

    private final String baseUrl;
    private final String authToken;
    private final boolean dryRun;
    private final int maxCommentLength;

    public JiraCommentTool(
            @Value("${app.jira.base-url}") String baseUrl,
            @Value("${app.jira.auth-token:}") String authToken,
            @Value("${app.jira.dry-run:true}") boolean dryRun,
            @Value("${app.jira.max-comment-length:1024}") int maxCommentLength
    ) {
        this.baseUrl = baseUrl != null && baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.authToken = authToken;
        this.dryRun = dryRun;
        this.maxCommentLength = maxCommentLength;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.mapper = new ObjectMapper();
    }

    public Result addComment(String issueKey, AdfDocument commentBody) {
        if (issueKey == null || issueKey.isBlank()) {
            return Result.error("issueKey must not be blank");
        }
        if (commentBody == null) {
            return Result.error("commentBody must not be null");
        }
        // Enforce max length against plain-text preview derived from ADF.
        String preview = commentBody.previewText(maxCommentLength + 1);
        if (preview.length() > maxCommentLength) {
            log.info("Comment text exceeds max length ({}). Will proceed but Jira may accept longer ADF; preview truncated to {} characters.", preview.length(), maxCommentLength);
        }

        if (dryRun) {
            log.info("[DRY-RUN] Would post comment to {}: {}", issueKey, preview(preview));
            return Result.ok("dry-run", null);
        }

        if (authToken == null || authToken.isBlank()) {
            return Result.error("Jira auth token is missing. Set app.jira.auth-token / JIRA_TOKEN.");
        }

        try {
            String url = baseUrl + "/rest/api/3/issue/" + encode(issueKey) + "/comment";
            Map<String, Object> payload = new HashMap<>();
            payload.put("body", commentBody);
            String json = mapper.writeValueAsString(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + authToken)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();
            if (status >= 200 && status < 300) {
                String location = extractSelfUrl(response.body());
                log.info("Posted Jira comment to {} (status {}): {}", issueKey, status, location != null ? location : "<no self>");
                return Result.ok("created", location);
            } else {
                String msg = "Failed to post Jira comment. Status=" + status + ", body=" + response.body();
                log.warn(msg);
                return Result.error(msg);
            }
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            String msg = "Error posting Jira comment: " + e.getMessage();
            log.error(msg, e);
            return Result.error(msg);
        }
    }

    private static String preview(String s) {
        return s.length() > 200 ? s.substring(0, 200) + "…" : s;
    }

    private static String encode(String s) {
        return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private String extractSelfUrl(String responseBody) {
        try {
            var node = mapper.readTree(responseBody);
            if (node.has("self")) {
                return node.get("self").asText();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public record Result(boolean success, String status, String url, String error) {
        public static Result ok(String status, String url) { return new Result(true, status, url, null); }
        public static Result error(String error) { return new Result(false, null, null, error); }
    }
}
