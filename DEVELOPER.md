Developer guide: Run in your IDE (IntelliJ, Eclipse)

This repository contains two Spring Boot apps:
- jira_ingester (HTTP webhook receiver; default port 8081)
- agent (consumer/worker; default port 8080)

Both apps are standard Spring Boot applications and can be run and debugged directly from your IDE.

Prerequisites
1) Java 21 JDK
2) RabbitMQ running locally
   - Use a dedicated user; do NOT use guest over published ports (guest is localhost-only).
   - Start via Docker (recommended):
     docker run --rm -p 5672:5672 -p 15672:15672 --name rabbit \
       -e RABBITMQ_DEFAULT_USER=appuser -e RABBITMQ_DEFAULT_PASS=apppass \
       rabbitmq:3-management
   - Management UI: http://localhost:15672 (user: appuser, pass: apppass)

3) Optional: .env file for local variables
   - Create a .env at the repo root (or copy from an internal template if you have one).
   - The repo includes a DotenvEnvironmentPostProcessor that loads .env into Spring without overriding real env variables.
   - Never commit secrets. .env is for your machine only.

Environment variables
Both apps use Spring’s standard AMQP properties:
- SPRING_RABBITMQ_HOST=localhost
- SPRING_RABBITMQ_PORT=5672
- SPRING_RABBITMQ_USERNAME=appuser
- SPRING_RABBITMQ_PASSWORD=apppass

Per-service variables:
- jira_ingester: JIRA_WEBHOOK_SECRET (required) and optional APP_WEBHOOK_SIGNATURE_HEADER, APP_WEBHOOK_ALGORITHM
- agent:
  - JIRA_TOKEN (required if you disable dry-run)
  - app.jira.dry-run=true by default in agent/src/main/resources/application.yml. Set to false to actually call Jira.

Running in IntelliJ IDEA
Option A: Quick start
- Right-click the main class and choose Run/Debug:
  - jira_ingester: org.dacrewj.jira_ingester.JiraIngesterApplication (port 8081)
  - agent: org.dacrewj.agent.DacrewAgentApplication (port 8080)
- In each Run Configuration, set Environment variables:
  SPRING_RABBITMQ_HOST=localhost;SPRING_RABBITMQ_PORT=5672;SPRING_RABBITMQ_USERNAME=appuser;SPRING_RABBITMQ_PASSWORD=apppass;JIRA_WEBHOOK_SECRET=change-me
  For agent: add JIRA_TOKEN=your-token (or leave dry-run=true)

Option B: Attach debugger explicitly
- Create a Debug configuration the same way and press Debug instead of Run.

Ports and endpoints
- jira_ingester: runs on port 8081 by default.
  - Health: http://localhost:8081/actuator/health (if actuator enabled by profile; otherwise check logs)
  - Webhook endpoint: see WebhookController for the exact path.
- agent: runs on port 8080 by default.

Running the CLI (no RabbitMQ)
- The agent also has a Spring Shell CLI entry point.
- Run with the 'cli' profile to disable all RabbitMQ auto-configuration and listeners:
  - From IDE: set SPRING_PROFILES_ACTIVE=cli and run org.dacrewj.agent.DacrewAgentShellApplication
  - From command line: ./gradlew :agent:bootRun --args='--spring.profiles.active=cli' -PmainClass=org.dacrewj.agent.DacrewAgentShellApplication
- application-cli.yml excludes RabbitMQ auto-config and prevents listener startup; it will not attempt to connect to localhost:5672.

Logs
- Logs go to the IDE console. To change log levels, edit application.yml or pass:
  -Dlogging.level.org.dacrewj=DEBUG

Debugging tips
- Set breakpoints in:
  - jira_ingester: controller and message publishing logic
  - agent: DacrewWorkConsumer and Jira* services
- Ensure a queue exists (dacrew.work by default). messaging-core creates the queue on startup (durable).
- If the consumer isn’t receiving messages, verify RabbitMQ creds, queue name (app.rabbit.queue-name), and that jira_ingester is publishing.

Troubleshooting
- Connection refused to RabbitMQ:
  - Is the Docker rabbit container running? Management UI accessible at 15672?
  - Using appuser/apppass (not guest/guest)? The guest user is restricted to localhost only by default.
- 500 from webhook endpoint:
  - Ensure JIRA_WEBHOOK_SECRET is set. The app returns 500 if the secret is missing to avoid insecure operation.
- Port already in use (8080/8081):
  - Change with -Dserver.port=XXXX in your Run/Debug configuration.
- .env not loaded:
  - Confirm file name is exactly .env and located at the project root (or module directory). The loader logs a line when it loads entries.

Alternative: docker compose
- If you prefer containers during development:
  - jira_ingester only: docker compose -f jira_ingester/compose.yaml up --build
  - all-in-one (both apps): docker compose -f compose.dual.yaml up --build

That’s it. Start RabbitMQ, set env vars (via .env or IDE), Run/Debug each app from your IDE, watch logs in console, and put breakpoints where needed.
