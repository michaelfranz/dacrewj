package org.dacrewj.contract;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * Java records mirroring the provided Pydantic Jira models.
 */
public final class JiraModels {

	@JsonIgnoreProperties(ignoreUnknown = true)
	@com.fasterxml.jackson.annotation.JsonTypeInfo(use = com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NONE)
	public record JiraWebhook(
			long timestamp,
			String webhookEvent,
			String issue_event_type_name,
			@JsonProperty("issue") JiraIssue jiraIssue,
			JiraUser user,
			JiraChangelog changelog,
			Map<String, Object> comment
	) implements Payload {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record JiraAvatarUrls(
			@JsonProperty("48x48") String url_48x48,
			@JsonProperty("24x24") String url_24x24,
			@JsonProperty("16x16") String url_16x16,
			@JsonProperty("32x32") String url_32x32
	) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record JiraUser(
			String self,
			String accountId,
			JiraAvatarUrls avatarUrls,
			String displayName,
			Boolean active,
			String timeZone,
			String accountType
	) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record JiraStatusCategory(
			String self,
			Integer id,
			String key,
			String colorName,
			String name
	) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record JiraStatus(
			String self,
			String description,
			String iconUrl,
			String name,
			String id,
			JiraStatusCategory statusCategory
	) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record JiraPriority(
			String self,
			String iconUrl,
			String name,
			String id
	) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record JiraIssueType(
			String self,
			String id,
			String description,
			String iconUrl,
			String name,
			Boolean subtask,
			Integer avatarId,
			String entityId,
			Integer hierarchyLevel
	) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record JiraProject(
			String self,
			String id,
			String key,
			String name,
			String projectTypeKey,
			Boolean simplified,
			JiraAvatarUrls avatarUrls
	) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record JiraProgress(
			int progress,
			int total
	) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record JiraIssueFields(
			String summary,
			String description,
			JiraStatus status,
			JiraPriority priority,
			JiraProject project,
			JiraIssueType issuetype,
			JiraUser assignee,
			JiraUser reporter,
			JiraUser creator,
			String created,
			String updated,
			Map<String, Object> resolution,
			List<String> labels,
			List<Map<String, Object>> components,
			List<Map<String, Object>> fixVersions,
			List<Map<String, Object>> versions,
			String duedate,
			JiraProgress progress,
			JiraProgress aggregateprogress
	) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record JiraIssue(
			String id,
			String self,
			String key,
			JiraIssueFields fields
	) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record JiraChangelogItem(
			String field,
			String fieldtype,
			String fieldId,
			@JsonProperty("from") Object from,
			String fromString,
			Object to,
			@JsonProperty("toString") String toStringValue
	) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record JiraChangelog(
			String id,
			List<JiraChangelogItem> items
	) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record JiraCommentBody(
			String type,
			int version,
			List<Map<String, Object>> content
	) {
		public JiraCommentBody {
			if (type == null) type = "doc";
			if (version == 0) version = 1;
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record JiraComment(
			Object body,
			Map<String, String> visibility
	) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record JiraTransition(
			Map<String, String> transition,
			Map<String, Object> fields,
			Map<String, Object> update
	) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record JiraFieldUpdate(
			Map<String, Object> fields,
			Map<String, Object> update
	) {
	}
}
