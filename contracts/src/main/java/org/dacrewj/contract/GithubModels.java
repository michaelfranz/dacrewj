package org.dacrewj.contract;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

public final class GithubModels {

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record GithubIssue(
			String id,
			String self,
			String key,
			String description
	) implements Payload {}
}
