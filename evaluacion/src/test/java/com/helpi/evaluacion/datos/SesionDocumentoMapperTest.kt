package com.helpi.evaluacion.datos

import androidx.room.Room
import com.helpi.evaluacion.contrato.SesionContratoSerializer
import com.helpi.evaluacion.datos.entidades.CausaInterrupcion
import com.helpi.evaluacion.datos.entidades.CausaSinResultado
import com.helpi.evaluacion.datos.entidades.CondicionesPruebaEntity
import com.helpi.evaluacion.datos.entidades.ConsentimientoEntity
import com.helpi.evaluacion.datos.entidades.Distancia
import com.helpi.evaluacion.datos.entidades.Entorno
import com.helpi.evaluacion.datos.entidades.EstadoSesion
import com.helpi.evaluacion.datos.entidades.Iluminacion
import com.helpi.evaluacion.datos.entidades.IntentoEntity
import com.helpi.evaluacion.datos.entidades.InterrupcionEntity
import com.helpi.evaluacion.datos.entidades.ManoDominante
import com.helpi.evaluacion.datos.entidades.ParticipanteEntity
import com.helpi.evaluacion.datos.entidades.PerfilParticipante
import com.helpi.evaluacion.datos.entidades.PrediccionTopEntity
import com.helpi.evaluacion.datos.entidades.ResultadoIntento
import com.helpi.evaluacion.datos.entidades.SesionEntity
import com.helpi.evaluacion.datos.entidades.SoporteCamara
import com.helpi.evaluacion.datos.entidades.TipoEntorno
import com.helpi.evaluacion.dominio.protocolo.GeneradorProtocolo
import com.networknt.schema.SchemaRegistry
import com.networknt.schema.SpecificationVersion
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.UUID
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
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SesionDocumentoMapperTest {
    private lateinit var database: HelpiEvaluacionDatabase
    private val jsonMapper = ObjectMapper()

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
    fun mapeaRoomCumpleReglasS02AS08YElEsquema() = runBlocking {
        val semilla = 20261005L
        val sesionId = "a7c4e2d9-5b13-4f8a-9e26-1d0b3c8f7a45"
        val secuencia = GeneradorProtocolo.generar(semilla).secuencia
        val consentimientoId = database.consentimientoDao().insertar(
            ConsentimientoEntity(
                avisoVersion = "1",
                avisoVideoSha256 = "a".repeat(64),
                otorgadoEn = 1_791_213_123_000L,
                revocadoEn = null
            )
        )
        database.participanteDao().insertar(ParticipanteEntity("P-007", 1_791_213_123_000L))
        database.sesionDao().crearConCondiciones(
            sesion(consentimientoId = consentimientoId, sesionId = sesionId),
            condiciones(sesionId)
        )
        insertarIntentos(sesionId, secuencia)
        database.sesionDao().insertarInterrupcion(
            InterrupcionEntity(
                sesionId = sesionId,
                bloque = 1,
                posicionPendiente = 5,
                causa = CausaInterrupcion.PROCESO_TERMINADO,
                ocurrioEn = 1_791_213_220_000L
            )
        )

        val documento = SesionDocumentoMapper(database).mapear(
            sesionId = sesionId,
            envioId = UUID.fromString("3f2b8c1e-9a47-4d2e-8b61-0c5e7a9d4f12"),
            generadoEn = Instant.parse("2026-10-05T15:20:07Z")
        )
        val serializado = SesionContratoSerializer().serialize(documento)
        val json = jsonMapper.readTree(String(serializado, StandardCharsets.UTF_8))
        val schema = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
            .getSchema(recurso("envio-sesion-evaluacion.schema.json"))

        assertEquals(1, documento.envio.revision)
        assertEquals(secuencia, documento.protocolo.secuencia)
        assertEquals(5, documento.intentos.size)
        assertTrue(schema.validate(json).toString(), schema.validate(json).isEmpty())
        validarReglasSemanticas(json)
    }

    private suspend fun insertarIntentos(sesionId: String, secuencia: List<Int>) {
        val intentos = database.intentoDao()
        for (posicion in 1..4) {
            if (posicion == 4) {
                guardarIntento(
                    intentos,
                    intento(
                        sesionId = sesionId,
                        posicion = posicion,
                        numeroIntento = 1,
                        reemplazado = true,
                        esperado = secuencia[posicion - 1],
                        resultado = ResultadoIntento.SIN_RESULTADO,
                        causa = CausaSinResultado.SIN_HOMBROS
                    ),
                    emptyList()
                )
                guardarClasificado(
                    intentos,
                    sesionId,
                    posicion,
                    2,
                    secuencia[posicion - 1],
                    ResultadoIntento.SOBRE_UMBRAL,
                    superoUmbral = true,
                    descartado = false,
                    vioVideo = true
                )
            } else {
                val resultado = when (posicion) {
                    2 -> ResultadoIntento.SOBRE_UMBRAL
                    3 -> ResultadoIntento.BAJO_UMBRAL
                    else -> ResultadoIntento.SOBRE_UMBRAL
                }
                val indicePredicho = if (posicion == 2) {
                    (secuencia[posicion - 1] + 1) % 64
                } else {
                    secuencia[posicion - 1]
                }
                guardarClasificado(
                    intentos,
                    sesionId,
                    posicion,
                    1,
                    secuencia[posicion - 1],
                    resultado,
                    indicePredicho,
                    superoUmbral = posicion != 3,
                    descartado = posicion == 2
                )
            }
        }
    }

    @Suppress("LongParameterList")
    private suspend fun guardarClasificado(
        dao: IntentoDao,
        sesionId: String,
        posicion: Int,
        numeroIntento: Int,
        esperado: Int,
        resultado: ResultadoIntento,
        predicho: Int = esperado,
        superoUmbral: Boolean,
        descartado: Boolean,
        vioVideo: Boolean = false
    ) {
        val confianza = if (superoUmbral) 0.95f else 0.613027f
        val top = listOf(
            PrediccionTopEntity(0, 1, predicho, "G$predicho", confianza),
            PrediccionTopEntity(0, 2, (predicho + 1) % 64, "G${(predicho + 1) % 64}", 0.03f),
            PrediccionTopEntity(0, 3, (predicho + 2) % 64, "G${(predicho + 2) % 64}", 0.01f)
        )
        guardarIntento(
            dao,
            intento(
                sesionId = sesionId,
                posicion = posicion,
                numeroIntento = numeroIntento,
                reemplazado = false,
                esperado = esperado,
                resultado = resultado,
                predicho = predicho,
                confianza = confianza,
                superoUmbral = superoUmbral,
                correcto = resultado == ResultadoIntento.SOBRE_UMBRAL && predicho == esperado,
                descartado = descartado,
                vioVideo = vioVideo
            ),
            top
        )
    }

    private suspend fun guardarIntento(
        dao: IntentoDao,
        intento: IntentoEntity,
        top: List<PrediccionTopEntity>
    ) {
        val intentoId = dao.insertar(intento)
        if (top.isNotEmpty()) {
            dao.insertarPredicciones(top.map { it.copy(intentoId = intentoId) })
        }
    }

    @Suppress("LongParameterList")
    private fun intento(
        sesionId: String,
        posicion: Int,
        numeroIntento: Int,
        reemplazado: Boolean,
        esperado: Int,
        resultado: ResultadoIntento,
        causa: CausaSinResultado? = null,
        predicho: Int? = null,
        confianza: Float? = null,
        superoUmbral: Boolean = false,
        correcto: Boolean = false,
        descartado: Boolean = false,
        vioVideo: Boolean = false
    ) = IntentoEntity(
        sesionId = sesionId,
        posicion = posicion,
        bloque = (posicion + 47) / 48,
        numeroIntento = numeroIntento,
        reemplazado = reemplazado,
        senaEsperadaIndice = esperado,
        senaEsperadaGlosa = "G$esperado",
        vioVideo = vioVideo,
        resultado = resultado,
        causaSinResultado = causa,
        predichoIndice = predicho,
        predichoGlosa = predicho?.let { "G$it" },
        confianza = confianza,
        umbral = 0.9f,
        superoUmbral = superoUmbral,
        correcto = correcto,
        descartado = descartado,
        loHiceMal = false,
        inicioRelMs = posicion * 1_000L,
        duracionSegmentoMs = if (resultado == ResultadoIntento.SIN_RESULTADO) null else 1_500L
    )

    private fun sesion(consentimientoId: Long, sesionId: String): SesionEntity = SesionEntity(
        id = sesionId,
        participanteCodigo = "P-007",
        consentimientoId = consentimientoId,
        protocoloVersion = "1.0.0",
        semilla = 20261005L,
        estado = EstadoSesion.INTERRUMPIDA,
        bloqueActual = 1,
        appVersionName = "0.1.0",
        appVersionCode = 1,
        modeloVersion = "eva-lsa64-v3",
        modeloSha256 = "e231d3962e2eb8d081e9a95d61d6600a6b489db06db03f971530bc7da8f0d488",
        catalogoSha256 = "377a02e97bd57b0133962dbe961c853ca3a205d0f985eceb31b358ea323df412",
        contratoKeypoints = 3,
        dispositivoFabricante = "motorola",
        dispositivoModelo = "moto g54 5G",
        sdkAndroid = 34,
        creadaEn = 1_791_213_111_000L,
        ultimoIntentoEn = 1_791_213_180_000L,
        ultimaExportacionEn = null,
        intentosPerdidos = 0,
        revision = 0
    )

    private fun condiciones(sesionId: String) = CondicionesPruebaEntity(
        sesionId = sesionId,
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

    private fun validarReglasSemanticas(json: JsonNode) {
        val secuencia = json.path("protocolo").path("secuencia").arrayItems().map { it.asInt() }
        val ocurrencias = secuencia.groupingBy { it }.eachCount()
        assertEquals((0..63).toSet(), ocurrencias.keys)
        assertTrue(ocurrencias.values.all { it == 3 })
        assertTrue(secuencia.zipWithNext().all { (izquierda, derecha) -> izquierda != derecha })

        val intentos = json.path("intentos").arrayItems()
        intentos.forEach { intento ->
            val posicion = intento.path("posicion").asInt()
            assertEquals(
                secuencia[posicion - 1],
                intento.path("senaEsperada").path("indice").asInt()
            )
            assertEquals((posicion + 47) / 48, intento.path("bloque").asInt())

            if (intento.path("resultado").asText() != "SIN_RESULTADO") {
                val top3 = intento.path("top3").arrayItems()
                assertEquals(listOf(1, 2, 3), top3.map { it.path("rango").asInt() })
                val confidencias = top3.map { it.path("confianza").decimalValue() }
                assertTrue(
                    confidencias.zipWithNext().all { (actual, siguiente) ->
                        actual >= siguiente
                    }
                )

                val prediccion = intento.path("prediccion")
                assertEquals(top3[0].path("indice"), prediccion.path("indice"))
                assertEquals(top3[0].path("glosa"), prediccion.path("glosa"))
                assertEquals(top3[0].path("confianza"), prediccion.path("confianza"))

                val superoUmbral = prediccion.path("confianza").decimalValue() >=
                    intento.path("umbral").decimalValue()
                assertEquals(superoUmbral, intento.path("superoUmbral").asBoolean())
                assertEquals(superoUmbral, intento.path("resultado").asText() == "SOBRE_UMBRAL")
                assertEquals(
                    intento.path("resultado").asText() == "SOBRE_UMBRAL" &&
                        prediccion.path("indice") == intento.path("senaEsperada").path("indice"),
                    intento.path("correcto").asBoolean()
                )
            }
            if (intento.path("descartado").asBoolean()) {
                assertEquals("SOBRE_UMBRAL", intento.path("resultado").asText())
            }
        }

        intentos.groupBy { it.path("posicion").asInt() }.values.forEach { mismaPosicion ->
            val vigentes = mismaPosicion.filterNot { it.path("reemplazado").asBoolean() }
            assertTrue(vigentes.size <= 1)
            if (vigentes.isNotEmpty()) {
                assertEquals(
                    mismaPosicion.maxOf { it.path("numeroIntento").asInt() },
                    vigentes.single().path("numeroIntento").asInt()
                )
            }
        }
    }

    private fun recurso(path: String) =
        checkNotNull(javaClass.classLoader?.getResourceAsStream(path)) {
            "No se encontró el recurso $path"
        }

    private fun JsonNode.arrayItems(): List<JsonNode> = (0 until size()).map { get(it) }
}
