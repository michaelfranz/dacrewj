package org.dacrewj.agent.agents;

import static java.time.LocalDateTime.now;
import static java.time.format.DateTimeFormatter.ofPattern;
import com.embabel.agent.tools.file.FileTools;
import com.embabel.common.ai.model.LlmOptions;
import com.embabel.agent.prompt.persona.RoleGoalBackstory;
import java.io.File;
import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * All properties can be overridden in application.yml
 */
@ConfigurationProperties("dacrew.agents.requirement-reviewer")
public record RequirementReviewerConfig(
		LlmOptions llm,
		int maxConcurrency,
		RoleGoalBackstory reviewer,
		String outputDirectory
) {

	public Path saveContent(RequirementReview review) {
		var dir = outputDirectory != null ? outputDirectory : System.getProperty("user.dir");
		var timestamp = now().format(ofPattern("yyyyMMdd'T'HHmmss"));
		var fileName = timestamp + "_" + review.source().toLowerCase() + "_" + review.key().toLowerCase() + ".md";
		return FileTools.readWrite(dir).createFile("reviews" + File.separator + fileName, review.text(), true);
	}
}
