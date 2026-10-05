package com.helpi.conversation.evaluacion

import android.app.ActivityManager
import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.SystemClock
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.helpi.evaluacion.datos.HelpiEvaluacionDatabase
import com.helpi.evaluacion.datos.PrediccionTopDato
import com.helpi.evaluacion.datos.entidades.CausaInterrupcion
import com.helpi.evaluacion.datos.entidades.CondicionesPruebaEntity
import com.helpi.evaluacion.datos.entidades.ConsentimientoEntity
import com.helpi.evaluacion.datos.entidades.Distancia
import com.helpi.evaluacion.datos.entidades.Entorno
import com.helpi.evaluacion.datos.entidades.EstadoSesion
import com.helpi.evaluacion.datos.entidades.Iluminacion
import com.helpi.evaluacion.datos.entidades.IntentoEntity
import com.helpi.evaluacion.datos.entidades.ManoDominante
import com.helpi.evaluacion.datos.entidades.ParticipanteEntity
import com.helpi.evaluacion.datos.entidades.PerfilParticipante
import com.helpi.evaluacion.datos.entidades.ResultadoIntento
import com.helpi.evaluacion.datos.entidades.SesionEntity
import com.helpi.evaluacion.datos.entidades.SoporteCamara
import com.helpi.evaluacion.datos.entidades.TipoEntorno
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Verifica la recuperación real al matar y volver a iniciar el proceso de evaluación. */
@RunWith(AndroidJUnit4::class)
class SesionInterrumpidaTest {
    @Test
    fun terminaProcesoEntreIntentosYAlReabrirConservaYRetomaLaSesion() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val paquete = context.packageName
        abrirAplicacion(paquete)

        val database = Room.databaseBuilder(
            context,
            HelpiEvaluacionDatabase::class.java,
            "helpi_evaluacion.db"
        ).build()
        val codigoParticipante = withContext(Dispatchers.IO) {
            (9000..9999).asSequence()
                .map { "P-$it" }
                .first { database.participanteDao().buscar(it) == null }
        }
        val sesionId = UUID.randomUUID().toString()
        try {
            withContext(Dispatchers.IO) {
                crearSesionEnCurso(database, sesionId, codigoParticipante)
                registrarIntentoRespondido(database, sesionId)
            }
        } finally {
            database.close()
        }

        val procesoAppInfo = context.getSystemService(ActivityManager::class.java)
            .runningAppProcesses.orEmpty()
            .firstOrNull { it.processName == paquete && it.pid != Process.myPid() }
        val procesoApp = requireNotNull(procesoAppInfo) {
            "No se encontró el proceso principal de $paquete separado del runner"
        }
        Process.killProcess(procesoApp.pid)
        repeat(50) {
            val sigueActivo = context.getSystemService(ActivityManager::class.java)
                .runningAppProcesses.orEmpty().any { it.pid == procesoApp.pid }
            if (!sigueActivo) return@repeat
            SystemClock.sleep(100L)
        }
        assertFalse(
            "El proceso de la app no terminó; PID=${procesoApp.pid}",
            context.getSystemService(ActivityManager::class.java)
                .runningAppProcesses.orEmpty().any { it.pid == procesoApp.pid }
        )
        abrirAplicacion(paquete)

