package com.helpi.conversation.evaluacion

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.helpi.conversation.lsa.ModelBundle
import com.helpi.conversation.lsa.ModelBundleResult
import com.helpi.conversation.lsa.SignAcceptancePolicy
import com.helpi.conversation.lsa.SignClassifier
import com.helpi.conversation.lsa.SignDecision
import com.helpi.conversation.observation.NoResultCause
import com.helpi.conversation.observation.RecognitionObserver
import com.helpi.evaluacion.datos.HelpiEvaluacionDatabase
import com.helpi.evaluacion.datos.PrediccionTopDato
import com.helpi.evaluacion.datos.entidades.CausaSinResultado
import com.helpi.evaluacion.datos.entidades.ConsentimientoEntity
import com.helpi.evaluacion.datos.entidades.EstadoSesion
import com.helpi.evaluacion.datos.entidades.IntentoEntity
import com.helpi.evaluacion.datos.entidades.ParticipanteEntity
import com.helpi.evaluacion.datos.entidades.ResultadoIntento
import com.helpi.evaluacion.datos.entidades.SesionEntity
import com.helpi.evaluacion.registro.EscritorRegistro
import com.helpi.evaluacion.registro.RegistroIntento
import com.helpi.evaluacion.registro.RegistroObserver
import java.io.File
import java.util.UUID
import kotlin.math.ceil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Mide el costo pareado del registro hasta el retorno del observador. */
@RunWith(AndroidJUnit4::class)
class RegistroSobrecostoTest {

    @Test
    fun mideCostoPareadoDelRegistroYControlAA() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val appContext = instrumentation.targetContext
        val testContext = instrumentation.context
        val casos = cargarFixture(testContext)
        assertTrue("fixture_android.json sin casos", casos.isNotEmpty())

        val resultadoBundle = ModelBundle.load(appContext)
        assertTrue("artefactos del modelo ausentes", resultadoBundle is ModelBundleResult.Ready)
        val bundle = (resultadoBundle as ModelBundleResult.Ready).bundle
        val classifier = SignClassifier(bundle)
        val policy = SignAcceptancePolicy(
            UMBRAL,
            bundle.manifest.outputsProbabilities,
            bundle.manifest.numClasses
        )
        val repeticiones = ceil(PARES_MINIMOS.toDouble() / casos.size).toInt() * casos.size
        val iteracionesConRegistro = CALENTAMIENTO + repeticiones
        val sesionesNecesarias = ceil(iteracionesConRegistro.toDouble() / POSICIONES_POR_SESION)
            .toInt()
        val databaseFile = File(appContext.cacheDir, "sobrecosto-${UUID.randomUUID()}.db")
        val database = Room.databaseBuilder(
            appContext,
            HelpiEvaluacionDatabase::class.java,
            databaseFile.absolutePath
        ).build()
        var escritor: EscritorRegistro? = null

