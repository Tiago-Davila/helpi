package com.helpi.evaluacion.consentimiento

import androidx.room.Room
import com.helpi.evaluacion.datos.HelpiEvaluacionDatabase
import com.helpi.evaluacion.datos.entidades.ConsentimientoEntity
import java.security.MessageDigest
import java.util.Locale
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ConsentimientoRepositorioTest {
    private lateinit var database: HelpiEvaluacionDatabase
    private lateinit var repositorio: ConsentimientoRepositorio

    @Before
    fun abrirBase() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            HelpiEvaluacionDatabase::class.java
        ).allowMainThreadQueries().build()
        repositorio = ConsentimientoRepositorio(database.consentimientoDao())
    }

    @After
    fun cerrarBase() {
        database.close()
    }

    @Test
    fun sinAceptarNoHayConsentimiento() = runBlocking {
        assertNull(repositorio.obtenerVigente())
        assertThrows(ConsentimientoNoVigenteException::class.java) {
            runBlocking { repositorio.exigirVigente() }
        }
        Unit
    }

    @Test
    fun aceptarGuardaVersionFechaYQueNoHuboVideo() = runBlocking {
        val guardado = repositorio.aceptar(1_000L)

        assertEquals(AVISO_VERSION_ACTUAL, guardado.avisoVersion)
        assertEquals(AVISO_SIN_VIDEO_SHA256, guardado.avisoVideoSha256)
        assertEquals(1_000L, guardado.otorgadoEn)
        assertEquals(guardado, repositorio.exigirVigente())
    }

    @Test
    fun consentimientoRevocadoNoEstaVigente() = runBlocking {
        val id = repositorio.aceptar(1_000L).id
        database.consentimientoDao().revocar(id, 2_000L)

        assertNull(repositorio.obtenerVigente())
        assertThrows(ConsentimientoNoVigenteException::class.java) {
            runBlocking { repositorio.exigirVigente() }
        }
        Unit
    }

    @Test
    fun consentimientoDeVersionAnteriorNoHabilitaElRegistro() = runBlocking {
        database.consentimientoDao().insertar(
            ConsentimientoEntity(
                avisoVersion = "1",
                avisoVideoSha256 = "a".repeat(64),
                otorgadoEn = 1_000L,
                revocadoEn = null
            )
        )

        assertNull(repositorio.obtenerVigente())
        assertThrows(ConsentimientoNoVigenteException::class.java) {
            runBlocking { repositorio.exigirVigente() }
        }
        Unit
    }

    @Test
    fun laMarcaSinVideoEsElHashDeCeroBytes() {
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(ByteArray(0))
            .joinToString("") { "%02x".format(Locale.ROOT, it.toInt() and 0xff) }
        assertEquals(hash, AVISO_SIN_VIDEO_SHA256)
    }
}
