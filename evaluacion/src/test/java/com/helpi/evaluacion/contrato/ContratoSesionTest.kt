package com.helpi.evaluacion.contrato

import com.networknt.schema.Schema
import com.networknt.schema.SchemaRegistry
import com.networknt.schema.SpecificationVersion
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.node.ObjectNode

@RunWith(RobolectricTestRunner::class)
class ContratoSesionTest {
    private val mapper = ObjectMapper()
    private lateinit var schema: Schema
    private lateinit var example: JsonNode

    @Before
    fun setUp() {
        schema = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
            .getSchema(resource("envio-sesion-evaluacion.schema.json"))
        example = mapper.readTree(resource("ejemplos/sesion-evaluacion-v1.ejemplo.json"))
    }

    @Test
    fun hashesDelContratoCoincidenConLosArchivosCanonicos() {
        val entries = resource("CONTRATO_SHA256")
            .bufferedReader(StandardCharsets.UTF_8)
            .useLines { lines ->
                lines.filter { it.isNotBlank() }
                    .map { line ->
                        val parts = line.trim().split(Regex("\\s+"), limit = 2)
                        parts[0] to parts[1].removePrefix("./")
                    }
                    .toList()
            }

        assertTrue("CONTRATO_SHA256 no debe estar vacío", entries.isNotEmpty())
        entries.forEach { (expected, path) ->
            val actual = MessageDigest.getInstance("SHA-256")
                .digest(resource(path).readBytes())
                .joinToString("") { byte -> "%02x".format(byte) }
            assertEquals("Hash incorrecto para $path", expected, actual)
        }
    }

    @Test
    fun ejemploValidaContraElEsquema() {
        assertTrue(schema.validate(example).toString(), schema.validate(example).isEmpty())
    }

