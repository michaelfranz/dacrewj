rootProject.name = "dacrewj"
include(":agent", ":contracts", ":jira_ingester", ":messaging-core", ":common")

// Explicitly set subproject names to ensure correct display in IDE Gradle tool window
project(":agent").name = "agent"
project(":contracts").name = "contracts"
project(":jira_ingester").name = "jira_ingester"
project(":messaging-core").name = "messaging-core"
project(":common").name = "common"
