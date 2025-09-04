package org.dacrewj.jira_ingester;

import org.dacrewj.messaging.RabbitConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(RabbitConfig.class)
public class JiraIngesterApplication {

	public static void main(String[] args) {
		SpringApplication.run(JiraIngesterApplication.class, args);
	}

}
