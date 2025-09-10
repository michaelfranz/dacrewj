package org.dacrewj.agent;

import com.embabel.agent.config.annotation.EnableAgents;
import com.embabel.agent.config.annotation.LocalModels;
import com.embabel.agent.config.annotation.LoggingThemes;
import org.dacrewj.messaging.RabbitConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;

@SpringBootApplication
@Import(RabbitConfig.class)
@EnableAgents(
		loggingTheme = LoggingThemes.STAR_WARS,
		localModels = { LocalModels.OLLAMA, LocalModels.DOCKER }
)
@Profile("server")
public class DacrewAgentApplication {

	public static void main(String[] args) {
		SpringApplication.run(DacrewAgentApplication.class, args);
	}

}
