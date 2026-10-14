// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.jetbrainsKotlinAndroid) apply false
    alias(libs.plugins.kotlinCompose) apply false
    alias(libs.plugins.ktfmt) apply false
    alias(libs.plugins.gms) apply false

    id("org.sonarqube") version "7.5.0.8588"
}

sonar {
    properties {
        property("sonar.projectKey", "Amber-Swent_amber")
        property("sonar.organization", "amber-swent")
        property(
            "sonar.coverageReportPaths",
            layout.buildDirectory.file("reports/firestore-rules-coverage.xml").get().asFile,
        )
        property("sonar.coverage.exclusions", "**/scripts/**")
    }
}