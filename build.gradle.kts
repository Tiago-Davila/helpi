plugins {
    id("org.jlleitschuh.gradle.ktlint") version "12.1.2"
    id("io.gitlab.arturbosch.detekt") version "1.23.8" apply false
    id("com.github.spotbugs") version "6.1.13" apply false
    id("org.owasp.dependencycheck") version "12.2.0"
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

ktlint {
    version.set("1.5.0")
    filter { exclude("**/.agents/**", "**/.specify/**", "**/build/**", "**/lsa/**") }
}

dependencyCheck {
    failBuildOnCVSS = 7.0f
    failOnError = true
    formats = listOf("HTML", "JSON", "SARIF")
    suppressionFile = "config/quality/dependency-suppressions.xml"
    data.directory = "${gradle.gradleUserHomeDir}/dependency-check-data"
    nvd.apiKey = System.getenv("NVD_API_KEY") ?: ""
    analyzers.assemblyEnabled = false
    analyzers.nodeEnabled = false
}
