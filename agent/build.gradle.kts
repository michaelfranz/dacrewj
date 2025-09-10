plugins {
    java
    id("org.springframework.boot") version "3.5.5"
    id("io.spring.dependency-management") version "1.1.7"
}

tasks.register("prepareKotlinBuildScriptModel") {
    // no-op for IDE compatibility with Kotlin DSL model task
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
    maven {
        name = "embabel-releases"
        url = uri("https://repo.embabel.com/artifactory/libs-release")
        mavenContent { releasesOnly() }
    }
    maven {
        name = "embabel-snapshots"
        url = uri("https://repo.embabel.com/artifactory/libs-snapshot")
        mavenContent { snapshotsOnly() }
    }
    maven {
        name = "spring-milestones"
        url = uri("https://repo.spring.io/milestone")
    }
}

val embabelVersion = "0.1.3-SNAPSHOT"
val springShellVersion = "3.4.1"

dependencies {
    implementation(project(":common"))
    implementation(project(":messaging-core"))
    implementation(project(":contracts"))

    implementation("org.springframework.shell:spring-shell-starter:$springShellVersion")
    implementation("org.springframework.boot:spring-boot-starter-amqp")
    implementation("org.springframework.boot:spring-boot-starter-web")

    // Embabel
    implementation("com.embabel.agent:embabel-agent-starter:$embabelVersion")
    implementation("com.embabel.agent:embabel-agent-starter-shell:$embabelVersion")
    testImplementation("com.embabel.agent:embabel-agent-test:$embabelVersion")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.amqp:spring-rabbit-test")
    testImplementation("javax.servlet:javax.servlet-api:4.0.1")

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}