        val recuperada = Room.databaseBuilder(
            context,
            HelpiEvaluacionDatabase::class.java,
            "helpi_evaluacion.db"
        ).build()
        try {
            withContext(Dispatchers.IO) {
                val sesion = requireNotNull(recuperada.sesionDao().buscar(sesionId))
                val interrupciones = recuperada.sesionDao().interrupciones(sesionId)
                assertEquals(EstadoSesion.INTERRUMPIDA, sesion.estado)
                assertEquals(codigoParticipante, sesion.participanteCodigo)
                assertEquals(20261005L, sesion.semilla)
                assertEquals(1, recuperada.intentoDao().cantidadDeSesion(sesionId))
                assertEquals(1, interrupciones.size)
                assertEquals(CausaInterrupcion.PROCESO_TERMINADO, interrupciones.single().causa)
                assertEquals(2, interrupciones.single().posicionPendiente)
                assertEquals(condiciones(sesionId), recuperada.sesionDao().condiciones(sesionId))
                assertTrue(
                    recuperada.intentoDao().listarDeSesion(sesionId)
                        .none { it.posicion == 2 }
                )
                recuperada.participanteDao().buscar(codigoParticipante)?.let {
                    recuperada.participanteDao().eliminar(it)
                }
                Unit
            }
        } finally {
            recuperada.close()
        }
    }

    private fun abrirAplicacion(paquete: String) {
        comandoShell("am start -W -n $paquete/com.helpi.conversation.MainActivity")
    }

    private fun comandoShell(comando: String): String {
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand(comando)
        return ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use {
            it.readText()
        }
    }

    private suspend fun crearSesionEnCurso(
        database: HelpiEvaluacionDatabase,
        sesionId: String,
        codigoParticipante: String
    ) {
        val consentimientoId = database.consentimientoDao().insertar(
            ConsentimientoEntity(
                avisoVersion = "1.0",
                avisoVideoSha256 = HASH,
                otorgadoEn = 20L,
                revocadoEn = null
            )
        )
        database.participanteDao().insertar(ParticipanteEntity(codigoParticipante, 10L))
        database.sesionDao().crearConCondiciones(
            SesionEntity(
                id = sesionId,
                participanteCodigo = codigoParticipante,
                consentimientoId = consentimientoId,
                protocoloVersion = "1.0.0",
                semilla = 20261005L,
                estado = EstadoSesion.CREADA,
                bloqueActual = 1,
                appVersionName = "1.0",
                appVersionCode = 1,
                modeloVersion = "1.0",
                modeloSha256 = HASH,
                catalogoSha256 = HASH,
                contratoKeypoints = 1,
                dispositivoFabricante = "test",
                dispositivoModelo = "test",
                sdkAndroid = 35,
                creadaEn = 30L,
                ultimoIntentoEn = null,
                ultimaExportacionEn = null,
                intentosPerdidos = 0,
                revision = 0
            ),
            condiciones(sesionId)
        )
        check(database.sesionDao().iniciarBloque(sesionId, 1))
    }

    private suspend fun registrarIntentoRespondido(
        database: HelpiEvaluacionDatabase,
        sesionId: String
    ) {
        database.intentoDao().registrarIntento(
            intento = IntentoEntity(
                sesionId = sesionId,
                posicion = 1,
                bloque = 1,
                numeroIntento = 1,
                reemplazado = false,
                senaEsperadaIndice = 1,
                senaEsperadaGlosa = "CASA",
                vioVideo = false,
                resultado = ResultadoIntento.SOBRE_UMBRAL,
                causaSinResultado = null,
                predichoIndice = 1,
                predichoGlosa = "CASA",
                confianza = 0.9f,
                umbral = 0.7f,
                superoUmbral = true,
                correcto = true,
                descartado = false,
                loHiceMal = false,
                inicioRelMs = 100L,
                duracionSegmentoMs = 500L
            ),
            top3 = listOf(
                PrediccionTopDato(1, 1, "CASA", 0.9f),
                PrediccionTopDato(2, 2, "AUTO", 0.06f),
                PrediccionTopDato(3, 3, "AGUA", 0.04f)
            ),
            momento = 800L
        )
    }

    private fun condiciones(sesionId: String) = CondicionesPruebaEntity(
        sesionId = sesionId,
        entorno = Entorno.EXTERIOR,
        tipoEntorno = TipoEntorno.TRANSPORTE,
        iluminacion = Iluminacion.BAJA,
        contraluz = true,
        distancia = Distancia.MAS_DE_2M,
        manoDominante = ManoDominante.ZURDA,
        guantes = true,
        soporteCamara = SoporteCamara.EN_MANO,
        perfilParticipante = PerfilParticipante.EQUIPO
    )

    private companion object {
        const val HASH = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    }
}
