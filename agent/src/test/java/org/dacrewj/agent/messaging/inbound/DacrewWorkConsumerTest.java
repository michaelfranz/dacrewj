package org.dacrewj.agent.messaging.inbound;

import org.dacrewj.agent.jira.JiraWorkService;
import org.dacrewj.contract.DacrewWork;
import org.dacrewj.contract.JiraModels;
import org.dacrewj.contract.Source;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DacrewWorkConsumerTest {

    @Mock
    private JiraWorkService jiraWorkService;

    @InjectMocks
    private DacrewWorkConsumer consumer;

    @Test
    void whenPayloadIsJiraWebhook_thenJiraWorkServicePerformWorkIsCalled() {
        // Arrange: build a minimal Jira webhook payload and DacrewWork
        var issueFields = new JiraModels.JiraIssueFields(
                "Summary", null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null
        );
        var issue = new JiraModels.JiraIssue("1", null, "ABC-123", issueFields);
        var webhook = new JiraModels.JiraWebhook(
                System.currentTimeMillis(),
                "jira:issue_updated",
                "issue_updated",
                issue,
                null,
                null,
                null
        );
        var work = new DacrewWork("work-1", Source.JIRA, webhook, Instant.now());

        // Act
        consumer.handleWork(work);

        // Assert
        ArgumentCaptor<JiraModels.JiraWebhook> captor = ArgumentCaptor.forClass(JiraModels.JiraWebhook.class);
        verify(jiraWorkService, times(1)).performWork(captor.capture());
        assertThat(captor.getValue()).isSameAs(webhook);
        verifyNoMoreInteractions(jiraWorkService);
    }

    @Test
    void whenPayloadIsNotJiraWebhook_thenJiraWorkServiceIsNotCalled() {
        // Arrange: create a DacrewWork with null payload (unsupported)
        var work = new DacrewWork("work-2", Source.GITHUB, null, Instant.now());

        // Act
        consumer.handleWork(work);

        // Assert
        verifyNoInteractions(jiraWorkService);
    }
}
