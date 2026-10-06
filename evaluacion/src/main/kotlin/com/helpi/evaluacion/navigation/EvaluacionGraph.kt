package com.helpi.evaluacion.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Minimal route graph until the evaluation flow receives its own screen. */
object EvaluacionGraph {
    const val VALIDATION_ROUTE = "validacion"

    var currentRoute: String? by mutableStateOf(null)
        private set

    fun navigate(route: String) {
        require(route == VALIDATION_ROUTE) { "Unknown evaluation route: $route" }
        currentRoute = route
    }

    fun volver() {
        currentRoute = null
    }
}
