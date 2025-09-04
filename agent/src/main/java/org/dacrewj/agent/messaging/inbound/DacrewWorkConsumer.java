package org.dacrewj.agent.messaging.inbound;

import org.dacrewj.agent.service.jira.JiraWorkService;
import org.dacrewj.contract.DacrewWork;
import org.dacrewj.contract.GithubModels;
import org.dacrewj.contract.JiraModels;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class DacrewWorkConsumer {

    private static final Logger log = LoggerFactory.getLogger(DacrewWorkConsumer.class);

	private final JiraWorkService jiraWorkService;

	public DacrewWorkConsumer(JiraWorkService jiraWorkService) {
		this.jiraWorkService = jiraWorkService;
	}

	@RabbitListener(queues = "${app.rabbit.queue-name:dacrew.work}", concurrency = "2-10")
    public void handleWork(DacrewWork work) {
        log.info("Received work: {} from {} at {}", work.id(), work.source(), work.createdAt());

        var payload = work.payload();
        if (payload instanceof JiraModels.JiraWebhook jira) {
			jiraWorkService.performWork(jira);
        } else if (payload instanceof GithubModels.GithubIssue github) {
			log.warn("Github handling is not yet supported (desc: {}) from work {}", github.description(), work.id());
        } else {
            log.warn("Unsupported payload type: {} for work {}", payload != null ? payload.getClass().getName() : "null", work.id());
        }
    }

}