        try {
            val sesiones = crearDatosTemporales(database, bundle, sesionesNecesarias)
            val writer = EscritorRegistro(database.intentoDao())
            escritor = writer
            val registro = RegistroObserver(bufferCapacity = iteracionesConRegistro)
            var intentosEntregados = 0
            var intentosEncolados = 0
            var claseEsperadaActual = casos.first().claseEsperada

            val observadorConRegistro = object : RecognitionObserver {
                override fun onSegmentStarted(startMs: Long) = registro.onSegmentStarted(startMs)

                override fun onNoResult(cause: NoResultCause) = registro.onNoResult(cause)

                override fun onDecision(
                    decision: SignDecision,
                    thresholdUsed: Float,
                    segmentEndMs: Long
                ) {
                    registro.onDecision(decision, thresholdUsed, segmentEndMs)
                    val indiceIntento = intentosEntregados++
                    val intento = convertirIntento(
                        decision,
                        thresholdUsed,
                        claseEsperadaActual,
                        sesiones[indiceIntento / POSICIONES_POR_SESION],
                        indiceIntento % POSICIONES_POR_SESION + 1,
                        bundle
                    )
                    if (writer.trySend(intento)) intentosEncolados++
                }
            }

            val pares = mutableListOf<Long>()
            val tiemposConRegistro = mutableListOf<Long>()
            val tiemposSinRegistro = mutableListOf<Long>()
            val controlesAA = mutableListOf<Long>()
            val random = java.util.Random(SEMILLA)

            fun medirConRegistro(caso: CasoFixture): Long {
                claseEsperadaActual = caso.claseEsperada
                return medir(caso, classifier, policy, observadorConRegistro)
            }

            repeat(CALENTAMIENTO) { iteracion ->
                val caso = casos[iteracion % casos.size]
                medirConRegistro(caso)
                medir(caso, classifier, policy, RecognitionObserver.NO_OP)
            }

            repeat(repeticiones) { iteracion ->
                val caso = casos[iteracion % casos.size]
                val conPrimero = random.nextBoolean()
                val tiempoCon: Long
                val tiempoSin: Long
                if (conPrimero) {
                    tiempoCon = medirConRegistro(caso)
                    tiempoSin = medir(caso, classifier, policy, RecognitionObserver.NO_OP)
                } else {
                    tiempoSin = medir(caso, classifier, policy, RecognitionObserver.NO_OP)
                    tiempoCon = medirConRegistro(caso)
                }
                tiemposConRegistro += tiempoCon
                tiemposSinRegistro += tiempoSin
                pares += tiempoCon - tiempoSin

                val primero = medir(caso, classifier, policy, RecognitionObserver.NO_OP)
                val segundo = medir(caso, classifier, policy, RecognitionObserver.NO_OP)
                controlesAA += if (random.nextBoolean()) segundo - primero else primero - segundo
            }

            writer.cerrarYEsperar()
            escritor = null
            val intentosPersistidos = withContext(Dispatchers.IO) {
                database.intentoDao().cantidadTotal()
            }
            val p95Aa = percentilMs(controlesAA, 0.95)
            val p95Diferencia = percentilMs(pares, 0.95)
            val esEmulador = Build.FINGERPRINT.contains("generic", ignoreCase = true) ||
                Build.MODEL.startsWith("sdk_gphone", ignoreCase = true) ||
                Build.HARDWARE.contains("ranchu", ignoreCase = true)
            val estado = when {
                p95Aa > LIMITE_MS -> "NO CONCLUYENTE"
                p95Diferencia > LIMITE_MS -> "NO APROBADO"
                esEmulador -> "INFORMATIVO EMULADOR; REQUIERE DISPOSITIVO DE REFERENCIA"
                else -> "CUMPLE UMBRALES; VALIDAR DISPOSITIVO DE REFERENCIA"
            }
            val reporte = JSONObject()
                .put("estado", estado)
                .put("fabricante", Build.MANUFACTURER)
                .put("dispositivo", Build.MODEL)
                .put("sdkAndroid", Build.VERSION.SDK_INT)
                .put("esEmulador", esEmulador)
                .put("semillaOrden", SEMILLA)
                .put("umbral", UMBRAL.toDouble())
                .put("casosFixture", casos.size)
                .put("calentamientoDescartado", CALENTAMIENTO)
                .put("pares", repeticiones)
                .put("p50ConMs", percentilMs(tiemposConRegistro, 0.50))
                .put("p95ConMs", percentilMs(tiemposConRegistro, 0.95))
                .put("p50SinMs", percentilMs(tiemposSinRegistro, 0.50))
                .put("p95SinMs", percentilMs(tiemposSinRegistro, 0.95))
                .put("p50DiferenciaMs", percentilMs(pares, 0.50))
                .put("p95DiferenciaMs", p95Diferencia)
                .put("p95ControlAAMs", p95Aa)
                .put("intentosEntregados", intentosEntregados)
                .put("intentosEncolados", intentosEncolados)
                .put("intentosPersistidos", intentosPersistidos)
                .put("latenciaAbsolutaEsCriterio", false)

            Log.i(TAG, "SOBRECOSTO_REPORTE=$reporte")
            guardarReporte(appContext, reporte)
            assertTrue("se deben obtener al menos 200 pares", pares.size >= PARES_MINIMOS)
            assertEquals(
                "el escritor debe encolar todas las decisiones",
                intentosEntregados,
                intentosEncolados
            )
            assertEquals(
                "el escritor debe persistir todas las decisiones",
                intentosEntregados,
                intentosPersistidos
            )
        } finally {
            escritor?.close()
            database.close()
            classifier.close()
            borrarBaseTemporal(databaseFile)
        }
    }

    private fun medir(
        caso: CasoFixture,
        classifier: SignClassifier,
        policy: SignAcceptancePolicy,
        observer: RecognitionObserver
    ): Long {
        val inicio = SystemClock.elapsedRealtimeNanos()
        val decision = policy.evaluate(classifier.classify(caso.tensor))
        observer.onDecision(decision, UMBRAL, 0L)
        return SystemClock.elapsedRealtimeNanos() - inicio
    }

    private suspend fun crearDatosTemporales(
        database: HelpiEvaluacionDatabase,
        bundle: com.helpi.conversation.lsa.ModelBundle,
        cantidadSesiones: Int
    ): List<String> = withContext(Dispatchers.IO) {
        val participante = ParticipanteEntity("P-001", System.currentTimeMillis())
        database.participanteDao().insertar(participante)
        val consentimientoId = database.consentimientoDao().insertar(
            ConsentimientoEntity(
                avisoVersion = "instrumentation-test",
                avisoVideoSha256 = HASH_TEST,
                otorgadoEn = System.currentTimeMillis(),
                revocadoEn = null
            )
        )
        val sesiones = List(cantidadSesiones) { UUID.randomUUID().toString() }
        sesiones.forEachIndexed { index, id ->
            database.sesionDao().insertar(
                SesionEntity(
                    id = id,
                    participanteCodigo = participante.codigo,
                    consentimientoId = consentimientoId,
                    protocoloVersion = "instrumentation-test",
                    semilla = SEMILLA + index,
                    estado = EstadoSesion.EN_CURSO,
                    bloqueActual = 1,
                    appVersionName = "instrumentation-test",
                    appVersionCode = 1,
                    modeloVersion = bundle.manifest.modelVersion,
                    modeloSha256 = bundle.manifest.modelSha256,
                    catalogoSha256 = bundle.manifest.catalogSha256,
                    contratoKeypoints = bundle.manifest.contractVersion,
                    dispositivoFabricante = Build.MANUFACTURER,
                    dispositivoModelo = Build.MODEL,
                    sdkAndroid = Build.VERSION.SDK_INT,
                    creadaEn = System.currentTimeMillis(),
                    ultimoIntentoEn = null,
                    ultimaExportacionEn = null,
                    intentosPerdidos = 0,
                    revision = 0
                )
            )
        }
        sesiones
    }

    private fun convertirIntento(
        decision: SignDecision,
        threshold: Float,
        claseEsperada: Int,
        sesionId: String,
        posicion: Int,
        bundle: com.helpi.conversation.lsa.ModelBundle
    ): RegistroIntento {
        val clasificada = decision.classIndex in 0 until bundle.catalog.size
        val resultado = when {
            !clasificada -> ResultadoIntento.SIN_RESULTADO
            decision.accepted -> ResultadoIntento.SOBRE_UMBRAL
            else -> ResultadoIntento.BAJO_UMBRAL
        }
        val top3 = if (clasificada) {
            decision.predictions.take(3).mapIndexed { index, prediction ->
                PrediccionTopDato(
                    rango = index + 1,
                    indice = prediction.classIndex,
                    glosa = requireNotNull(bundle.catalog.gloss(prediction.classIndex)),
                    confianza = prediction.confidence
                )
            }
        } else {
            emptyList()
        }
        check(resultado == ResultadoIntento.SIN_RESULTADO || top3.size == 3) {
            "Una clasificación válida debe contener el top-3"
        }
        val primerResultado = top3.firstOrNull()
        val intento = IntentoEntity(
            sesionId = sesionId,
            posicion = posicion,
            bloque = (posicion - 1) / POSICIONES_POR_BLOQUE + 1,
            numeroIntento = 1,
            reemplazado = false,
            senaEsperadaIndice = claseEsperada,
            senaEsperadaGlosa = requireNotNull(bundle.catalog.gloss(claseEsperada)),
            vioVideo = false,
            resultado = resultado,
            causaSinResultado = if (clasificada) null else CausaSinResultado.ERROR_MODELO,
            predichoIndice = primerResultado?.indice,
            predichoGlosa = primerResultado?.glosa,
            confianza = primerResultado?.confianza,
            umbral = threshold,
            superoUmbral = resultado == ResultadoIntento.SOBRE_UMBRAL,
            correcto = resultado == ResultadoIntento.SOBRE_UMBRAL &&
                primerResultado?.indice == claseEsperada,
            descartado = false,
            loHiceMal = false,
            inicioRelMs = 0L,
            duracionSegmentoMs = 0L
        )
        return RegistroIntento(intento, top3, SystemClock.elapsedRealtime())
    }

    private fun cargarFixture(context: Context): List<CasoFixture> {
        val fixture = context.assets.open("fixture_android.json").bufferedReader().use {
            JSONObject(it.readText())
        }
        val casos = fixture.getJSONArray("casos")
        return List(casos.length()) { indiceCaso ->
            val caso = casos.getJSONObject(indiceCaso)
            val secuencia = caso.getJSONArray("secuencia")
            val tensor = FloatArray(FRAMES * COORDS)
            for (cuadro in 0 until secuencia.length()) {
                val coordenadas = secuencia.getJSONArray(cuadro)
                for (coordenada in 0 until coordenadas.length()) {
                    tensor[cuadro * COORDS + coordenada] =
                        coordenadas.getDouble(coordenada).toFloat()
                }
            }
            CasoFixture(tensor, caso.getInt("claseEsperada"))
        }
    }

    private fun guardarReporte(context: Context, reporte: JSONObject) {
        val destino = File(context.filesDir, "qa-stages/sobrecosto/resultado.json")
        check(destino.parentFile?.mkdirs() == true || destino.parentFile?.isDirectory == true)
        destino.writeText(reporte.toString(2))
    }

    private fun borrarBaseTemporal(databaseFile: File) {
        databaseFile.delete()
        File(databaseFile.path + "-wal").delete()
        File(databaseFile.path + "-shm").delete()
    }

    private fun percentilMs(muestrasNanos: List<Long>, p: Double): Double {
        val indice = ceil(p * muestrasNanos.size).toInt().coerceAtLeast(1) - 1
        return muestrasNanos.sorted()[indice].toDouble() / NANOS_POR_MILISEGUNDO
    }

    private data class CasoFixture(val tensor: FloatArray, val claseEsperada: Int)

    private companion object {
        const val PARES_MINIMOS = 200
        const val CALENTAMIENTO = 20
        const val POSICIONES_POR_SESION = 192
        const val POSICIONES_POR_BLOQUE = POSICIONES_POR_SESION / 4
        const val FRAMES = 40
        const val COORDS = 168
        const val UMBRAL = 0.90f
        const val LIMITE_MS = 5.0
        const val NANOS_POR_MILISEGUNDO = 1_000_000.0
        const val SEMILLA = 0x5EED_016L
        const val TAG = "RegistroSobrecostoTest"
        const val HASH_TEST = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    }
}
