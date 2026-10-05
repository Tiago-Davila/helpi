plugins {
    jacoco
    id("org.jlleitschuh.gradle.ktlint")
    id("io.gitlab.arturbosch.detekt")
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.helpi.conversation"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.helpi.conversation"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // MediaPipe Tasks 0.10.32 incluye x86_64, necesario para probar la misma
        // cadena de inferencia en el emulador oficial de Google.
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    flavorDimensions += "registro"

    productFlavors {
        create("evaluacion") {
            dimension = "registro"
            applicationIdSuffix = ".evaluacion"
            versionNameSuffix = "-evaluacion"
            resValue("string", "app_name", "Helpi Evaluación")
        }
        create("produccion") {
            dimension = "registro"
        }
    }

    // Obligatorio: sin esto Gradle comprime los modelos en el APK y
    // LiteRT/MediaPipe no pueden mapearlos a memoria (falla poco descriptiva).
    androidResources {
        noCompress += listOf("tflite", "task")
    }

    testOptions { unitTests.isIncludeAndroidResources = true }

    lint {
        baseline = file("../config/quality/lint-baseline.xml")
        abortOnError = true
        warningsAsErrors = true
        // Dependabot handles version availability; keep lint deterministic/offline.
        disable += setOf(
            "GradleDependency",
            "AndroidGradlePluginVersion",
            "NewerVersionAvailable",
            // The runner may have a newer SDK installed than compileSdk. API 35
            // remains intentional until the API 36 behavior migration is tested.
            "OldTargetApi"
        )
        checkDependencies = true
    }

    testCoverage { jacocoVersion = "0.8.12" }

    buildTypes {
        debug { enableUnitTestCoverage = true }
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }
}

dependencies {
    implementation(project(":domain"))
    add("evaluacionImplementation", project(":evaluacion"))

    if (providers.gradleProperty("canarioEvaluacion").orNull == "true") {
        add("produccionImplementation", project(":evaluacion"))
    }

    implementation(libs.kotlinx.coroutines.android)

    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)

    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)

    implementation(libs.mediapipe.tasks.vision) {
        // tasks-core 0.10.32 fue compilado contra el retorno concreto de
        // Any.Builder.build(); protobuf-javalite 4.x sólo expone el retorno
        // genérico y falla en runtime. El runtime completo conserva esa firma.
        exclude(group = "com.google.protobuf", module = "protobuf-javalite")
        // El backend CCT solo envía telemetría. La aplicación no usa red.
        exclude(group = "com.google.android.datatransport", module = "transport-backend-cct")
    }
    implementation(libs.protobuf.java)
    // MediaPipe 0.10.32 requests Guava 27; use the patched Android runtime.
    implementation(libs.guava)
    implementation(libs.litert)
    implementation(libs.vosk.android)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    // Robolectric 4.14.1 trae BouncyCastle 1.78.1 (GHSA-574f-3g2m-x479, GHSA-9pwp-9qqc-pr26,
    // GHSA-qp49-qgx5-5m26, corregidos en 1.85). Solo afecta al classpath de tests.
    constraints {
        testImplementation("org.bouncycastle:bcprov-jdk18on:1.86") {
            because("vulnerabilidades críticas en versiones < 1.85")
        }
    }
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(composeBom)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}

ktlint {
    version.set("1.5.0")
    baseline.set(rootProject.file("config/quality/ktlint-baseline.xml"))
}

detekt {
    buildUponDefaultConfig = true
    baseline = rootProject.file("config/quality/detekt-baseline.xml")
    config.setFrom(rootProject.file("config/quality/detekt.yml"))
}

jacoco { toolVersion = "0.8.12" }

tasks.withType<Test>().configureEach {
    extensions.configure<JacocoTaskExtension> {
        isIncludeNoLocationClasses = true
        excludes = listOf("jdk.internal.*")
    }
}

tasks.register<JacocoReport>("jacocoProduccionDebugReport") {
    dependsOn("testProduccionDebugUnitTest")
    executionData.setFrom(
        layout.buildDirectory.file(
            "outputs/unit_test_code_coverage/produccionDebugUnitTest/testProduccionDebugUnitTest.exec"
        )
    )
    classDirectories.setFrom(
        fileTree(layout.buildDirectory.dir("tmp/kotlin-classes/produccionDebug")) {
            exclude("**/R.class", "**/R$*.class", "**/BuildConfig.*", "**/Manifest*.*")
        }
    )
    sourceDirectories.setFrom(files("src/main/java"))
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.register<JacocoReport>("jacocoEvaluacionDebugReport") {
    dependsOn("testEvaluacionDebugUnitTest")
    executionData.setFrom(
        layout.buildDirectory.file(
            "outputs/unit_test_code_coverage/evaluacionDebugUnitTest/testEvaluacionDebugUnitTest.exec"
        )
    )
    classDirectories.setFrom(
        fileTree(layout.buildDirectory.dir("tmp/kotlin-classes/evaluacionDebug")) {
            exclude("**/R.class", "**/R$*.class", "**/BuildConfig.*", "**/Manifest*.*")
        }
    )
    sourceDirectories.setFrom(files("src/main/java"))
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.register("verificarProduccionSinEvaluacion") {
    group = "verification"
    description = "Ensures the production runtime classpath excludes evaluation-only dependencies."

    doLast {
        val forbiddenProjectPaths = setOf(":evaluacion", ":evaluacion-dominio")

        fun forbiddenDependencies(configurationName: String): Set<String> =
            configurations.getByName(configurationName)
                .incoming.resolutionResult.allComponents
                .mapNotNull { component ->
                    when (val id = component.id) {
                        is org.gradle.api.artifacts.component.ProjectComponentIdentifier ->
                            id.projectPath.takeIf { it in forbiddenProjectPaths }

                        is org.gradle.api.artifacts.component.ModuleComponentIdentifier -> {
                            when {
                                id.group == "androidx.room" && id.module.startsWith("room-") ->
                                    "${id.group}:${id.module}"

                                id.group == "androidx.work" && id.module.startsWith("work-") ->
                                    "${id.group}:${id.module}"

                                id.group == "com.google.android.datatransport" &&
                                    id.module == "transport-backend-cct" ->
                                    "${id.group}:${id.module}"

                                else -> null
                            }
                        }

                        else -> null
                    }
                }.toSet()

        val productionForbidden = forbiddenDependencies("produccionReleaseRuntimeClasspath")
        check(productionForbidden.isEmpty()) {
            "Forbidden production dependency: ${productionForbidden.sorted().joinToString()}"
        }

        val evaluationCct = forbiddenDependencies("evaluacionReleaseRuntimeClasspath")
            .filter { it == "com.google.android.datatransport:transport-backend-cct" }
        check(evaluationCct.isEmpty()) {
            "Forbidden evaluation telemetry dependency: ${evaluationCct.sorted().joinToString()}"
        }
    }
}
