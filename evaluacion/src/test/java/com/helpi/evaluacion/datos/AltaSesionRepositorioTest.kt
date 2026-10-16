package com.helpi.evaluacion.datos

import androidx.room.Room
import com.helpi.evaluacion.consentimiento.AVISO_VERSION_ACTUAL
import com.helpi.evaluacion.consentimiento.ConsentimientoNoVigenteException
import com.helpi.evaluacion.datos.entidades.ConsentimientoEntity
import com.helpi.evaluacion.datos.entidades.Distancia
import com.helpi.evaluacion.datos.entidades.Entorno
import com.helpi.evaluacion.datos.entidades.EstadoSesion
import com.helpi.evaluacion.datos.entidades.Iluminacion
import com.helpi.evaluacion.datos.entidades.ManoDominante
import com.helpi.evaluacion.datos.entidades.PerfilParticipante
import com.helpi.evaluacion.datos.entidades.SoporteCamara
import com.helpi.evaluacion.datos.entidades.TipoEntorno
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AltaSesionRepositorioTest {
    private lateinit var database: HelpiEvaluacionDatabase
    private val ahora = 1_791_213_907_000L
    private val sesionId = "3f2b8c1e-9a47-4d2e-8b61-0c5e7a9d4f12"

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
    fun persisteParticipanteSesionCreadaYCondicionesEnUnaTransaccion() = runBlocking {
        val consentimientoId = insertarConsentimientoVigente()

        val creada = repositorio().crear("P-007", condiciones())

        assertEquals(sesionId, creada)
        assertEquals(1, database.participanteDao().cantidad())
        assertEquals(1, database.sesionDao().cantidadSesiones())
        val sesion = requireNotNull(database.sesionDao().buscar(sesionId))
        assertEquals(consentimientoId, sesion.consentimientoId)
        assertEquals(EstadoSesion.CREADA, sesion.estado)
        assertEquals(1, sesion.bloqueActual)
        assertEquals(20261005L, sesion.semilla)
        assertEquals("0.1.0-evaluacion", sesion.appVersionName)
        assertEquals(7, sesion.appVersionCode)
        assertEquals("eva-lsa64-v3", sesion.modeloVersion)
        assertEquals("a".repeat(64), sesion.modeloSha256)
        assertEquals("b".repeat(64), sesion.catalogoSha256)
        assertEquals(3, sesion.contratoKeypoints)
        assertEquals("motorola", sesion.dispositivoFabricante)
        assertEquals("moto g54 5G", sesion.dispositivoModelo)
        assertEquals(34, sesion.sdkAndroid)
        assertEquals(ahora, sesion.creadaEn)
        assertEquals(condiciones().aEntidad(sesionId), database.sesionDao().condiciones(sesionId))
    }

    @Test
    fun rechazaCrearSesionSinConsentimientoVigente() = runBlocking {
        assertThrows(ConsentimientoNoVigenteException::class.java) {
            runBlocking { repositorio().crear("P-007", condiciones()) }
        }

        assertEquals(0, database.participanteDao().cantidad())
        assertEquals(0, database.sesionDao().cantidadSesiones())
    }

    @Test
    fun reutilizaElParticipanteAlCrearUnaSegundaSesion() = runBlocking {
        insertarConsentimientoVigente()
        repositorio().crear("P-007", condiciones())

        val segunda = repositorio(
            crearSesionId = { "a7c4e2d9-5b13-4f8a-9e26-1d0b3c8f7a45" },
            generarSemilla = { 20261006L }
        ).crear("P-007", condiciones())

        assertEquals("a7c4e2d9-5b13-4f8a-9e26-1d0b3c8f7a45", segunda)
        assertEquals(1, database.participanteDao().cantidad())
        assertEquals(2, database.sesionDao().cantidadSesiones())
    }

    private suspend fun insertarConsentimientoVigente(): Long =
        database.consentimientoDao().insertar(
            ConsentimientoEntity(
                avisoVersion = AVISO_VERSION_ACTUAL,
                avisoVideoSha256 = "c".repeat(64),
                otorgadoEn = ahora - 1_000,
                revocadoEn = null
            )
        )

    private fun repositorio(
        crearSesionId: () -> String = { sesionId },
        generarSemilla: () -> Long = { 20261005L }
    ) = AltaSesionRepositorio(
        database = database,
        metadatos = MetadatosAltaSesion(
            appVersionName = "0.1.0-evaluacion",
            appVersionCode = 7,
            modeloVersion = "eva-lsa64-v3",
            modeloSha256 = "a".repeat(64),
            catalogoSha256 = "b".repeat(64),
            contratoKeypoints = 3,
            fabricante = "motorola",
            modeloDispositivo = "moto g54 5G",
            sdkAndroid = 34
        ),
        reloj = { ahora },
        generarSemilla = generarSemilla,
        crearSesionId = crearSesionId
    )

    private fun condiciones() = CondicionesSesion(
        entorno = Entorno.INTERIOR,
        tipoEntorno = TipoEntorno.AULA,
        iluminacion = Iluminacion.MEDIA,
        contraluz = false,
        distancia = Distancia.ENTRE_1_Y_2M,
        manoDominante = ManoDominante.DIESTRA,
        guantes = false,
        soporteCamara = SoporteCamara.TRIPODE,
        perfilParticipante = PerfilParticipante.SORDA_SENANTE
    )
}
