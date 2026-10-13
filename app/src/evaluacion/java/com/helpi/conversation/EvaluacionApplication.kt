package com.helpi.conversation

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.helpi.evaluacion.datos.HelpiEvaluacionDatabaseProvider
import com.helpi.evaluacion.envio.DepuracionWorker
import com.helpi.evaluacion.registro.RecuperacionSesiones
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/** Recupera sesiones antes de mostrar evaluación y registra las salidas a segundo plano. */
class EvaluacionApplication :
    Application(),
    Application.ActivityLifecycleCallbacks {
    private lateinit var recuperacionSesiones: RecuperacionSesiones

    override fun onCreate() {
        super.onCreate()
        recuperacionSesiones = RecuperacionSesiones(
            HelpiEvaluacionDatabaseProvider.obtener(this)
        )
        runBlocking(Dispatchers.IO) { recuperacionSesiones.alArrancar() }
        DepuracionWorker.programar(this)
        DepuracionWorker.ejecutarAlAbrir(this)
        registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityStopped(activity: Activity) {
        if (activity is MainActivity) {
            runBlocking(Dispatchers.IO) { recuperacionSesiones.alPasarSegundoPlano() }
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

    override fun onActivityStarted(activity: Activity) = Unit

    override fun onActivityResumed(activity: Activity) = Unit

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    override fun onActivityDestroyed(activity: Activity) = Unit
}
