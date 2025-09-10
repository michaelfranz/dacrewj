package org.dacrewj.agent.agents;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.common.OperationContext;
import com.embabel.agent.config.models.OpenAiModels;
import com.embabel.agent.domain.io.UserInput;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;
import org.dacrewj.contract.AdfDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Agent(name = "Requirement Reviewer",
		description = "Reviews a draft chatrequirement and provides a critique and improvement suggestions",
		version = "1.0.0",
		beanName = "requirementReviewerAgent"
)
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
	public DraftRequirement getDraftRequirement(UserInput userInput, OperationContext context) {
		DraftRequirement draftRequirement = context.ai()
				.withDefaultLlm()
				.createObjectIfPossible("""
						Create a draft requirement from this user input extracting their details: %s 
						""".formatted(userInput.getContent()), DraftRequirement.class);

		log.info("Draft requirement: {}", draftRequirement);

		return draftRequirement;
	}

	@Action
	public Critique getCritique(DraftRequirement requirement, OperationContext context) {
		Critique critique = context.ai()
				.withDefaultLlm()
				.createObjectIfPossible("""
						You are a business software analyst in our software company, and your role is to provide
						provide a critique of the given draft requirement, ensuring that each of the following areas
						are considered:
						
						Clarity & Intent
							•	Single, clear objective: Is the core intent obvious in one sentence?
							•	Problem–solution framing: Does it state the user/business problem and the intended change?
							•	Unambiguous wording: Avoids vague terms (“optimize,” “improve,” “fast”) without anchors.
							•	Scope boundaries: What’s explicitly in vs. out?
						
						Fitness for Development (“Definition of Ready”)
							•	Implementable now: No blocking unknowns that prevent starting work.
							•	Estimable: Enough detail to roughly size with reasonable confidence.
							•	Testable: Outcome can be verified by tests or inspection.
							•	Traceable: Ties to a goal/OKR, epic, or stakeholder request.
						
						Language & Consistency
							•	Consistent terminology: Uses domain terms consistently with glossary/backlog.
							•	No contradictions: Summary and description don’t disagree or conflict with other items.
							•	No extraneous solutioning: Leaves room for engineering decisions where appropriate (not over-specifying implementation unless necessary).
						
						Prioritization & Value
							•	Business value stated: Why this matters now; expected impact.
							•	Priority & sequencing: Makes sense relative to related work.
						
						Create a critique from this requirement using the summary: %s
						and the description: %s
						
						If the requirement is flawless, then create a critique with an empty list of criticisms.
						""".formatted(requirement.summary(), requirement.description), Critique.class);

		log.info("Requirement critique: {}", critique);

		return critique;
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
				.createObjectIfPossible("""
								You are a business software analyst in our software company, and your role is to provide
								improvement suggestions, given a requirement and a critique of said requirement.
								
								You will create an ImprovementSuggestions based on the requirements summary, "%s", description, "%s",
								and the criticisms made about the requirement:
								%s
								""".formatted(summary, description, critique.criticisms.stream().map(c -> "- " + c + "\n")),
						ImprovementSuggestions.class);
	}

	@AchievesGoal(description = "Provide a critique and suggestions for a requirement")
	@Action
	public RequirementReview getRequirementReview(Critique critique, ImprovementSuggestions suggestions, OperationContext context) {
		log.info("RequirementReviewer.reviewRequirement");
		return new RequirementReview(critique, suggestions, critique.criticisms.isEmpty());
	}

	/* Seems to make the context window too big:
						Users & Use Cases
							•	Primary user/persona: Who benefits or interacts?
							•	User journey touchpoint: Where in the flow does this occur?
							•	Key scenarios covered: Typical, important, and one edge case at least.
							•	User impact: What changes for the user (UX, behavior, expectations)?

						Outcomes & Acceptance
							•	Observable outcome: What should be different after delivery?
							•	Acceptance criteria or examples: Concrete examples (Gherkin-style or bullet points).
							•	Success metrics: What would count as “good enough” (e.g., error rate ↓, load time ≤ X)?

						Constraints & Boundaries
							•	Business rules: Any rules that must be enforced?
							•	Regulatory/compliance: GDPR/PII/PHI/export constraints if relevant.
							•	Time/market deadlines: Hard dates, sequencing with other work.

						Dependencies & Interactions
							•	Upstream/downstream dependencies: Services, teams, approvals, data feeds.
							•	Cross-team impact: Anyone else who must review or adapt?
							•	Migration/compatibility: Backward compatibility, versioning, or data migration needs.

						Data & Interfaces
							•	Data touched: Entities/fields created, read, updated, deleted.
							•	API/contract impact: New/changed endpoints, events, or schemas.
							•	State & idempotency: How repeated actions behave; eventual consistency expectations.

						Error Handling & Resilience
							•	Failure modes described: What can go wrong and how it should behave.
							•	Recovery paths: Retries, fallbacks, compensating actions.
							•	Observability: Logs, metrics, alerts to confirm correct/incorrect behavior.

						Performance & Non-Functionals
							•	Performance targets: Latency, throughput, batch windows.
							•	Availability/SLOs: Any uptime or reliability expectations.
							•	Security & privacy: AuthZ/AuthN, data minimization, encryption at rest/in transit.
							•	Accessibility & i18n: A11y considerations; locale, time zones, languages.

						UX & Content, if applicable (not applicable of the requirement describes a non-UX item)
							•	User-facing copy hints: Labels, messages, empty states, error text (at least tone + intent).
							•	Discoverability: How users find/trigger the feature; avoids hidden behaviors.
							•	Consistency: Aligns with existing patterns/components.

						Risks & Alternatives
							•	Key risks identified: Technical, product, or operational risks called out.
							•	Reasonable alternatives considered: Why the proposed approach/solution direction?
							•	Rollout strategy: Feature flags, staged rollout, and rollback plan.

						Delivery & Validation
							•	Test strategy outline: Unit/integration/e2e or contract tests implied by acceptance.
							•	Analytics/telemetry: What events or measures to instrument for learning.
							•	Post-release checks: How we’ll verify in production (dashboards, runbooks).


	 */
}
