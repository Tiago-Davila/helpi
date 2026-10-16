package com.helpi.evaluacion.consentimiento

import androidx.room.Room
import com.helpi.evaluacion.datos.HelpiEvaluacionDatabase
import com.helpi.evaluacion.datos.entidades.ConsentimientoEntity
import java.io.File
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
    private lateinit var video: AvisoVideoAsset
    private lateinit var repositorio: ConsentimientoRepositorio

    @Before
    fun abrirBase() {
        val context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(
            context,
            HelpiEvaluacionDatabase::class.java
        ).allowMainThreadQueries().build()
        val archivo = File(context.cacheDir, "aviso-test.mp4").apply {
            writeBytes(byteArrayOf(0, 1, 2, 3, 4, 5))
        }
        video = AvisoVideoAsset(archivo, requireNotNull(AvisoVideoAssetLoader.hash(archivo)))
        repositorio = ConsentimientoRepositorio(database.consentimientoDao())
    }

    @After
    fun cerrarBase() {
        database.close()
        video.archivo.delete()
    }

    @Test
    fun aceptarGuardaHashVersionYFechaYPermiteLasOperacionesProtegidas() = runBlocking {
        assertNull(repositorio.obtenerVigente(video))

        val guardado = repositorio.aceptar(video, 1_000L)

        assertEquals(AVISO_VERSION_ACTUAL, guardado.avisoVersion)
        assertEquals(video.sha256, guardado.avisoVideoSha256)
        assertEquals(1_000L, guardado.otorgadoEn)
        assertEquals(guardado, repositorio.exigirVigente(video))
    }

    @Test
    fun consentimientoAnteriorRevocadoOConHashDistintoNoEstaVigente() = runBlocking {
        val id = repositorio.aceptar(video, 1_000L).id
        val videoModificado = video.copy(sha256 = "b".repeat(64))
        assertNull(repositorio.obtenerVigente(videoModificado))
        database.consentimientoDao().revocar(id, 2_000L)
        assertNull(repositorio.obtenerVigente(video))
        assertThrows(ConsentimientoNoVigenteException::class.java) {
            runBlocking { repositorio.exigirVigente(video) }
        }
        Unit
    }

    @Test
    fun consentimientoDeVersionAnteriorNoHabilitaElRegistro() = runBlocking {
        database.consentimientoDao().insertar(
            ConsentimientoEntity(
                avisoVersion = "0",
                avisoVideoSha256 = video.sha256,
                otorgadoEn = 1_000L,
                revocadoEn = null
            )
        )

        assertNull(repositorio.obtenerVigente(video))
        assertThrows(ConsentimientoNoVigenteException::class.java) {
            runBlocking { repositorio.exigirVigente(video) }
        }
        Unit
    }

    @Test
    fun cambioDelArchivoInvalidaElHashPresentado() = runBlocking {
        repositorio.aceptar(video, 1_000L)
        video.archivo.writeBytes("reemplazado".toByteArray())

        assertNull(repositorio.obtenerVigente(video))
        assertThrows(ConsentimientoNoVigenteException::class.java) {
            runBlocking { repositorio.exigirVigente(video) }
        }
        Unit
    }

    @Test
    fun hashDeFixtureEsSha256Hexadecimal() {
        assertEquals(64, video.sha256.length)
        assertEquals(byteArrayOf(0, 1, 2, 3, 4, 5).sha256Hex(), video.sha256)
    }

    private fun ByteArray.sha256Hex(): String = MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString("") { "%02x".format(Locale.ROOT, it.toInt() and 0xff) }
}
