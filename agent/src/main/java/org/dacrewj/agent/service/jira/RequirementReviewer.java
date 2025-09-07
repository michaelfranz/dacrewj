package org.dacrewj.agent.service.jira;

import java.util.List;
import org.dacrewj.contract.AdfDocument;
import org.dacrewj.contract.JiraModels;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component("requirement-reviewer")
public class RequirementReviewer {

	private static final Logger log = LoggerFactory.getLogger(RequirementReviewer.class);

	private final JiraCommentTool jiraCommentTool;
	private final com.embabel.agent.core.AgentRuntime agentRuntime;

	public RequirementReviewer(JiraCommentTool jiraCommentTool,
							  com.embabel.agent.core.AgentRuntime agentRuntime) {
		this.jiraCommentTool = jiraCommentTool;
		this.agentRuntime = agentRuntime;
	}

	public void performWork(JiraModels.JiraIssue issue) {
		assert issue.fields() != null;
		assert issue.fields().issuetype().description().equals(JiraConstants.DRAFT_REQUIREMENT);

		log.info("RequirementReviewer invoked for issue {}", issue.key());

		// Build agent input variables from Jira issue
		var summary = issue.fields().summary();
		var description = issue.fields().description();
		var jiraIssueInput = new java.util.LinkedHashMap<String,Object>();
		jiraIssueInput.put("key", issue.key());
		jiraIssueInput.put("summary", summary != null ? summary : "");
		jiraIssueInput.put("description", description != null ? description : "");

		var inputs = new java.util.HashMap<String, Object>();
		inputs.put("jira-issue", jiraIssueInput);
		inputs.put("domain-documentation", ""); // placeholder for future enrichment
		inputs.put("code-base", ""); // placeholder for future enrichment

		// Execute Embabel agent configured under embabel.agents.requirement-reviewer
		com.embabel.agent.core.AgentResult agentResult = null;
		try {
			agentResult = agentRuntime.execute("requirement-reviewer", inputs);
		} catch (Exception e) {
			log.warn("Embabel agent execution failed, fallback to simple acknowledgement: {}", e.toString());
		}

		// If the agent already invoked the jira-comment tool, we may be done. Otherwise, fallback.
		if (agentResult != null && agentResult.getToolInvocations() != null && !agentResult.getToolInvocations().isEmpty()) {
			log.info("Embabel agent produced {} tool invocations for issue {}", agentResult.getToolInvocations().size(), issue.key());
			return; // assume tool posted the comment
		}

		// Fallback: post a minimal ADF comment based on agent text output if present, else acknowledgement
		String text = (agentResult != null && agentResult.getOutput() != null && !agentResult.getOutput().isBlank())
				? agentResult.getOutput()
				: buildMessage(issue);

		AdfDocument.Node textNode = new AdfDocument.Node("text", null, null, text, null);
		AdfDocument.Node paragraph = new AdfDocument.Node("paragraph", null, java.util.List.of(textNode), null, null);
		AdfDocument adf = new AdfDocument("doc", 1, java.util.List.of(paragraph));

		var result = jiraCommentTool.addComment(issue.key(), adf);
		if (result.success()) {
			log.info("Jira comment created for {} with status {} at {}", issue.key(), result.status(), result.url());
		} else {
			log.warn("Failed to create Jira comment for {}: {}", issue.key(), result.error());
		}
	}

	private String buildMessage(JiraModels.JiraIssue issue) {
		var f = issue.fields();
		String summary = f.summary();
		String reporter = f.reporter() != null ? f.reporter().displayName() : "unknown";
		return "Automated review: Received Draft Requirement '" + (summary != null ? summary : issue.key()) + "'. Reporter: " + reporter + ".";
	}
}
