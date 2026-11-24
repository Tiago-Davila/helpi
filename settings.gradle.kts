pluginManagement {
    repositories {
        google()
        if (System.getenv("HELPI_ESPEJO_MAVEN") == "true") {
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
        // el espejo de Google (el mismo de scripts/ci/robolectric.sh) va primero. Lo activa solo
        // ci.yml: en el grafo de dependencias cambiaría el origen registrado de cada paquete.
        if (System.getenv("HELPI_ESPEJO_MAVEN") == "true") {
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
