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
    // SpotBugs 4.8.6 trae httpclient5/httpcore5 5.1.3 vía Saxon y xmlresolver
    // (GHSA-hf6x-8p5f-cgmf, corregido en 5.4.3). Solo afecta al classpath de análisis.
    constraints {
        spotbugs("org.apache.httpcomponents.client5:httpclient5:5.6.5") {
            because("arrastra httpcore5 sin la denegación de servicio")
        }
        listOf("httpcore5", "httpcore5-h2").forEach {
            spotbugs("org.apache.httpcomponents.core5:$it:5.4.3") {
                because("denegación de servicio en versiones < 5.4.3")
            }
        }
    }
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
