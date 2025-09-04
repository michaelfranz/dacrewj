package org.dacrewj.contract;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
		@JsonSubTypes.Type(value = JiraModels.JiraWebhook.class, name = "jira"),
		@JsonSubTypes.Type(value = GithubModels.GithubIssue.class, name = "github")
})public sealed interface Payload permits JiraModels.JiraWebhook, GithubModels.GithubIssue {
}
