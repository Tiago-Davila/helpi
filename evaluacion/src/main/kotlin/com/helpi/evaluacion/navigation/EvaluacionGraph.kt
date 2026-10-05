package com.helpi.evaluacion.navigation

/** Minimal route graph until the evaluation flow receives its own screen. */
object EvaluacionGraph {
    const val VALIDATION_ROUTE = "validacion"

    @Volatile
    var currentRoute: String? = null
        private set

    fun navigate(route: String) {
        require(route == VALIDATION_ROUTE) { "Unknown evaluation route: $route" }
        currentRoute = route
    }
}
