package org.dacrewj.agent.agents;

import com.embabel.common.ai.prompt.PromptContributor;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import org.jetbrains.annotations.NotNull;

public record DraftRequirement(
		@JsonPropertyDescription("Source of the requirement") String source,
		@JsonPropertyDescription("Key of the requirement") String key,
		@JsonPropertyDescription("Summary of the requirement") String summary,
		@JsonPropertyDescription("Description of the requirement") String description) implements PromptContributor {

	@Override
	public @NotNull String contribution() {
		return """
				Draft Requirement:
				Source: %s
				Key: %s
				Summary: %s
				Description: %s
				""".formatted(source, key, summary, description);
	}
}
