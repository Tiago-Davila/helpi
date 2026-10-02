buildscript {
    dependencies {
        // AGP arrastra netty 4.1.93 vía grpc-netty (GHSA-c653-97m9-rcg9, corregido en 4.1.135).
        classpath(platform("io.netty:netty-bom:4.1.138.Final"))
    }
}

plugins {
    id("org.jlleitschuh.gradle.ktlint") version "12.1.2"
    id("io.gitlab.arturbosch.detekt") version "1.23.8" apply false
    id("com.github.spotbugs") version "6.1.13" apply false
    id("org.owasp.dependencycheck") version "13.0.0"
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
    // Analyze only code shipped in the APK plus the Java domain runtime. Build
    // plugins, emulator tooling and test runners are not production inputs.
    scanConfigurations = listOf("releaseRuntimeClasspath", "runtimeClasspath")
    suppressionFile = "config/quality/dependency-suppressions.xml"
    data.directory = "${gradle.gradleUserHomeDir}/dependency-check-data"
    nvd.apiKey = System.getenv("NVD_API_KEY") ?: ""
    // OWASP recommends a mirror for operational CI. Its daily cache avoids
    // unauthenticated NVD throttling while Dependency-Check still performs the
    // matching and fails the build on high/critical findings.
    nvd.datafeedUrl =
        "https://dependency-check.github.io/DependencyCheck_Builder/nvd_cache/nvdcve-{0}.json.gz"
    analyzers.assemblyEnabled = false
    analyzers.nodeEnabled = false
}
