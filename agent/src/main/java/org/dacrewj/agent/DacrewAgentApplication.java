package org.dacrewj.agent;

import com.embabel.agent.config.annotation.EnableAgents;
import com.embabel.agent.config.annotation.LocalModels;
import com.embabel.agent.config.annotation.LoggingThemes;
import com.embabel.agent.config.annotation.McpServers;
import org.dacrewj.agent.agents.RequirementReviewerConfig;
import org.dacrewj.messaging.RabbitConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;

@SpringBootApplication
@Import(RabbitConfig.class)
@EnableAgents(
		loggingTheme = LoggingThemes.STAR_WARS,
		localModels = { LocalModels.OLLAMA, LocalModels.DOCKER },
		mcpServers = McpServers.DOCKER
)
@EnableConfigurationProperties(RequirementReviewerConfig.class)
@Profile("server")
public class DacrewAgentApplication {

	public static void main(String[] args) {
		SpringApplication.run(DacrewAgentApplication.class, args);
	}

}
