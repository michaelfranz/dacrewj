package org.dacrewj.agent.service.jira;

import org.dacrewj.contract.JiraModels;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class RequirementReviewer {

	private static final Logger log = LoggerFactory.getLogger(RequirementReviewer.class);

	public void performWork(JiraModels.JiraIssue issue) {
		assert issue.fields() != null;
		assert issue.fields().issuetype().description().equals(JiraConstants.DRAFT_REQUIREMENT);

		log.debug("Invoked for issue {}", issue.key());


	}
}
