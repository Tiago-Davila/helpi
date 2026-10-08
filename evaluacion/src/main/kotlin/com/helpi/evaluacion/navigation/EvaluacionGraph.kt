package com.helpi.evaluacion.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Minimal route graph for evaluation screens. */
object EvaluacionGraph {
    const val VALIDATION_ROUTE = "validacion"
    const val ALTA_SESION_ROUTE = "alta-sesion"
    const val SETTINGS_ROUTE = "ajustes-evaluacion"
    const val DELETE_ROUTE = "borrar-datos-evaluacion"

    var currentRoute: String? by mutableStateOf(null)
        private set

    var sesionActualId: String? by mutableStateOf(null)
        private set

    fun navigate(route: String) {
        require(route in setOf(VALIDATION_ROUTE, ALTA_SESION_ROUTE, SETTINGS_ROUTE, DELETE_ROUTE)) {
            "Unknown evaluation route: $route"
        }
        currentRoute = route
    }

    fun establecerSesionActual(sesionId: String) {
        sesionActualId = sesionId
    }

    fun volver() {
        currentRoute = null
    }
}