    @Test
    fun cadaEjemploNegativoEsRechazado() {
        val directory = checkNotNull(javaClass.classLoader?.getResource("ejemplos/negativos"))
        val paths = Files.list(Paths.get(directory.toURI())).use { files ->
            files.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".json") }
                .map { "ejemplos/negativos/${it.fileName}" }
                .sorted()
                .toList()
        }

        assertEquals("La colección normativa debe tener nueve negativos", 9, paths.size)
        paths.forEach { path ->
            val errors = schema.validate(mapper.readTree(resource(path)))
            assertFalse("$path debería ser rechazado", errors.isEmpty())
        }
    }

    @Test
    fun esquemaPermiteMasDeMilIntentosYQuinientasInterrupciones() {
        val expanded = example.deepCopy() as ObjectNode
        val attempts = expanded.withArray("intentos")
        val firstAttempt = attempts[0].deepCopy()
        while (attempts.size() <= 1_000) attempts.add(firstAttempt.deepCopy())

        val interruptions = expanded.withArray("interrupciones")
        val firstInterruption = interruptions[0].deepCopy()
        while (interruptions.size() <=
            500
        ) {
            interruptions.add(firstInterruption.deepCopy())
        }

        assertEquals(1_001, attempts.size())
        assertEquals(501, interruptions.size())
        val errors = schema.validate(expanded)
        assertTrue(errors.toString(), errors.isEmpty())
    }

    @Test
    fun reglasSemanticasS02AS08SeCumplenEnElEjemplo() {
        val sequence = example.path("protocolo").path("secuencia").arrayItems().map { it.asInt() }
        val occurrences = sequence.groupingBy { it }.eachCount()
        assertEquals((0..63).toSet(), occurrences.keys)
        assertTrue(occurrences.values.all { it == 3 })
        assertTrue(sequence.zipWithNext().all { (left, right) -> left != right })

        val attempts = example.path("intentos").arrayItems()
        attempts.forEach { attempt ->
            val position = attempt.path("posicion").asInt()
            assertEquals(
                sequence[position - 1],
                attempt.path("senaEsperada").path("indice").asInt()
            )
            assertEquals((position + 47) / 48, attempt.path("bloque").asInt())

            if (attempt.path("resultado").asText() != "SIN_RESULTADO") {
                val topItems = attempt.path("top3").arrayItems()
                assertEquals(listOf(1, 2, 3), topItems.map { it.path("rango").asInt() })
                val confidences = topItems.map { it.path("confianza").decimalValue() }
                assertTrue(confidences.zipWithNext().all { (left, right) -> left >= right })

                val prediction = attempt.path("prediccion")
                assertEquals(topItems[0].path("indice"), prediction.path("indice"))
                assertEquals(topItems[0].path("glosa"), prediction.path("glosa"))
                assertEquals(topItems[0].path("confianza"), prediction.path("confianza"))

                val superoUmbral = prediction.path("confianza").decimalValue() >=
                    attempt.path("umbral").decimalValue()
                assertEquals(superoUmbral, attempt.path("superoUmbral").asBoolean())
                assertEquals(superoUmbral, attempt.path("resultado").asText() == "SOBRE_UMBRAL")
                assertEquals(
                    attempt.path("resultado").asText() == "SOBRE_UMBRAL" &&
                        prediction.path("indice") == attempt.path("senaEsperada").path("indice"),
                    attempt.path("correcto").asBoolean()
                )
            }

            if (attempt.path("descartado").asBoolean()) {
                assertEquals("SOBRE_UMBRAL", attempt.path("resultado").asText())
            }
        }

        attempts.groupBy { it.path("posicion").asInt() }.values.forEach { samePosition ->
            val unreplaced = samePosition.filterNot { it.path("reemplazado").asBoolean() }
            assertTrue("Hay más de un intento vigente para una posición", unreplaced.size <= 1)
            if (unreplaced.isNotEmpty()) {
                val highestAttemptNumber = samePosition.maxOf { it.path("numeroIntento").asInt() }
                assertEquals(
                    highestAttemptNumber,
                    unreplaced.single().path("numeroIntento").asInt()
                )
            }
        }
    }

    @Test
    fun serializacionDeSesionFijaCoincideSemanticaConElEjemplo() {
        val sequence = example.path("protocolo").path("secuencia").arrayItems().map { it.asInt() }
        val bytes = SesionContratoSerializer().serialize(fixedDocument(sequence))
        assertFalse("La salida no debe comenzar con BOM UTF-8", bytes.startsWithUtf8Bom())
        val serialized = mapper.readTree(String(bytes, StandardCharsets.UTF_8))
        assertEquals(example, serialized)
    }

    @Test
    fun confianzasSeRedondeanASeisDecimalesConHalfEven() {
        val sequence = example.path("protocolo").path("secuencia").arrayItems().map { it.asInt() }
        val fixed = fixedDocument(sequence)
        val firstAttempt = fixed.intentos.first()
        val tiedPrediction = firstAttempt.prediccion!!.copy(confianza = 0.1234565)
        val tiedTop3 = firstAttempt.top3.mapIndexed { index, top ->
            when (index) {
                0 -> top.copy(confianza = 0.1234565)
                1 -> top.copy(confianza = 0.1234575)
                else -> top
            }
        }
        val serializedDocument = fixed.copy(
            intentos = listOf(firstAttempt.copy(prediccion = tiedPrediction, top3 = tiedTop3)) +
                fixed.intentos.drop(1)
        )
        val actual = mapper.readTree(
            String(
                SesionContratoSerializer().serialize(serializedDocument),
                StandardCharsets.UTF_8
            )
        )
        assertEquals(
            "0.123456",
            actual.path("intentos")[0].path("prediccion").path("confianza").asText()
        )
        assertEquals(
            "0.123458",
            actual.path("intentos")[0].path("top3")[1].path("confianza").asText()
        )
    }

    private fun fixedDocument(sequence: List<Int>) = SesionDocumento(
        envio = EnvioDocumento(
            envioId = UUID.fromString("3f2b8c1e-9a47-4d2e-8b61-0c5e7a9d4f12"),
            revision = 1,
            generadoEn = Instant.parse("2026-10-05T15:20:07Z")
        ),
        sesion = SesionRegistroDocumento(
            sesionId = UUID.fromString("a7c4e2d9-5b13-4f8a-9e26-1d0b3c8f7a45"),
            estado = EstadoSesionDocumento.INTERRUMPIDA,
            creadaEn = Instant.parse("2026-10-05T14:58:31Z"),
            ultimoIntentoEn = Instant.parse("2026-10-05T15:00:12Z"),
            intentosPerdidos = 0
        ),
        participante = ParticipanteDocumento("P-007"),
        consentimiento = ConsentimientoDocumento("1", Instant.parse("2026-10-05T14:52:03Z")),
        condiciones = CondicionesDocumento(
            entorno = "INTERIOR",
            tipoEntorno = "AULA",
            iluminacion = "MEDIA",
            contraluz = false,
            distancia = "ENTRE_1_Y_2M",
            manoDominante = "DIESTRA",
            guantes = false,
            soporteCamara = "TRIPODE",
            perfilParticipante = "SORDA_SENANTE"
        ),
        versiones = VersionesDocumento(
            appVersionName = "0.1.0",
            appVersionCode = 1,
            modelo = "eva-lsa64-v3",
            modeloSha256 = "e231d3962e2eb8d081e9a95d61d6600a6b489db06db03f971530bc7da8f0d488",
            catalogoSha256 = "377a02e97bd57b0133962dbe961c853ca3a205d0f985eceb31b358ea323df412",
            contratoKeypoints = 3,
            protocolo = "1.0.0"
        ),
        dispositivo = DispositivoDocumento("motorola", "moto g54 5G", 34),
        protocolo = ProtocoloDocumento(
            version = "1.0.0",
            semilla = 20261005L,
            senas = 64,
            repeticiones = 3,
            bloques = 4,
            intentosPorBloque = 48,
            secuencia = sequence
        ),
        intentos = listOf(
            intento(
                1, 1, false, 4, "Brillante", false, ResultadoIntentoDocumento.SOBRE_UMBRAL,
                null, 4, "Brillante", 0.962134,
                listOf(
                    top(1, 4, "Brillante", 0.962134),
                    top(2, 5, "Celeste", 0.02187),
                    top(3, 2, "Verde", 0.006411)
                ),
                0.9, true, true, false, false, 4210, 1630
            ),
            intento(
                2, 1, false, 18, "Amargo", false, ResultadoIntentoDocumento.SOBRE_UMBRAL,
                null, 30, "Desayuno", 0.917402,
                listOf(
                    top(1, 30, "Desayuno", 0.917402),
                    top(2, 18, "Amargo", 0.064118),
                    top(3, 44, "Asado", 0.009953)
                ),
                0.9, true, false, true, false, 11875, 1544
            ),
            intento(
                3, 1, false, 52, "Aparecer", false, ResultadoIntentoDocumento.BAJO_UMBRAL,
                null, 52, "Aparecer", 0.613027,
                listOf(
                    top(1, 52, "Aparecer", 0.613027),
                    top(2, 54, "Atrapar", 0.288761),
                    top(3, 11, "Hombre", 0.040335)
                ),
                0.9, false, false, false, true, 19340, 2011
            ),
            intento(
                4, 1, true, 38, "Nombre", false, ResultadoIntentoDocumento.SIN_RESULTADO,
                CausaSinResultadoDocumento.SIN_HOMBROS, null, null, null, emptyList(), 0.9,
                false, false, false, false, 26902, null
            ),
            intento(
                4, 2, false, 38, "Nombre", true, ResultadoIntentoDocumento.SOBRE_UMBRAL,
                null, 38, "Nombre", 0.93885,
                listOf(
                    top(1, 38, "Nombre", 0.93885),
                    top(2, 7, "Rosa", 0.030107),
                    top(3, 1, "Rojo", 0.012264)
                ),
                0.9, true, true, false, false, 35618, 1702
            )
        ),
        interrupciones = listOf(
            InterrupcionDocumento(
                bloque = 1,
                posicionPendiente = 5,
                causa = CausaInterrupcionDocumento.PROCESO_TERMINADO,
                ocurrioEn = Instant.parse("2026-10-05T15:01:40Z")
            )
        )
    )

    private fun intento(
        posicion: Int,
        numeroIntento: Int,
        reemplazado: Boolean,
        senaEsperadaIndice: Int,
        senaEsperadaGlosa: String,
        vioVideo: Boolean,
        resultado: ResultadoIntentoDocumento,
        causaSinResultado: CausaSinResultadoDocumento?,
        predichoIndice: Int?,
        predichoGlosa: String?,
        confianza: Double?,
        top3: List<PrediccionTopDocumento>,
        umbral: Double,
        superoUmbral: Boolean,
        correcto: Boolean,
        descartado: Boolean,
        loHiceMal: Boolean,
        inicioRelMs: Long,
        duracionSegmentoMs: Long?
    ) = IntentoDocumento(
        posicion = posicion,
        bloque = (posicion + 47) / 48,
        numeroIntento = numeroIntento,
        reemplazado = reemplazado,
        senaEsperada = SeniaDocumento(senaEsperadaIndice, senaEsperadaGlosa),
        vioVideo = vioVideo,
        resultado = resultado,
        causaSinResultado = causaSinResultado,
        prediccion = if (predichoIndice == null) {
            null
        } else {
            PrediccionDocumento(
                predichoIndice,
                checkNotNull(predichoGlosa),
                checkNotNull(confianza)
            )
        },
        top3 = top3,
        umbral = umbral,
        superoUmbral = superoUmbral,
        correcto = correcto,
        descartado = descartado,
        loHiceMal = loHiceMal,
        inicioRelMs = inicioRelMs,
        duracionSegmentoMs = duracionSegmentoMs
    )

    private fun top(rango: Int, indice: Int, glosa: String, confianza: Double) =
        PrediccionTopDocumento(rango, indice, glosa, confianza)

    private fun resource(path: String): InputStream =
        checkNotNull(javaClass.classLoader?.getResourceAsStream(path)) {
            "No se encontró el recurso $path"
        }

    private fun JsonNode.arrayItems(): List<JsonNode> = (0 until size()).map { get(it) }

    private fun ByteArray.startsWithUtf8Bom(): Boolean = size >= 3 &&
        this[0] == 0xEF.toByte() &&
        this[1] == 0xBB.toByte() &&
        this[2] == 0xBF.toByte()
}
