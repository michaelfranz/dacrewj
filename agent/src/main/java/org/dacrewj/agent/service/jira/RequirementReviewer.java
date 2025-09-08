package org.dacrewj.agent.service.jira;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.common.OperationContext;
import com.embabel.agent.config.models.OpenAiModels;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;
import org.dacrewj.contract.AdfDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Agent(description = "Requirement Reviewer")
public record RequirementReviewer() {

	private static final Logger log = LoggerFactory.getLogger(RequirementReviewer.class);

	public record DraftRequirement(
			@JsonPropertyDescription("Source of the requirement") String source,
			@JsonPropertyDescription("Key of the requirement") String key,
			@JsonPropertyDescription("Summary of the requirement") String summary,
			@JsonPropertyDescription("Description of the requirement") String description) {
	}

	public record Critique(
			@JsonPropertyDescription("A critique") List<String> criticisms) {
	}

	public record ImprovementSuggestions(
			@JsonPropertyDescription("Suggested improvements") List<String> suggestions) {
	}

	public record RequirementReview(
			@JsonPropertyDescription("Critique of the requirement") Critique critique,
			@JsonPropertyDescription("Improvement suggestions") ImprovementSuggestions suggestions,
			@JsonPropertyDescription("Whether requirement approved (true if approved, otherwise false)") boolean approved) {
			public AdfDocument toAdf() {
				// Build a Jira ADF document with three sections:
				// 1) "Constructive Feedback"
				// 2) "Suggested Improvements"
				// 3) "Conclusion"
				var content = new java.util.ArrayList<AdfDocument.Node>();

				// Helper to create a heading with given text and level 2
				java.util.function.Function<String, AdfDocument.Node> h2 = (title) -> {
					var textNode = new AdfDocument.Node("text", null, null, title, null);
					var heading = new AdfDocument.Node("heading", java.util.Map.of("level", 2), java.util.List.of(textNode), null, null);
					return heading;
				};

				// Helper to create a paragraph from plain text
				java.util.function.Function<String, AdfDocument.Node> paragraph = (txt) -> {
					var textNode = new AdfDocument.Node("text", null, null, txt, null);
					return new AdfDocument.Node("paragraph", null, java.util.List.of(textNode), null, null);
				};

				// Helper to create a bullet list from lines
				java.util.function.Function<java.util.List<String>, AdfDocument.Node> bulletList = (lines) -> {
					var items = new java.util.ArrayList<AdfDocument.Node>();
					for (String line : lines) {
						var textNode = new AdfDocument.Node("text", null, null, line, null);
						var para = new AdfDocument.Node("paragraph", null, java.util.List.of(textNode), null, null);
						var listItem = new AdfDocument.Node("listItem", null, java.util.List.of(para), null, null);
						items.add(listItem);
					}
					return new AdfDocument.Node("bulletList", null, items, null, null);
				};

				// Section 1: Constructive Feedback
				content.add(h2.apply("Constructive Feedback"));
				if (critique != null && critique.criticisms() != null && !critique.criticisms().isEmpty()) {
					content.add(bulletList.apply(critique.criticisms()));
				} else {
					content.add(paragraph.apply("No criticisms"));
				}

				// Section 2: Suggested Improvements
				content.add(h2.apply("Suggested Improvements"));
				if (suggestions != null && suggestions.suggestions() != null && !suggestions.suggestions().isEmpty()) {
					content.add(bulletList.apply(suggestions.suggestions()));
				} else {
					content.add(paragraph.apply("No improvement suggestions"));
				}

				// Section 3: Conclusion
				content.add(h2.apply("Conclusion"));
				content.add(paragraph.apply(approved ? "Draft requirement approved" : "Draft requirement rejected"));

				return new AdfDocument("doc", 1, content);
			}
		}

	@Action
	public Critique getRequirementCritique(DraftRequirement requirement, OperationContext context) {
		log.info("RequirementReviewer.getRequirementCritique invoked for issue {}", requirement.key());
		var summary = requirement.summary();
		var description = requirement.description();

		return context.ai()
				.withLlm(OpenAiModels.GPT_41_MINI)
				.createObject("""
								You are a business software analyst in our software company, and your role is to give
								critique draft requirements, which have been written in plain text.
								
								You will create a Critique of the specification given its summary, "%s", and description, "%s".
								
								If the draft requirement is well formulated and requires no improvement, then the 
								Critique will be empty.
								""".formatted(summary, description),
						Critique.class);
	}

	@Action
	public ImprovementSuggestions getImprovementSuggestions(DraftRequirement requirement, Critique critique, OperationContext context) {
		log.info("RequirementReviewer.getImprovementSuggestions invoked for issue {}", requirement.key());
		var summary = requirement.summary();
		var description = requirement.description();

		if (critique.criticisms.isEmpty()) {
			return new ImprovementSuggestions(List.of());
		}

		return context.ai()
				.withLlm(OpenAiModels.GPT_41_MINI)
				.createObject("""
								You are a business software analyst in our software company, and your role is to provide
								improvement suggestions, given a requirement and a critique of said requirement.
								
								You will create an ImprovementSuggestions based on the requirements summary, "%s", description, "%s",
								and the criticisms made about the requirement:
								%s
								""".formatted(summary, description, critique.criticisms.stream().map(c -> "- " + c + "\n")),
						ImprovementSuggestions.class);
	}

	@AchievesGoal(description = "Review the provided draft requirement")
	@Action
	public RequirementReview getRequirementReview(Critique critique, ImprovementSuggestions suggestions, OperationContext context) {
		log.info("RequirementReviewer.reviewRequirement");
		return new RequirementReview(critique, suggestions, critique.criticisms.isEmpty());
	}
}
