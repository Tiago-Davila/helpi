package com.helpi.evaluacion.envio

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.helpi.evaluacion.datos.HelpiEvaluacionDatabase
import com.helpi.evaluacion.datos.HelpiEvaluacionDatabaseProvider
import com.helpi.evaluacion.datos.entidades.EstadoDepuracionEntity
import com.helpi.evaluacion.datos.entidades.EstadoEnvio
import com.helpi.evaluacion.dominio.retencion.ReglasRetencion
import com.helpi.evaluacion.dominio.retencion.ReglasRetencion.DecisionReloj
import com.helpi.evaluacion.dominio.retencion.ReglasRetencion.EstadoEnvio as EstadoEnvioRetencion
import java.util.concurrent.TimeUnit

class DepuracionWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val database = HelpiEvaluacionDatabaseProvider.obtener(applicationContext)
        return try {
            depurar(database, System.currentTimeMillis())
            Result.success()
        } catch (cancelacion: kotlinx.coroutines.CancellationException) {
            throw cancelacion
        } catch (error: Exception) {
            Log.e(TAG, "Falló la depuración de datos de evaluación", error)
            Result.retry()
        }
    }

    private suspend fun depurar(database: HelpiEvaluacionDatabase, ahora: Long) {
        database.withTransaction {
            val dao = database.depuracionDao()
            val anterior = dao.obtener()
            val decision = ReglasRetencion.evaluarReloj(anterior?.ultimaHoraVistaEn, ahora)
            val ultimaHoraVista = when {
                anterior == null -> ahora
                ahora > anterior.ultimaHoraVistaEn -> ahora
                else -> anterior.ultimaHoraVistaEn
            }

            if (decision != DecisionReloj.PERMITE_DEPURAR) {
                Log.w(TAG, "Depuración omitida por protección de reloj: $decision")
                dao.guardar(
                    EstadoDepuracionEntity(
                        ultimaHoraVistaEn = ultimaHoraVista,
                        ultimaDepuracionEn = anterior?.ultimaDepuracionEn
                    )
                )
                return@withTransaction
            }

            val envioDao = database.envioDao()
            val sesionDao = database.sesionDao()
            for (envio in envioDao.listarTodos()) {
                val estado = when (envio.estado) {
                    EstadoEnvio.PENDIENTE -> EstadoEnvioRetencion.PENDIENTE
                    EstadoEnvio.RECHAZADO -> EstadoEnvioRetencion.RECHAZADO
                    EstadoEnvio.BLOQUEADO -> EstadoEnvioRetencion.BLOQUEADO
                }
                if (ReglasRetencion.envioVencido(estado, envio.creadoEn, ahora)) {
                    if (envio.estado == EstadoEnvio.PENDIENTE) {
                        sesionDao.marcarEnvioVencido(envio.sesionId)
                    }
                    envioDao.eliminarPorId(envio.envioId)
                }
            }

            val sesionesVencidas = sesionDao.listarTodas()
                .filter { sesion ->
                    ReglasRetencion.sesionVencida(
                        sesion.ultimaExportacionEn,
                        sesion.ultimoIntentoEn,
                        sesion.creadaEn,
                        ahora
                    )
                }
                .map { it.id }
            if (sesionesVencidas.isNotEmpty()) {
                sesionDao.eliminar(sesionesVencidas)
            }

            dao.guardar(
                EstadoDepuracionEntity(
                    ultimaHoraVistaEn = ultimaHoraVista,
                    ultimaDepuracionEn = ahora
                )
            )
        }
    }

    companion object {
        private const val TAG = "DepuracionWorker"
        private const val TRABAJO_PERIODICO = "depuracion-sesiones-evaluacion-diaria"
        private const val TRABAJO_AL_ABRIR = "depuracion-sesiones-evaluacion-apertura"

        fun programar(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .build()
            val request = PeriodicWorkRequestBuilder<DepuracionWorker>(1, TimeUnit.DAYS)
                .setConstraints(constraints)
                .build()
            workManager(context).enqueueUniquePeriodicWork(
                TRABAJO_PERIODICO,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun ejecutarAlAbrir(context: Context) {
            val request = OneTimeWorkRequestBuilder<DepuracionWorker>().build()
            workManager(context).enqueueUniqueWork(
                TRABAJO_AL_ABRIR,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                request
            )
        }

        private fun workManager(context: Context): WorkManager {
            val appContext = context.applicationContext
            return try {
                WorkManager.getInstance(appContext)
            } catch (_: IllegalStateException) {
                WorkManager.initialize(appContext, Configuration.Builder().build())
                WorkManager.getInstance(appContext)
            }
        }
    }
}
