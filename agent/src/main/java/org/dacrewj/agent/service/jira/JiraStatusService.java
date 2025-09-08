package org.dacrewj.agent.service.jira;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

@Service
public class JiraStatusService {

    private static final Logger log = LoggerFactory.getLogger(JiraStatusService.class);

    private final String baseUrl;
    private final String authToken;
    private final boolean dryRun;
    private final HttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();

    public JiraStatusService(
            @Value("${app.jira.base-url}") String baseUrl,
            @Value("${app.jira.auth-token:}") String authToken,
            @Value("${app.jira.dry-run:true}") boolean dryRun,
            @Value("${app.jira.max-comment-length:1024}") int maxCommentLength
    ) {
        this.baseUrl = baseUrl != null && baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.authToken = authToken;
        this.dryRun = dryRun;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    public void updateStatus(String key, String status) {
        if (key == null || key.isBlank()) {
            log.warn("updateStatus called with blank key");
            return;
        }
        if (status == null || status.isBlank()) {
            log.warn("updateStatus called with blank status for {}", key);
            return;
        }
        if (dryRun) {
            log.info("[DRY-RUN] Would update Jira issue {} to status '{}'", key, status);
            return;
        }
        if (authToken == null || authToken.isBlank()) {
            log.warn("Jira auth token is missing. Set app.jira.auth-token / JIRA_TOKEN.");
            return;
        }

        try {
            String transitionId = resolveTransitionId(key, status);
            if (transitionId == null) {
                log.warn("No matching transition found for issue {} and status '{}'", key, status);
                return;
            }
            String url = baseUrl + "/rest/api/3/issue/" + encode(key) + "/transitions";
            String body = mapper.writeValueAsString(Map.of("transition", Map.of("id", transitionId)));
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + authToken)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                log.info("Updated Jira issue {} to status '{}' (transition {})", key, status, transitionId);
            } else {
                log.warn("Failed to transition issue {} to '{}'. Status={}, body={}", key, status, resp.statusCode(), resp.body());
            }
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Error updating Jira status for {}: {}", key, e.getMessage(), e);
        }
    }

    private String resolveTransitionId(String key, String desiredStatusName) throws IOException, InterruptedException {
        String url = baseUrl + "/rest/api/3/issue/" + encode(key) + "/transitions?expand=transitions.fields";
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + authToken)
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            log.warn("Failed to fetch transitions for {}. Status={}, body={}", key, resp.statusCode(), resp.body());
            return null;
        }
        var node = mapper.readTree(resp.body());
        if (!node.has("transitions")) return null;
        for (var t : node.get("transitions")) {
            String name = t.has("name") ? t.get("name").asText() : null;
            String id = t.has("id") ? t.get("id").asText() : null;
            if (name != null && id != null && name.equalsIgnoreCase(desiredStatusName)) {
                return id;
            }
            // Some Jira setups expose to.statusCategory or to.name as target status
            if (t.has("to") && t.get("to").has("name")) {
                String toName = t.get("to").get("name").asText();
                if (toName != null && toName.equalsIgnoreCase(desiredStatusName)) {
                    return id;
                }
            }
        }
        return null;
    }

    private static String encode(String s) {
        return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
