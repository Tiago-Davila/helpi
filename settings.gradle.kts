pluginManagement {
    repositories {
        google()
        if (System.getenv("CI") != null) {
            maven("https://maven-central.storage-download.googleapis.com/maven2")
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        // En CI, Central puede responder HTTP 429 y Gradle corta sin probar otro repositorio:
        // el espejo de Google (el mismo de scripts/ci/robolectric.sh) va primero.
        if (System.getenv("CI") != null) {
            maven("https://maven-central.storage-download.googleapis.com/maven2")
        }
        mavenCentral()
    }
}

rootProject.name = "helpi-conversacion"
include(":app")
include(":domain")
include(":evaluacion")
include(":evaluacion-dominio")
