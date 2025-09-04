# syntax=docker/dockerfile:1.7

# --- Build stage -----------------------------------------------------------
FROM eclipse-temurin:21-jdk AS build

WORKDIR /workspace

# Copy only necessary files for a Gradle multi-project build
COPY settings.gradle ./
COPY build.gradle ./
COPY contracts/settings.gradle.kts contracts/settings.gradle.kts
COPY contracts/build.gradle.kts contracts/build.gradle.kts
COPY jira_ingester/settings.gradle jira_ingester/settings.gradle
COPY jira_ingester/build.gradle jira_ingester/build.gradle

# Copy sources
COPY contracts ./contracts
COPY jira_ingester ./jira_ingester

# Build only the jira_ingester module; use Gradle wrapper from jira_ingester
WORKDIR /workspace/jira_ingester
RUN ./gradlew clean bootJar --no-daemon

# --- Runtime stage ---------------------------------------------------------
FROM eclipse-temurin:21-jre

# Create non-root user
RUN useradd --system --create-home --uid 1001 appuser
USER appuser

# App directory
WORKDIR /app

# Copy fat jar from build stage
COPY --from=build /workspace/jira_ingester/build/libs/*.jar /app/app.jar

# Default port
EXPOSE 8080

# Health and HMAC settings via env; can be overridden at runtime
ENV SERVER_PORT=8080 \
    JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC -XX:MaxGCPauseMillis=200" \
    SPRING_PROFILES_ACTIVE=default

# Run Spring Boot application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
