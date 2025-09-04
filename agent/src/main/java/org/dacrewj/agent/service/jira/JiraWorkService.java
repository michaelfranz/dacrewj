package org.dacrewj.agent.service.jira;

import org.dacrewj.contract.JiraModels;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class JiraWorkService {

	private static final Logger log = LoggerFactory.getLogger(JiraWorkService.class);

	private final RequirementReviewer requirementReviewer;

	public JiraWorkService(RequirementReviewer requirementReviewer) {
		this.requirementReviewer = requirementReviewer;
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
			case JiraConstants.DRAFT_REQUIREMENT: requirementReviewer.performWork(issue);
			// Add additional cases here...
			default: log.warn("Jira issue {} with type {} has no agent assigned to it", key, type.description());
		}
	}

}
