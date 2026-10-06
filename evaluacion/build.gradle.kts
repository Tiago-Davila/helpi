import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    id("org.jlleitschuh.gradle.ktlint")
    id("io.gitlab.arturbosch.detekt")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.isFile }?.inputStream()?.use { stream ->
        load(stream)
    }
}

fun javaStringLiteral(value: String): String {
    val escaped = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
    return "\"$escaped\""
}

fun evaluationProperty(name: String): String =
    providers.gradleProperty(name).orNull ?: localProperties.getProperty(name).orEmpty()

val urlReceptor = evaluationProperty("helpi.evaluacion.urlReceptor")
val claveEnvio = evaluationProperty("helpi.evaluacion.claveEnvio")

android {
    namespace = "com.helpi.evaluacion"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        buildConfigField("String", "URL_RECEPTOR", javaStringLiteral(urlReceptor))
        buildConfigField("String", "CLAVE_ENVIO", javaStringLiteral(claveEnvio))
        buildConfigField(
            "boolean",
            "ENVIO_HABILITADO",
            (urlReceptor.isNotBlank() && claveEnvio.isNotBlank()).toString()
        )
    }

    sourceSets {
        getByName("test") {
            resources.srcDir("../specs/001-calidad-reconocimiento/contracts")
        }
    }

    testOptions { unitTests.isIncludeAndroidResources = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":evaluacion-dominio"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.work.runtime.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.room.testing)
    testImplementation(libs.work.testing)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.json.schema.validator)
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
