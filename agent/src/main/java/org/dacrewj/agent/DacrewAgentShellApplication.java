package org.dacrewj.agent;

import com.embabel.agent.config.annotation.EnableAgents;
import com.embabel.agent.config.annotation.LocalModels;
import com.embabel.agent.config.annotation.LoggingThemes;
import com.embabel.agent.config.annotation.McpServers;
import org.dacrewj.agent.agents.RequirementReviewerConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.shell.command.annotation.CommandScan;

@SpringBootApplication
@EnableAgents(
		loggingTheme = LoggingThemes.STAR_WARS,
		localModels = { LocalModels.OLLAMA, LocalModels.DOCKER },
		mcpServers = McpServers.DOCKER
)
@EnableConfigurationProperties(RequirementReviewerConfig.class)
@CommandScan
@Profile("cli")
public class DacrewAgentShellApplication {

	public static void main(String[] args) {
		SpringApplication.run(DacrewAgentShellApplication.class, args);
	}

}
