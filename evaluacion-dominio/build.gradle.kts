plugins {
    `java-library`
    jacoco
    id("com.github.spotbugs")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    testImplementation(libs.junit)
}

sourceSets {
    test {
        resources.srcDir("../specs/001-calidad-reconocimiento/contracts")
    }
}

tasks.test {
    useJUnit()
}

jacoco { toolVersion = "0.8.12" }

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

spotbugs {
    ignoreFailures.set(false)
    excludeFilter.set(rootProject.file("config/quality/spotbugs-exclude.xml"))
}

tasks.withType<com.github.spotbugs.snom.SpotBugsTask>().configureEach {
    reports.create("xml") { required.set(true) }
    reports.create("html") { required.set(true) }
}
