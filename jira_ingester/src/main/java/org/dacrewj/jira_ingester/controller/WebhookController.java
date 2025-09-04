package org.dacrewj.jira_ingester.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import org.dacrewj.contract.DacrewWork;
import org.dacrewj.contract.JiraModels;
import org.dacrewj.contract.Source;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WebhookController {

	private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

	private final ObjectMapper objectMapper;
	private final RabbitTemplate rabbitTemplate;
	private final Queue dacrewQueue;

	public WebhookController(ObjectMapper objectMapper, RabbitTemplate rabbitTemplate, Queue dacrewQueue) {
		this.objectMapper = objectMapper;
		this.rabbitTemplate = rabbitTemplate;
		this.dacrewQueue = dacrewQueue;
	}

	@GetMapping(path = "/health", produces = "application/json")
	public ResponseEntity<Map<String, String>> health() {
		return ResponseEntity.ok(Map.of(
				"status", "healthy",
				"service", "jira_ingest"
		));
	}

	@PostMapping(path = "/webhook/jira", consumes = "application/json", produces = "application/json")
	public ResponseEntity<Map<String, String>> jiraWebhook(@RequestBody(required = false) byte[] body,
														   @RequestHeader MultiValueMap<String, String> headers,
														   @RequestParam MultiValueMap<String, String> queryParams,
														   HttpServletRequest request) {
		try {
			if (!queryParams.isEmpty()) {
				log.info("Query parameters received: {}", queryParams);
			}

			// Parse webhook payload
			byte[] raw = body != null ? body : (byte[]) request.getAttribute("cachedRequestBody");
			if (raw == null) {
				// Fallback: read from request if interceptor not applied
				raw = request.getInputStream().readAllBytes();
			}
			var payloadJson = new String(raw, StandardCharsets.UTF_8);
			try {
				JsonNode root = objectMapper.readTree(payloadJson);
				JiraModels.JiraWebhook webhook = objectMapper.readValue(payloadJson, JiraModels.JiraWebhook.class);
				JiraModels.JiraIssue jiraIssue = webhook.jiraIssue();
				if (jiraIssue == null) {
					log.info("Webhook processed but no issue data available");
				} else {
					String issueKey = jiraIssue.key();
					String projectKey = jiraIssue.fields() != null && jiraIssue.fields().project() != null ? jiraIssue.fields().project().key() : "unknown";
					long timestamp = root.path("timestamp").asLong(Instant.now().toEpochMilli());
					String workId = projectKey + "-" + issueKey + "-" + timestamp;

					var work = new DacrewWork(
							workId,
							Source.JIRA,
							webhook,
							Instant.now()
					);

					rabbitTemplate.convertAndSend(dacrewQueue.getName(), work);
					log.info("DacrewWork enqueued for processing: {}", workId);
				}
			} catch (Exception e) {
				log.error("Error processing webhook", e);
				return ResponseEntity.internalServerError().body(Map.of(
						"status", "error",
						"message", "Error processing webhook"
				));
			}

			return ResponseEntity.ok(Map.of(
					"status", "success",
					"message", "Webhook processed successfully"
			));
		} catch (Exception e) {
			log.error("Unexpected error handling webhook", e);
			return ResponseEntity.internalServerError().body(Map.of(
					"status", "error",
					"message", "Internal server error"
			));
		}
	}
}
