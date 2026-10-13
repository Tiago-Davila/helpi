package com.helpi.evaluacion.envio

import android.content.Context
import android.content.pm.PackageManager
import android.database.SQLException
import android.os.Build
import android.util.Log
import androidx.room.withTransaction
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import com.helpi.evaluacion.BuildConfig
import com.helpi.evaluacion.datos.HelpiEvaluacionDatabase
import com.helpi.evaluacion.datos.HelpiEvaluacionDatabaseProvider
import com.helpi.evaluacion.datos.entidades.EnvioEntity
import com.helpi.evaluacion.datos.entidades.EstadoEnvio
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

class EnvioWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        if (!BuildConfig.ENVIO_HABILITADO) {
            Log.w(TAG, "Envío de evaluación deshabilitado: falta URL o credencial")
            return Result.failure()
        }

        return try {
            val packageInfo = applicationContext.packageManager.getPackageInfo(
                applicationContext.packageName,
                0
            )
            val cliente = ReceptorCliente(
                urlReceptor = BuildConfig.URL_RECEPTOR,
                claveEnvio = BuildConfig.CLAVE_ENVIO,
                appVersionName = packageInfo.versionName.orEmpty(),
                appVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    packageInfo.longVersionCode.toInt()
                } else {
                    @Suppress("DEPRECATION")
                    packageInfo.versionCode
                }
            )
            when (
                EnvioColaProcesador(
                    database = HelpiEvaluacionDatabaseProvider.obtener(applicationContext),
                    cliente = cliente
                ).procesar()
            ) {
                EnvioColaProcesador.Decision.COMPLETADO -> Result.success()
                EnvioColaProcesador.Decision.REINTENTAR -> Result.retry()
            }
        } catch (cancelacion: CancellationException) {
            throw cancelacion
        } catch (error: PackageManager.NameNotFoundException) {
            reintentar(error)
        } catch (error: IOException) {
            reintentar(error)
        } catch (error: SQLException) {
            reintentar(error)
        } catch (error: SecurityException) {
            reintentar(error)
        }
    }

    private fun reintentar(error: Exception): Result {
        Log.e(TAG, "Falló el envío de sesiones de evaluación", error)
        return Result.retry()
    }

    companion object {
        private const val TAG = "EnvioWorker"
        private const val BACKOFF_MINUTES = 1L

        fun solicitud(): OneTimeWorkRequest = OneTimeWorkRequestBuilder<EnvioWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                BACKOFF_MINUTES,
                TimeUnit.MINUTES
            )
            .build()
    }
}

internal class EnvioColaProcesador(
    private val database: HelpiEvaluacionDatabase,
    private val cliente: ReceptorCliente,
    private val reloj: () -> Long = System::currentTimeMillis
) {
    suspend fun procesar(): Decision {
        val pendientes = database.envioDao().listarTodos()
            .filter { envio -> envio.estado == EstadoEnvio.PENDIENTE }

        for (envio in pendientes) {
            when (
                val resultado = cliente.enviar(envio.envioId, envio.payload, envio.sha256)
            ) {
                is ResultadoEnvio.Confirmado -> confirmar(envio)
                is ResultadoEnvio.Rechazado -> actualizarEstado(
                    envio,
                    EstadoEnvio.RECHAZADO,
                    resultado.motivo
                )
                is ResultadoEnvio.Bloqueado -> actualizarEstado(
                    envio,
                    EstadoEnvio.BLOQUEADO,
                    resultado.motivo
                )
                is ResultadoEnvio.Transitorio -> return Decision.REINTENTAR
            }
        }
        return Decision.COMPLETADO
    }

    private suspend fun confirmar(envio: EnvioEntity) {
        database.withTransaction {
            if (database.envioDao().eliminarPorId(envio.envioId) == 1) {
                database.sesionDao().actualizarUltimaExportacionEn(envio.sesionId, reloj())
            }
        }
    }

    private suspend fun actualizarEstado(envio: EnvioEntity, estado: EstadoEnvio, motivo: String) {
        database.envioDao().actualizarEstadoPendiente(envio.envioId, estado, motivo)
    }

    enum class Decision {
        COMPLETADO,
        REINTENTAR
    }
}
