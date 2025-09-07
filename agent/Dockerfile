# Simple runtime-only Dockerfile for the agent module
# Build the JAR first (for example: ./gradlew :agent:bootJar)
# Then build the image: docker build -t dacrewj/agent:local .

FROM eclipse-temurin:21-jre-alpine AS runtime

# Set a dedicated, non-root user for better security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

# Application directories
WORKDIR /app

# Expose the default Spring Boot port
EXPOSE 8080

# Copy the fat jar produced by Spring Boot
# You may override the JAR_FILE build arg when building the image
ARG JAR_FILE=build/libs/agent-0.0.1-SNAPSHOT.jar
COPY ${JAR_FILE} app.jar

# JVM and Spring Boot runtime options (tune as needed)
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0 -Dfile.encoding=UTF-8"
ENV SPRING_PROFILES_ACTIVE=${SPRING_PROFILES_ACTIVE:-default}

# Healthcheck (optional; relies on Spring Boot actuator if enabled)
# HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
#   CMD wget -qO- http://localhost:8080/actuator/health | grep '"status":"UP"' || exit 1

# Require JIRA_TOKEN or JIRA_TOKEN_FILE at runtime; fail fast if missing.
# Usage examples:
#   docker run -e JIRA_TOKEN=... dacrewj/agent:local
#   docker run -e JIRA_TOKEN_FILE=/run/secrets/jira_token -v /path/secret:/run/secrets/jira_token:ro dacrewj/agent:local
ENTRYPOINT ["sh", "-c", "set -eu; \
  if [ \"${JIRA_TOKEN:-}\" = \"\" ] && [ ! -f \"${JIRA_TOKEN_FILE:-}\" ]; then \
    echo 'ERROR: JIRA_TOKEN not set and JIRA_TOKEN_FILE not provided. Set JIRA_TOKEN env var or mount a secret file and set JIRA_TOKEN_FILE.' >&2; \
    exit 1; \
  fi; \
  if [ \"${JIRA_TOKEN:-}\" = \"\" ] && [ -f \"${JIRA_TOKEN_FILE:-}\" ]; then \
    export JIRA_TOKEN=\"$(cat \"${JIRA_TOKEN_FILE}\")\"; \
  fi; \
  exec java $JAVA_OPTS -jar /app/app.jar"]
