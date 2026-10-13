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
    // En Android org.json lo provee la plataforma; en la JVM, el artefacto.
    compileOnly(libs.org.json)
    testImplementation(libs.org.json)
    testImplementation(libs.junit)
}

tasks.test {
    useJUnit()
    systemProperty("lsa.assets", rootProject.file("app/src/main/assets/lsa").path)
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
