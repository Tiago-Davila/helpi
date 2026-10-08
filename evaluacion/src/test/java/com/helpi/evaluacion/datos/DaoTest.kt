package com.helpi.evaluacion.datos

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import com.helpi.evaluacion.consentimiento.AVISO_VERSION_ACTUAL
import com.helpi.evaluacion.datos.entidades.CausaInterrupcion
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
import com.helpi.evaluacion.datos.entidades.InterrupcionEntity
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
import org.junit.Assert.assertNotNull
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
class DaoTest {
    private lateinit var database: HelpiEvaluacionDatabase
    private lateinit var sesiones: SesionDao
    private lateinit var intentos: IntentoDao
    private lateinit var consentimientos: ConsentimientoDao
    private lateinit var participantes: ParticipanteDao
    private lateinit var envios: EnvioDao

    @Before
    fun abrirBase() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            HelpiEvaluacionDatabase::class.java
        ).allowMainThreadQueries().build()
        sesiones = database.sesionDao()
        intentos = database.intentoDao()
        consentimientos = database.consentimientoDao()
        participantes = database.participanteDao()
        envios = database.envioDao()
    }

    @After
    fun cerrarBase() {
        database.close()
    }

    @Test
    fun borrarParticipanteEliminaSusDatosEnCascada() = runBlocking {
        val sesionId = crearSesion()
        intentos.registrarIntento(intentoSinResultado(sesionId), emptyList(), 500L)
        sesiones.insertarInterrupcion(
            InterrupcionEntity(
                sesionId = sesionId,
                bloque = 1,
                posicionPendiente = 1,
                causa = CausaInterrupcion.PROCESO_TERMINADO,
                ocurrioEn = 600L
            )
        )
        envios.insertar(
            EnvioEntity(
                envioId = uuid(3),
                sesionId = sesionId,
                revision = 1,
                payload = byteArrayOf(1, 2, 3),
                sha256 = HASH,
                estado = EstadoEnvio.PENDIENTE,
                motivo = null,
                intentos = 0,
                creadoEn = 700L,
                ultimoIntentoEn = null
            )
        )

        assertEquals(1, participantes.eliminar(ParticipanteEntity("P-007", 1L)))

        assertTrue(sesiones.listarDeParticipante("P-007").isEmpty())
        assertEquals(0, sesiones.cantidadCondiciones(sesionId))
        assertEquals(0, sesiones.cantidadInterrupciones(sesionId))
        assertEquals(0, intentos.cantidadDeSesion(sesionId))
        assertTrue(envios.listarDeSesion(sesionId).isEmpty())
        assertEquals(1, consentimientos.listar().size)
    }

    @Test
    fun indiceUnicoRechazaMismaPosicionYNumeroDeIntento() = runBlocking {
        val sesionId = crearSesion()
        val intento = intentoSinResultado(sesionId)

        intentos.insertar(intento)
        val error = runCatching { intentos.insertar(intento) }.exceptionOrNull()

        assertNotNull(error)
        assertTrue(error is SQLiteConstraintException)
    }

    @Test
    fun noIniciaUnaSegundaSesionEnCurso() = runBlocking {
        val primera = crearSesion("P-007", uuid(1))
        val segunda = crearSesion("P-007", uuid(2))

        assertTrue(sesiones.iniciarBloque(primera, 1, AVISO_VERSION_ACTUAL, HASH))
        assertFalse(sesiones.iniciarBloque(segunda, 1, AVISO_VERSION_ACTUAL, HASH))
        assertEquals(EstadoSesion.EN_CURSO, sesiones.estado(primera))
        assertEquals(EstadoSesion.CREADA, sesiones.estado(segunda))
    }

    @Test
    fun iniciarBloqueExigeElConsentimientoMasRecienteConVersionYHashActuales() = runBlocking {
        val sesionId = crearSesion()
        val consentimientoId = requireNotNull(sesiones.buscar(sesionId)).consentimientoId

        assertFalse(
            sesiones.iniciarBloque(sesionId, 1, AVISO_VERSION_ACTUAL, "b".repeat(64))
        )
        consentimientos.revocar(consentimientoId, 3L)
        assertFalse(sesiones.iniciarBloque(sesionId, 1, AVISO_VERSION_ACTUAL, HASH))
        assertEquals(EstadoSesion.CREADA, sesiones.estado(sesionId))
    }

    @Test
    fun pausaEntreBloquesPersisteElBloqueYLaPosicionPendienteAtomicos() = runBlocking {
        val sesionId = crearSesion()
        assertTrue(sesiones.iniciarBloque(sesionId, 1, AVISO_VERSION_ACTUAL, HASH))
        val interrupcion = InterrupcionEntity(
            sesionId = sesionId,
            bloque = 2,
            posicionPendiente = 49,
            causa = CausaInterrupcion.PERSONA_PAUSO_DIA,
            ocurrioEn = 900L
        )

        assertTrue(sesiones.pausarSiEnCurso(interrupcion))

        assertEquals(EstadoSesion.PAUSADA, sesiones.estado(sesionId))
        assertEquals(2, sesiones.buscar(sesionId)?.bloqueActual)
        assertEquals(listOf(interrupcion.copy(id = 1L)), sesiones.interrupciones(sesionId))
        assertFalse(sesiones.pausarSiEnCurso(interrupcion))
        assertEquals(1, sesiones.cantidadInterrupciones(sesionId))
    }

    @Test
    fun completaYTerminadaAntesSoloCambianDesdeEnCurso() = runBlocking {
        val completa = crearSesion(sesionId = uuid(1))
        assertTrue(sesiones.iniciarBloque(completa, 1, AVISO_VERSION_ACTUAL, HASH))
        assertEquals(1, sesiones.marcarCompletaSiEnCurso(completa))

        val terminada = crearSesion(sesionId = uuid(2))
        assertTrue(sesiones.iniciarBloque(terminada, 1, AVISO_VERSION_ACTUAL, HASH))
        assertEquals(1, sesiones.marcarTerminadaAntesSiEnCurso(terminada))

        assertEquals(EstadoSesion.COMPLETA, sesiones.estado(completa))
        assertEquals(EstadoSesion.TERMINADA_ANTES, sesiones.estado(terminada))
        assertEquals(0, sesiones.marcarTerminadaAntesSiEnCurso(completa))
    }

    @Test
    fun exportacionActualizaRevisionYFechaEnUnaOperacionOptimista() = runBlocking {
        val sesionId = crearSesion()

        assertEquals(
            1,
            sesiones.actualizarExportacion(sesionId, revision = 1, exportadoEn = 1_200L)
        )
        assertEquals(1, sesiones.buscar(sesionId)?.revision)
        assertEquals(1_200L, sesiones.buscar(sesionId)?.ultimaExportacionEn)
        assertEquals(
            0,
            sesiones.actualizarExportacion(sesionId, revision = 3, exportadoEn = 2_400L)
        )
        assertEquals(1, sesiones.buscar(sesionId)?.revision)
        assertEquals(1_200L, sesiones.buscar(sesionId)?.ultimaExportacionEn)
    }

    @Test
    fun repeticionReemplazaElAnteriorYTop3QuedaCompletoOrdenado() = runBlocking {
        val sesionId = crearSesion()
        val primeroId = intentos.registrarIntento(intentoSinResultado(sesionId), emptyList(), 500L)
        val segundo = intentoClasificado(sesionId, numeroIntento = 2)
        val segundoId = intentos.registrarIntento(
            segundo,
            top3Desordenado(),
            800L
        )

        val guardados = intentos.listarDeSesion(sesionId)
        val activos = guardados.filterNot(IntentoEntity::reemplazado)
        val top3 = intentos.predicciones(segundoId)

        assertEquals(2, guardados.size)
        assertTrue(guardados.first { it.id == primeroId }.reemplazado)
        assertEquals(listOf(2), activos.map(IntentoEntity::numeroIntento))
        assertEquals(guardados.maxOf(IntentoEntity::numeroIntento), activos.single().numeroIntento)
        assertEquals(listOf(1, 2, 3), top3.map { it.rango })
        assertEquals(segundo.predichoIndice, top3.first().indice)
        assertEquals(segundo.predichoGlosa, top3.first().glosa)
        assertEquals(segundo.confianza, top3.first().confianza)
        assertEquals(800L, sesiones.buscar(sesionId)?.ultimoIntentoEn)
    }

    @Test
    fun sinResultadoNoGuardaFilasTop3() = runBlocking {
        val sesionId = crearSesion()
        val intentoId = intentos.registrarIntento(intentoSinResultado(sesionId), emptyList(), 900L)

        assertEquals(0, intentos.cantidadPredicciones(intentoId))
        assertTrue(intentos.predicciones(intentoId).isEmpty())
        assertNull(intentos.buscar(intentoId)?.predichoIndice)
    }

    @Test
    fun sumaIntentosPerdidosEnLaTransaccionDelSiguienteIntento() = runBlocking {
        val sesionId = crearSesion()
        val primero = intentoSinResultado(sesionId)
        intentos.registrarIntento(primero, emptyList(), 900L, intentosPerdidos = 2)
        intentos.registrarIntento(
            primero.copy(numeroIntento = 2, inicioRelMs = 1_000L),
            emptyList(),
            1_000L,
            intentosPerdidos = 3
        )

        assertEquals(5, sesiones.buscar(sesionId)?.intentosPerdidos)
    }

    private suspend fun crearSesion(
        codigo: String = "P-007",
        sesionId: String = uuid(1),
        estado: EstadoSesion = EstadoSesion.CREADA
    ): String {
        if (participantes.buscar(codigo) == null) {
            participantes.insertar(ParticipanteEntity(codigo = codigo, creadoEn = 1L))
        }
        val consentimientoId = consentimientos.insertar(
            ConsentimientoEntity(
                avisoVersion = AVISO_VERSION_ACTUAL,
                avisoVideoSha256 = HASH,
                otorgadoEn = 2L,
                revocadoEn = null
            )
        )
        val sesion = SesionEntity(
            id = sesionId,
            participanteCodigo = codigo,
            consentimientoId = consentimientoId,
            protocoloVersion = "1.0.0",
            semilla = 20261005L,
            estado = estado,
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
            creadaEn = 100L,
            ultimoIntentoEn = null,
            ultimaExportacionEn = null,
            intentosPerdidos = 0,
            revision = 0
        )
        sesiones.crearConCondiciones(sesion, condiciones(sesionId))
        return sesionId
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

    private fun intentoSinResultado(sesionId: String) = IntentoEntity(
        sesionId = sesionId,
        posicion = 1,
        bloque = 1,
        numeroIntento = 1,
        reemplazado = false,
        senaEsperadaIndice = 7,
        senaEsperadaGlosa = "CASA",
        vioVideo = false,
        resultado = ResultadoIntento.SIN_RESULTADO,
        causaSinResultado = CausaSinResultado.SIN_HOMBROS,
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
    )

    private fun intentoClasificado(sesionId: String, numeroIntento: Int) = IntentoEntity(
        sesionId = sesionId,
        posicion = 1,
        bloque = 1,
        numeroIntento = numeroIntento,
        reemplazado = false,
        senaEsperadaIndice = 7,
        senaEsperadaGlosa = "CASA",
        vioVideo = false,
        resultado = ResultadoIntento.SOBRE_UMBRAL,
        causaSinResultado = null,
        predichoIndice = 7,
        predichoGlosa = "CASA",
        confianza = 0.9f,
        umbral = 0.7f,
        superoUmbral = true,
        correcto = true,
        descartado = false,
        loHiceMal = false,
        inicioRelMs = 600L,
        duracionSegmentoMs = 1_000L
    )

    private fun top3Desordenado() = listOf(
        PrediccionTopDato(rango = 3, indice = 9, glosa = "AGUA", confianza = 0.1f),
        PrediccionTopDato(rango = 1, indice = 7, glosa = "CASA", confianza = 0.9f),
        PrediccionTopDato(rango = 2, indice = 8, glosa = "COMER", confianza = 0.4f)
    )

    private fun uuid(sufijo: Int) = "00000000-0000-4000-8000-${sufijo.toString().padStart(12, '0')}"

    private companion object {
        const val HASH = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    }
}
