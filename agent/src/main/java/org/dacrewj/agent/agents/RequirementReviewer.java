package org.dacrewj.agent.agents;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.annotation.Export;
import com.embabel.agent.api.annotation.WaitFor;
import com.embabel.agent.api.common.OperationContext;
import com.embabel.agent.domain.io.UserInput;
import com.embabel.common.ai.prompt.PromptContributor;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



record Criticism(@JsonPropertyDescription("A criticismText") String criticismText) implements PromptContributor {
	@Override
	public @NotNull String contribution() {
		return criticismText;

	}
}

record Critique(
		@JsonPropertyDescription("A critique") List<Criticism> criticisms) implements PromptContributor {
	@Override
	public @NotNull String contribution() {
		return IntStream.range(0, criticisms.size())
				.mapToObj(i -> (i + 1) + "1. " + criticisms.get(i).contribution())
				.collect(Collectors.joining("\n"));

	}
}

record ImprovementSuggestions(
		@JsonPropertyDescription("Suggested improvements") List<String> suggestions) implements PromptContributor {
	@Override
	public @NotNull String contribution() {
		return String.join("\n", suggestions);
	}
}

@Agent(name = "Requirement Reviewer",
		description = """
				Review a draft requirement, provide a critique, improvement suggestions, and a statement on
				whether the draft is approved for development"""
)
public record RequirementReviewer(RequirementReviewerConfig config) {

	private static final Logger logger = LoggerFactory.getLogger(RequirementReviewer.class);

	public RequirementReviewer {
		logger.info("Initialized with configuration {}", config);
	}

	@Action(cost = 100.0)
	DraftRequirement askForDraftRequirement(OperationContext context) {
		return WaitFor.formSubmission("""
				Please provide a source (e.g. Jira), a key (e.g. BTW123), a summary and
				a description of the requirement""", DraftRequirement.class);
	}

	@Action
	public DraftRequirement getDraftRequirement(UserInput userInput, OperationContext context) {
		DraftRequirement draftRequirement = context.ai()
				.withDefaultLlm()
				.createObjectIfPossible("""
								Create a draft requirement from this user input extracting their details: %s
						""".formatted(userInput.getContent()), DraftRequirement.class);

		logger.info("Draft requirement: {}", draftRequirement);

		return draftRequirement;
	}

	@Action
	Critique criticise(
			DraftRequirement requirement,
			OperationContext context) {
		Critique critique = context.ai()
				.withLlm(config.llm())
				.withPromptElements(config.reviewer(), requirement)
				.createObject("""
						Create a critique based on the given draft requirement's summary and description:
						Summary: %s
						Description: %s
						
						The critique comprises a list of criticisms. A criticismText must name the best-practice
						principle, which has been ignored or contradicted by the requirement, and then explain
						specifics of how the requirement ignores or contradicts the principle.
						Do not include suggestion improvements in the criticismText of the criticismText.
						Order the list of criticisms in terms of seriousness, starting with the most serious item
						at position 0 in the list.
						If the requirement is flawless, then create a critique with an empty list of criticisms.
						""".formatted(requirement.summary(), requirement.description()), Critique.class);

		logger.info("Requirement critique: {}", critique);

		return critique;
	}

	@Action
	ImprovementSuggestions suggestImprovements(DraftRequirement requirement, Critique critique, OperationContext context) {
		logger.info("RequirementReviewer.getImprovementSuggestions invoked for issue {}", requirement.key());
		if (critique.criticisms().isEmpty()) {
			return new ImprovementSuggestions(List.of());
		}

		var suggestions = context.parallelMap(
				critique.criticisms(),
				config.maxConcurrency(),
				criticism -> writeSuggestion(requirement, criticism, context)
		);

		return new ImprovementSuggestions(suggestions);
	}

	@Action
	String writeSuggestion(DraftRequirement requirement, Criticism criticism, OperationContext context) {
		logger.info("Writing improvement suggestion to address criticism text: {}", criticism);
		return context.ai()
				.withLlm(config.llm())
				.withPromptElements(requirement, criticism)
				.createObject("""
						Write a succinct suggestion for how to address the criticism text.
						Criticism: %s
						""".formatted(criticism), String.class);
	}

	@Action
	RequirementReview writeReview(DraftRequirement draftRequirement, Critique critique, ImprovementSuggestions suggestions, OperationContext context) {
		logger.info("RequirementReviewer.writeReview");
		return new RequirementReview(
				draftRequirement.source(),
				draftRequirement.key(),
				draftRequirement.summary(),
				critique.criticisms().stream().map(Criticism::criticismText).toList(),
				suggestions.suggestions(),
				critique.criticisms().isEmpty());
	}



	@AchievesGoal(
			description = "RequirementReview has been written and published for the draft requirement",
			export = @Export(remote = true)
	)
	@Action
	RequirementReview publishRequirementReview(RequirementReview review) {
		logger.info("RequirementReviewer.reviewRequirement");
		var path = config.saveContent(review);
		logger.info("Book {} written and saved to {}", review.summary(), path);
		return review;
	}
}
