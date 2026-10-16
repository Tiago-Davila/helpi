package com.helpi.evaluacion.consentimiento

import androidx.room.Room
import com.helpi.evaluacion.datos.HelpiEvaluacionDatabase
import com.helpi.evaluacion.datos.entidades.CausaSinResultado
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BorradoDatosTest {
    private lateinit var database: HelpiEvaluacionDatabase
    private lateinit var repositorio: BorradoDatos
    private var consentimientoId = 0L

    @Before
    fun abrirBase() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            HelpiEvaluacionDatabase::class.java
        ).allowMainThreadQueries().build()
        repositorio = BorradoDatos(database)
        runBlocking {
            consentimientoId = database.consentimientoDao().insertar(
                ConsentimientoEntity(
                    avisoVersion = AVISO_VERSION_ACTUAL,
                    avisoVideoSha256 = HASH,
                    otorgadoEn = 10L,
                    revocadoEn = 20L
                )
            )
        }
    }

    @After
    fun cerrarBase() {
        database.close()
    }

    @Test
    fun previsualizaYBorraSoloAlParticipanteElegidoSinConsentimientoVigente() = runBlocking {
        val sesionUno = crearSesion("P-001", SESION_1, ultimaExportacionEn = 500L)
        val sesionDos = crearSesion("P-002", SESION_2, ultimaExportacionEn = null)
        registrarIntento(sesionUno)
        registrarIntento(sesionDos)
        insertarEnvio(sesionUno, ENVIO_PENDIENTE)

        val previo = repositorio.previsualizar("P-001")
        assertEquals(1, previo.sesiones)
        assertEquals(1, previo.intentos)
        assertEquals(1, previo.enviosPendientes)
        assertEquals(listOf(sesionUno), previo.sesionesYaCompartidas)

        val eliminado = repositorio.borrar("P-001")

        assertEquals(previo, eliminado)
        assertEquals(listOf(sesionDos), database.sesionDao().listarTodas().map { it.id })
        assertEquals(1, database.intentoDao().cantidadTotal())
        assertTrue(database.envioDao().listarDeSesion(sesionUno).isEmpty())
        assertEquals(1, database.participanteDao().cantidad())
        assertEquals(1, database.consentimientoDao().listar().size)
    }

    @Test
    fun borrarTodasLasSesionesDevuelveIdsCompartidosYConservaElHistorialDeConsentimiento() =
        runBlocking {
            val sesionUno = crearSesion("P-001", SESION_1, ultimaExportacionEn = 500L)
            val sesionDos = crearSesion("P-002", SESION_2, ultimaExportacionEn = null)
            registrarIntento(sesionUno)
            registrarIntento(sesionDos)
            insertarEnvio(sesionDos, ENVIO_PENDIENTE)

            val previo = repositorio.previsualizar(null)
            assertEquals(2, previo.sesiones)
            assertEquals(2, previo.intentos)
            assertEquals(1, previo.enviosPendientes)
            assertEquals(listOf(sesionUno), previo.sesionesYaCompartidas)

            val eliminado = repositorio.borrar(null)

            assertEquals(previo, eliminado)
            assertTrue(database.sesionDao().listarTodas().isEmpty())
            assertEquals(0, database.intentoDao().cantidadTotal())
            assertTrue(database.envioDao().listarTodos().isEmpty())
            assertEquals(0, database.participanteDao().cantidad())
            assertEquals(1, database.consentimientoDao().listar().size)
        }

    private suspend fun crearSesion(
        participanteCodigo: String,
        sesionId: String,
        ultimaExportacionEn: Long?
    ): String {
        database.participanteDao().insertar(ParticipanteEntity(participanteCodigo, 5L))
        database.sesionDao().crearConCondiciones(
            SesionEntity(
                id = sesionId,
                participanteCodigo = participanteCodigo,
                consentimientoId = consentimientoId,
                protocoloVersion = "1.0.0",
                semilla = 20261005L,
                estado = EstadoSesion.COMPLETA,
                bloqueActual = 4,
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
                ultimoIntentoEn = 400L,
                ultimaExportacionEn = ultimaExportacionEn,
                intentosPerdidos = 0,
                revision = 0
            ),
            condiciones(sesionId)
        )
        return sesionId
    }

    private suspend fun registrarIntento(sesionId: String) {
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
                resultado = ResultadoIntento.SIN_RESULTADO,
                causaSinResultado = CausaSinResultado.SEGMENTO_CORTO,
                predichoIndice = null,
                predichoGlosa = null,
                confianza = null,
                umbral = 0.7f,
                superoUmbral = false,
                correcto = false,
                descartado = false,
                loHiceMal = false,
                inicioRelMs = 100L,
                duracionSegmentoMs = null
            ),
            top3 = emptyList(),
            momento = 200L
        )
    }

    private suspend fun insertarEnvio(sesionId: String, envioId: String) {
        database.envioDao().insertar(
            EnvioEntity(
                envioId = envioId,
                sesionId = sesionId,
                revision = 1,
                payload = byteArrayOf(1, 2),
                sha256 = HASH,
                estado = EstadoEnvio.PENDIENTE,
                motivo = null,
                intentos = 0,
                creadoEn = 300L,
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
        const val SESION_1 = "6f1d90ab-a125-4ab2-842c-05205b62db38"
        const val SESION_2 = "6f1d90ab-a125-4ab2-842c-05205b62db39"
        const val ENVIO_PENDIENTE = "2f1d90ab-a125-4ab2-842c-05205b62db38"
    }
}
