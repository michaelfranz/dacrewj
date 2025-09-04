package org.dacrewj.contract;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.*;

class JiraModelsParsingTest {

    @Test
    void parsesSampleWebhookPayloadIntoJiraWebhook() throws Exception {
        // Arrange: load sample JSON from test resources
        String resourcePath = "/resources/jira-webhook-payload.json";
        InputStream is = getClass().getResourceAsStream(resourcePath);
        assertThat(is).as("Test resource not found: %s", resourcePath).isNotNull();

        ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();

        // Act
        JiraModels.JiraWebhook webhook = mapper.readValue(is, JiraModels.JiraWebhook.class);

        // Assert
        assertThat(webhook).as("Deserialized JiraWebhook should not be null").isNotNull();
        assertThat(webhook.timestamp()).as("timestamp should be > 0").isGreaterThan(0L);
        assertThat(webhook.webhookEvent()).as("webhookEvent should be present").isNotNull();

        // If issue is present, validate a couple of nested fields to ensure deep mapping works
        JiraModels.JiraIssue issue = webhook.jiraIssue();
        if (issue != null) {
            assertThat(issue.key()).as("issue.key should be present").isNotNull();
            JiraModels.JiraIssueFields fields = issue.fields();
            if (fields != null && fields.project() != null) {
                assertThat(fields.project().key()).as("project.key should be present").isNotNull();
            }
        }
    }
}
