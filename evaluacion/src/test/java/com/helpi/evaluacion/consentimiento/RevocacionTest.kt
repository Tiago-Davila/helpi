package com.helpi.evaluacion.consentimiento

import androidx.room.Room
import com.helpi.evaluacion.datos.HelpiEvaluacionDatabase
import com.helpi.evaluacion.datos.PrediccionTopDato
import com.helpi.evaluacion.datos.entidades.CausaInterrupcion
import com.helpi.evaluacion.datos.entidades.CondicionesPruebaEntity
import com.helpi.evaluacion.datos.entidades.ConsentimientoEntity
import com.helpi.evaluacion.datos.entidades.Distancia
import com.helpi.evaluacion.datos.entidades.Entorno
import com.helpi.evaluacion.datos.entidades.EnvioEntity
import com.helpi.evaluacion.datos.entidades.EstadoEnvio
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
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RevocacionTest {
    private lateinit var database: HelpiEvaluacionDatabase

    @Before
    fun abrirBase() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            HelpiEvaluacionDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun cerrarBase() {
        database.close()
    }

    @Test
    fun revocarInterrumpeElRegistroYEliminaPendientesSinBorrarLoGuardado() = runBlocking {
        val consentimientoId = crearConsentimiento()
        val sesionEnCurso = crearSesion("P-001", SESION_1, consentimientoId)
        val sesionCreada = crearSesion("P-002", SESION_2, consentimientoId)
        check(database.sesionDao().iniciarBloque(sesionEnCurso, 1, AVISO_VERSION_ACTUAL, HASH))
        registrarIntentoRespondido(sesionEnCurso)
        insertarEnvio(sesionEnCurso, ENVIO_PENDIENTE, EstadoEnvio.PENDIENTE)
        insertarEnvio(sesionEnCurso, ENVIO_RECHAZADO, EstadoEnvio.RECHAZADO)

        assertTrue(Revocacion(database, reloj = { AHORA }).revocar())

        assertEquals(AHORA, database.consentimientoDao().buscar(consentimientoId)?.revocadoEn)
        assertEquals(2, database.sesionDao().listarTodas().size)
        assertEquals(EstadoSesion.INTERRUMPIDA, database.sesionDao().estado(sesionEnCurso))
        assertEquals(EstadoSesion.CREADA, database.sesionDao().estado(sesionCreada))
        assertEquals(1, database.intentoDao().cantidadDeSesion(sesionEnCurso))
        val interrupcion = database.sesionDao().interrupciones(sesionEnCurso).single()
        assertEquals(CausaInterrupcion.REVOCACION, interrupcion.causa)
        assertEquals(2, interrupcion.posicionPendiente)
        assertEquals(AHORA, interrupcion.ocurrioEn)
        assertNull(database.envioDao().buscar(ENVIO_PENDIENTE))
        assertEquals(EstadoEnvio.RECHAZADO, database.envioDao().buscar(ENVIO_RECHAZADO)?.estado)
        assertFalse(
            database.sesionDao().iniciarBloque(
                sesionCreada,
                1,
                AVISO_VERSION_ACTUAL,
                HASH
            )
        )
    }

    @Test
    fun haceFaltaAceptarNuevamenteParaIniciarUnaSesionTrasLaRevocacion() = runBlocking {
        val consentimientoId = crearConsentimiento()
        val sesionId = crearSesion("P-001", SESION_1, consentimientoId)

        assertTrue(Revocacion(database, reloj = { AHORA }).revocar())
        assertFalse(database.sesionDao().iniciarBloque(sesionId, 1, AVISO_VERSION_ACTUAL, HASH))

        database.consentimientoDao().insertar(
            ConsentimientoEntity(
                avisoVersion = AVISO_VERSION_ACTUAL,
                avisoVideoSha256 = HASH,
                otorgadoEn = AHORA + 1,
                revocadoEn = null
            )
        )

        assertTrue(database.sesionDao().iniciarBloque(sesionId, 1, AVISO_VERSION_ACTUAL, HASH))
    }

    private suspend fun crearConsentimiento(): Long = database.consentimientoDao().insertar(
        ConsentimientoEntity(
            avisoVersion = AVISO_VERSION_ACTUAL,
            avisoVideoSha256 = HASH,
            otorgadoEn = 10L,
            revocadoEn = null
        )
    )

    private suspend fun crearSesion(
        participanteCodigo: String,
        sesionId: String,
        consentimientoId: Long
    ): String {
        database.participanteDao().insertar(ParticipanteEntity(participanteCodigo, 5L))
        database.sesionDao().crearConCondiciones(
            SesionEntity(
                id = sesionId,
                participanteCodigo = participanteCodigo,
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
                creadaEn = 20L,
                ultimoIntentoEn = null,
                ultimaExportacionEn = null,
                intentosPerdidos = 0,
                revision = 0
            ),
            condiciones(sesionId)
        )
        return sesionId
    }

    private suspend fun registrarIntentoRespondido(sesionId: String) {
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
            momento = 100L
        )
    }

    private suspend fun insertarEnvio(sesionId: String, envioId: String, estado: EstadoEnvio) {
        database.envioDao().insertar(
            EnvioEntity(
                envioId = envioId,
                sesionId = sesionId,
                revision = 1,
                payload = byteArrayOf(1, 2, 3),
                sha256 = HASH,
                estado = estado,
                motivo = null,
                intentos = 0,
                creadoEn = 200L,
                ultimoIntentoEn = null
            )
        )
    }

    private fun condiciones(sesionId: String) = CondicionesPruebaEntity(
        sesionId = sesionId,
        entorno = Entorno.INTERIOR,
        tipoEntorno = TipoEntorno.CASA,
        iluminacion = Iluminacion.BUENA,
        contraluz = false,
        distancia = Distancia.ENTRE_1_Y_2M,
        manoDominante = ManoDominante.DIESTRA,
        guantes = false,
        soporteCamara = SoporteCamara.TRIPODE,
        perfilParticipante = PerfilParticipante.SORDA_SENANTE
    )

    private companion object {
        const val HASH = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        const val AHORA = 999L
        const val SESION_1 = "6f1d90ab-a125-4ab2-842c-05205b62db38"
        const val SESION_2 = "6f1d90ab-a125-4ab2-842c-05205b62db39"
        const val ENVIO_PENDIENTE = "2f1d90ab-a125-4ab2-842c-05205b62db38"
        const val ENVIO_RECHAZADO = "2f1d90ab-a125-4ab2-842c-05205b62db39"
    }
}
