plugins {
    kotlin("jvm") version "2.1.0"
}

allprojects {
    repositories {
        mavenCentral()
    }
}

subprojects {
    // Common configuration for all subprojects can go here in future.
    plugins.apply("java")

    dependencies {
        testImplementation("org.assertj:assertj-core:3.26.0")
    }
}
