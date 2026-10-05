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

android {
    namespace = "com.helpi.evaluacion"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
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
