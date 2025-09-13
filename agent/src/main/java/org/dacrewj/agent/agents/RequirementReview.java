package org.dacrewj.agent.agents;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;

public record RequirementReview(
		@JsonPropertyDescription("Source of the requirement") String source,
		@JsonPropertyDescription("Key of the requirement") String key,
		@JsonPropertyDescription("Summary of the requirement") String summary,
		@JsonPropertyDescription("Critique of the requirement") List<String> critique,
		@JsonPropertyDescription("Improvement suggestions") List<String> suggestions,
		@JsonPropertyDescription("Whether requirement approved (true if approved, otherwise false)") boolean approved) {
	public String text() {
		return """
					# %s
					## Criticisms
					%s
					## Suggestions
					%s
					## Conclusion
					%s
					""".formatted(
				getTitle(),
				numberedList(critique),
				numberedList(suggestions),
				approved ? "Approved" : "Rejected"
		);
	}

	private @NotNull String getTitle() {
		return source + "/" + key + ": " + summary;
	}

	private @NotNull String numberedList(List<String> items) {
		if (items.isEmpty()) {
			return "N/A";
		}
		return IntStream.range(0, items.size())
				.mapToObj(i -> (i+1) + ". " + items.get(i))
				.collect(Collectors.joining("\n"));
	}
}
