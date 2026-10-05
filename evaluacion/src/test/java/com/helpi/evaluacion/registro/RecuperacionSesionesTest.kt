package com.helpi.evaluacion.registro

import androidx.room.Room
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
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RecuperacionSesionesTest {
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
    fun alArrancarInterrumpeLaSesionYConservaDatosConLaPrimeraPosicionPendiente() = runBlocking {
        val sesionId = crearSesionEnCurso()
        registrarIntentoRespondido(sesionId, posicion = 1)

        RecuperacionSesiones(database, reloj = { 900L }).alArrancar()

        val sesion = requireNotNull(database.sesionDao().buscar(sesionId))
        val interrupcion = database.sesionDao().interrupciones(sesionId).single()
        assertEquals(EstadoSesion.INTERRUMPIDA, sesion.estado)
        assertEquals("P-007", sesion.participanteCodigo)
        assertEquals(20261005L, sesion.semilla)
        assertEquals(1, interrupcion.bloque)
        assertEquals(2, interrupcion.posicionPendiente)
        assertEquals(CausaInterrupcion.PROCESO_TERMINADO, interrupcion.causa)
        assertEquals(900L, interrupcion.ocurrioEn)
        assertEquals(1, database.intentoDao().cantidadDeSesion(sesionId))
        assertEquals(condiciones(), database.sesionDao().condiciones(sesionId))
    }

    @Test
    fun alPasarAonStopRegistraSegundoPlanoUnaSolaVez() = runBlocking {
        val sesionId = crearSesionEnCurso()
        registrarIntentoRespondido(sesionId, posicion = 1)
        val recuperacion = RecuperacionSesiones(database, reloj = { 1_000L })

        recuperacion.alPasarSegundoPlano()
        recuperacion.alPasarSegundoPlano()

        val interrupcion = database.sesionDao().interrupciones(sesionId).single()
        assertEquals(EstadoSesion.INTERRUMPIDA, database.sesionDao().estado(sesionId))
        assertEquals(CausaInterrupcion.SEGUNDO_PLANO, interrupcion.causa)
        assertEquals(2, interrupcion.posicionPendiente)
    }

    private suspend fun crearSesionEnCurso(): String {
        val dao = database.sesionDao()
        database.participanteDao().insertar(ParticipanteEntity("P-007", creadoEn = 10L))
        val consentimientoId = database.consentimientoDao().insertar(
            ConsentimientoEntity(
                avisoVersion = "1.0",
                avisoVideoSha256 = HASH,
                otorgadoEn = 20L,
                revocadoEn = null
            )
        )
        val sesionId = UUID_SESION
        dao.crearConCondiciones(
            SesionEntity(
                id = sesionId,
                participanteCodigo = "P-007",
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
            condiciones()
        )
        check(dao.iniciarBloque(sesionId, 1))
        return sesionId
    }

    private suspend fun registrarIntentoRespondido(sesionId: String, posicion: Int) {
        database.intentoDao().registrarIntento(
            intento = IntentoEntity(
                sesionId = sesionId,
                posicion = posicion,
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

    private fun condiciones() = CondicionesPruebaEntity(
        sesionId = UUID_SESION,
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
        const val UUID_SESION = "6f1d90ab-a125-4ab2-842c-05205b62db38"
        const val HASH = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    }
}
