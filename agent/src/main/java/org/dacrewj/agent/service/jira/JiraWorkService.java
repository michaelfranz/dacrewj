package org.dacrewj.agent.service.jira;

import com.embabel.agent.api.common.Ai;
import com.embabel.agent.api.common.autonomy.AgentInvocation;
import com.embabel.agent.core.AgentPlatform;
import org.dacrewj.contract.JiraModels;
import org.dacrewj.contract.Source;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class JiraWorkService {

	private static final Logger log = LoggerFactory.getLogger(JiraWorkService.class);

	private final AgentPlatform agentPlatform;
	private final Ai ai;
	private final JiraCommentService jiraCommentService;
	private final JiraStatusService jiraStatusService;

	public JiraWorkService(AgentPlatform agentPlatform, Ai ai, JiraCommentService jiraCommentService, JiraStatusService jiraStatusService) {
		this.agentPlatform = agentPlatform;
		this.ai = ai;
		this.jiraCommentService = jiraCommentService;
		this.jiraStatusService = jiraStatusService;
	}

	public void performWork(JiraModels.JiraWebhook webhook) {
		var issue = webhook.jiraIssue();
		if (issue == null) {
			log.warn("Jira webhook with timestamp {} contains no issue", webhook.timestamp());
			return;
		}
		String key = issue.key();
		if (issue.fields() == null) {
			log.warn("Jira issue {} contains no fields", key);
			return;
		}
		String summary = issue.fields().summary();
		log.info("Handling Jira issue {} ({})", key, summary);
		JiraModels.JiraIssueFields fields = webhook.jiraIssue().fields();
		var type = fields.issuetype();
		switch (type.description()) {
			case JiraConstants.DRAFT_REQUIREMENT: handleDraftRequirement(issue);
			// Add additional cases here...
			default: log.warn("Jira issue {} with type {} has no agent assigned to it", key, type.description());
		}
	}

	private void handleDraftRequirement(JiraModels.JiraIssue issue) {
		var reviewInvocation = AgentInvocation.create(agentPlatform, RequirementReviewer.RequirementReview.class);
		var requirement = new RequirementReviewer.DraftRequirement(
				Source.JIRA.name(),
				issue.key(),
				issue.fields().summary(),
				issue.fields().description()
		);
		RequirementReviewer.RequirementReview review = reviewInvocation.invoke(requirement);
		jiraCommentService.addComment(issue.key(), review.toAdf());

		jiraStatusService.updateStatus(issue.key(), review.approved() ? JiraConstants.APPROVED : JiraConstants.REJECTED);
	}

}
