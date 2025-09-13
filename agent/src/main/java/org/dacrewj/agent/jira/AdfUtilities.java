package org.dacrewj.agent.jira;

import static java.util.Map.of;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.dacrewj.agent.agents.RequirementReview;
import org.dacrewj.contract.AdfDocument;
import org.dacrewj.contract.AdfDocument.Node;
import org.jetbrains.annotations.NotNull;

final class AdfUtilities {

	private static @NotNull Node getTextNode(String title) {
		return new Node("text", null, null, title, null);
	}

	// Helper to create a heading
	private static final BiFunction<String, Integer, Node> h = (txt, level) ->
			new Node("heading", of("level", level), List.of(getTextNode(txt)), null, null);

	// Helper to create a heading level 1
	private static final Function<String, Node> h1 = txt -> h.apply(txt, 1);

	// Helper to create a heading level 2
	private static final Function<String, Node> h2 = txt -> h.apply(txt, 2);

	// Helper to create a paragraph from plain text
	private static final Function<String, Node> paragraph = (txt) ->
			new Node("paragraph", null, List.of(getTextNode(txt)), null, null);

	// Helper to create a bullet list from lines
	private static final Function<List<String>, Node> bulletList = (lines) -> {
		var items = new ArrayList<Node>();
		for (String line : lines) {
			var textNode = getTextNode(line);
			var para = new Node("paragraph", null, List.of(textNode), null, null);
			var listItem = new Node("listItem", null, List.of(para), null, null);
			items.add(listItem);
		}
		return new Node("bulletList", null, items, null, null);
	};

	static AdfDocument toAdf(String source, String key, RequirementReview review) {
		// Build a Jira ADF document with three sections:
		// 1) "Constructive Feedback"
		// 2) "Suggested Improvements"
		// 3) "Conclusion"
		var content = new ArrayList<Node>();

		addTitle(source, key, content);
		addFeedback(review, content);
		addSuggestions(review, content);
		addConclusion(review, content);

		return new AdfDocument("doc", 1, content);
	}

	private static void addTitle(String source, String key, ArrayList<Node> content) {
		content.add(h1.apply("Requirement Review for %s issue %s".formatted(source, key)));
	}

	private static void addFeedback(RequirementReview review, ArrayList<Node> content) {
		content.add(h2.apply("Constructive Feedback"));
		var critique = review.critique();
		if (critique != null && !critique.isEmpty()) {
			content.add(bulletList.apply(critique));
		} else {
			content.add(paragraph.apply("No criticisms"));
		}
	}

	private static void addSuggestions(RequirementReview review, ArrayList<Node> content) {
		content.add(h2.apply("Suggested Improvements"));
		var suggestions = review.suggestions();
		if (suggestions != null && !suggestions.isEmpty()) {
			content.add(bulletList.apply(suggestions));
		} else {
			content.add(paragraph.apply("No improvement suggestions"));
		}
	}

	private static void addConclusion(RequirementReview review, ArrayList<Node> content) {
		content.add(h2.apply("Conclusion"));
		content.add(paragraph.apply(review.approved() ? "Draft requirement approved" : "Draft requirement rejected"));
	}
}