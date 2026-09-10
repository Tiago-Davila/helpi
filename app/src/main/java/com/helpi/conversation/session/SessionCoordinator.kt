package com.helpi.conversation.session

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.helpi.conversation.audio.AudioArbiter
import com.helpi.conversation.audio.OfflineSpeechOutput
import com.helpi.conversation.audio.VoskModelStore
import com.helpi.conversation.audio.VoskSpeechSource
import com.helpi.conversation.chat.Conversation
import com.helpi.conversation.chat.Speaker
import com.helpi.conversation.chat.Turn
import com.helpi.conversation.chat.VoiceState
import com.helpi.conversation.keypoints.KeypointContract
import com.helpi.conversation.lsa.ModelBundle
import com.helpi.conversation.lsa.ModelBundleResult
import com.helpi.conversation.lsa.SignAcceptancePolicy
import com.helpi.conversation.lsa.SignClassifier
import com.helpi.conversation.vision.FramingEvaluator
import com.helpi.conversation.vision.LandmarkFrame
import com.helpi.conversation.vision.ObservationFactory
import com.helpi.conversation.vision.SegmentEvent
import com.helpi.conversation.vision.SegmenterConfig
import com.helpi.conversation.vision.SignSegmenter
import java.util.ArrayDeque
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Único coordinador de la sesión: recibe eventos de ambos canales, mantiene
 * los estados y autoriza publicación y TTS. Todo el estado muta en un
 * executor serial (único escritor).
 *
 * La conversación vive exclusivamente en memoria y se descarta al cerrar.
 */
class SessionCoordinator(private val context: Context) {

    data class Capabilities(
        val keyboard: Boolean = true,
        val vision: Boolean = false,
        val visionDetail: String = "",
        val stt: Boolean = false,
        val sttDetail: String = "",
        val tts: Boolean = false,
        val ttsDetail: String = "",
    )

    data class RecognitionFeedback(
        val confidence: Float,
        val threshold: Float,
        val accepted: Boolean,
        val gloss: String? = null,
    )

    data class UiState(
        val session: SessionState = SessionState.INICIO,
        val visual: VisualChannelState = VisualChannelState.NO_DISPONIBLE,
        val audio: AudioChannelState = AudioChannelState.NO_DISPONIBLE,
        val framing: FramingEvaluator.Issue = FramingEvaluator.Issue.SIN_PERSONA,
        val turns: List<Turn> = emptyList(),
        val capabilities: Capabilities = Capabilities(),
        val confidenceThreshold: Float = DEFAULT_THRESHOLD,
        val lastRecognition: RecognitionFeedback? = null,
        val notice: String? = null,
    )

    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private val classifierExecutor = Executors.newSingleThreadExecutor()

    private var stateMachine = SessionStateMachine()
    private val conversation = Conversation()
    private val segmenter = SignSegmenter(SegmenterConfig.defaults())
    private val framingEvaluator = FramingEvaluator()
    private val observationFactory = ObservationFactory()

    /** Buffer de cuadros del segmento en curso (timestamps + 201 coords). */
    private val frameBuffer = ArrayDeque<Pair<Long, FloatArray>>()
    private val preRollBuffer = ArrayDeque<Pair<Long, FloatArray>>()

    private var classifier: SignClassifier? = null
    private var acceptancePolicy: SignAcceptancePolicy? = null
    private var bundle: ModelBundle? = null
    private var speech: VoskSpeechSource? = null
    private var speechOutput: OfflineSpeechOutput? = null
    private var classifying = false
    private var ticker: ScheduledFuture<*>? = null
    private var confidenceThreshold = DEFAULT_THRESHOLD
    private var lastRecognition: RecognitionFeedback? = null

    /** Generación de sesión: los callbacks tardíos de otra generación se descartan. */
    @Volatile
    private var generation = 0

    private var partialTurnId: Long = -1
    private var pausedAtMs: Long = 0
    private var visualState = VisualChannelState.NO_DISPONIBLE
    private var audioState = AudioChannelState.NO_DISPONIBLE
    private var framing = FramingEvaluator.Issue.SIN_PERSONA
    private var capabilities = Capabilities()
    private var notice: String? = null

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val arbiter: AudioArbiter by lazy {
        AudioArbiter(object : AudioArbiter.Listener {
        override fun requestCloseSttGate() {
            val gen = generation
            audioState = AudioChannelState.CERRANDO_ENTRADA_STT
            speech?.closeGate()
            // confirmación explícita: la compuerta quedó cerrada
            submit(gen) {
                audioState = AudioChannelState.TTS_HABLANDO
                arbiter.sttGateClosed(now())
                publish()
            }
        }

        override fun openSttGate() {
            speech?.openGate()
            audioState = if (capabilities.stt) {
                AudioChannelState.STT_ESCUCHANDO
            } else {
                AudioChannelState.NO_DISPONIBLE
            }
        }

        override fun speak(messageId: Long, text: String) {
            audioState = AudioChannelState.TTS_HABLANDO
            speechOutput?.speak(messageId, text)
        }

        override fun expired(messageId: Long) {
            if (hasTurn(messageId)) {
                conversation.updateVoice(messageId, VoiceState.NOT_SPOKEN)
            }
        }
    })
    }

    // ------------------------------------------------------------------
    // ciclo de vida
    // ------------------------------------------------------------------

    fun prepare() = submit(generation) {
        if (stateMachine.current() == SessionState.CERRADA) {
            stateMachine = SessionStateMachine()
            visualState = VisualChannelState.NO_DISPONIBLE
            audioState = AudioChannelState.NO_DISPONIBLE
            capabilities = Capabilities()
            lastRecognition = null
            notice = null
        }
        if (!stateMachine.canTransition(SessionState.PREPARANDO)) return@submit
        stateMachine.transition(SessionState.PREPARANDO)
        publish()

        // Canal visual: modelo + catálogo (unidad verificada)
        val visionCap = if (!hasPermission(Manifest.permission.CAMERA)) {
            false to "permiso de cámara denegado"
        } else when (val result = ModelBundle.load(context)) {
            is ModelBundleResult.Ready -> {
                bundle = result.bundle
                try {
                    classifier = SignClassifier(result.bundle)
                    acceptancePolicy = SignAcceptancePolicy(
                        confidenceThreshold,
                        result.bundle.manifest.outputsProbabilities,
                        result.bundle.manifest.numClasses,
                    )
                    true to ""
                } catch (e: Exception) {
                    false to "modelo incompatible: ${e.message}"
                }
            }
            is ModelBundleResult.Missing -> false to "falta ${result.asset}"
            is ModelBundleResult.Invalid -> false to result.cause
        }

        // Canal A: Vosk
        val sttCap = if (!hasPermission(Manifest.permission.RECORD_AUDIO)) {
            false to "permiso de micrófono denegado"
        } else when (val result = VoskModelStore.install(context)) {
            is VoskModelStore.Result.Ready -> {
                try {
                    val gen = generation
                    speech = VoskSpeechSource(
                        modelDir = result.modelDir,
                        onPartial = { text -> submit(gen) { onSttPartial(text) } },
                        onFinal = { text -> submit(gen) { onSttFinal(text) } },
                        onSpeechActivity = { active ->
                            submit(gen) {
                                arbiter.hearingSpeechActive(active, now())
                                if (active) audioState = AudioChannelState.STT_TRANSCRIBIENDO
                            }
                        },
                        onError = { msg -> submit(gen) { notice = msg; publish() } },
                    )
                    true to ""
                } catch (e: Exception) {
                    false to "Vosk no inicializó: ${e.message}"
                }
            }
            is VoskModelStore.Result.Missing -> false to result.detail
            is VoskModelStore.Result.Failed -> false to result.detail
        }

        // Salida de voz
        val gen = generation
        speechOutput = OfflineSpeechOutput(
            context,
            onReady = { desc ->
                submit(gen) {
                    capabilities = capabilities.copy(
                        tts = desc != null,
                        ttsDetail = desc ?: "sin voz española offline: la persona oyente no recibirá audio",
                    )
                    if (desc != null &&
                        stateMachine.current() == SessionState.ACTIVA_LIMITADA &&
                        targetActiveState() == SessionState.ACTIVA
                    ) {
                        stateMachine.transition(SessionState.ACTIVA)
                    }
                    publish()
                }
            },
            onSpoken = { id ->
                submit(gen) {
                    if (hasTurn(id)) conversation.updateVoice(id, VoiceState.SPOKEN)
                    audioState = AudioChannelState.GUARDA_ACUSTICA
                    arbiter.ttsFinished(id, now())
                    publish()
                }
            },
            onFailed = { id ->
                submit(gen) {
                    if (hasTurn(id)) conversation.updateVoice(id, VoiceState.NOT_SPOKEN)
                    arbiter.ttsFailed(id, now())
                    publish()
                }
            },
        )

        capabilities = capabilities.copy(
            vision = visionCap.first, visionDetail = visionCap.second,
            stt = sttCap.first, sttDetail = sttCap.second,
        )
        audioState = if (sttCap.first) AudioChannelState.STT_LISTO else AudioChannelState.NO_DISPONIBLE

        // El teclado siempre es un canal disponible, aun si se deniegan los
        // sensores. La sesión no queda bloqueada por una capacidad opcional.
        stateMachine.transition(SessionState.LISTA)
        publish()
    }

    fun startConversation() = submit(generation) {
        if (!stateMachine.canTransition(targetActiveState())) return@submit
        stateMachine.transition(targetActiveState())
        if (capabilities.stt) {
            speech?.start()
            audioState = AudioChannelState.STT_ESCUCHANDO
        }
        visualState = if (capabilities.vision) {
            VisualChannelState.BUSCANDO_ENCUADRE
        } else {
            VisualChannelState.NO_DISPONIBLE
        }
        // reloj del árbitro (vencimientos, guarda, watchdog)
        val gen = generation
        ticker?.cancel(false)
        ticker = executor.scheduleWithFixedDelay(
            { if (gen == generation) submit(gen) { arbiter.tick(now()); publish() } },
            200, 200, TimeUnit.MILLISECONDS,
        )
        publish()
    }

    /** Segundo plano / bloqueo: pausa sensores y descarta lo pendiente. */
    fun pause() = submit(generation) {
        if (!stateMachine.canTransition(SessionState.PAUSADA)) return@submit
        pausedAtMs = now()
        stateMachine.transition(SessionState.PAUSADA)
        speechOutput?.stop()
        speech?.closeGate()
        frameBuffer.clear()
        preRollBuffer.clear()
        observationFactory.reset()
        if (partialTurnId >= 0) {
            conversation.markIncomplete(partialTurnId)
            partialTurnId = -1
        }
        publish()
    }

    /**
     * Reanudación EXPLÍCITA ("Reanudar"): nunca automática. Si la pausa
     * superó la vigencia, la sesión se invalida y el historial se descarta;
     * se verifica el vencimiento aunque Android haya suspendido los timers.
     */
    fun resume() = submit(generation) {
        if (stateMachine.current() != SessionState.PAUSADA) return@submit
        if (now() - pausedAtMs > PAUSE_TTL_MS) {
            notice = "La pausa superó los 2 minutos: la conversación se descartó."
            doClose()
            return@submit
        }
        // se re-verifica el estado de los canales antes de volver a mostrar
        stateMachine.transition(SessionState.PREPARANDO)
        stateMachine.transition(SessionState.LISTA)
        stateMachine.transition(targetActiveState())
        if (capabilities.stt) {
            speech?.openGate()
            audioState = AudioChannelState.STT_ESCUCHANDO
        }
        if (capabilities.vision) {
            visualState = VisualChannelState.BUSCANDO_ENCUADRE
        }
        publish()
    }

    /** Cierre explícito: descarta el historial y libera recursos. */
    fun closeSession() = submit(generation) { doClose() }

    private fun doClose() {
        generation++
        if (stateMachine.canTransition(SessionState.CERRANDO)) {
            stateMachine.transition(SessionState.CERRANDO)
        }
        speechOutput?.close()
        speechOutput = null
        ticker?.cancel(false)
        ticker = null
        speech?.close()
        speech = null
        arbiter.reset()
        classifier?.close()
        classifier = null
        acceptancePolicy = null
        bundle = null
        lastRecognition = null
        conversation.clear()
        frameBuffer.clear()
        preRollBuffer.clear()
        if (stateMachine.canTransition(SessionState.CERRADA)) {
            stateMachine.transition(SessionState.CERRADA)
        }
        publish()
    }

    fun shutdown() {
        closeSession()
        executor.shutdown()
        classifierExecutor.shutdown()
    }

    // ------------------------------------------------------------------
    // canal visual
    // ------------------------------------------------------------------

    /** Entrada desde HolisticExtractor (worker de MediaPipe): solo publica. */
    fun onLandmarks(frame: LandmarkFrame) {
        val gen = generation
        submit(gen) { processLandmarks(frame) }
    }

    private fun processLandmarks(frame: LandmarkFrame) {
        if (!capabilities.vision || !isActive()) return

        val obs = observationFactory.observe(frame)
        framing = framingEvaluator.evaluate(obs)

        val vector = KeypointContract.flattenFrame(frame.leftHand, frame.rightHand, frame.pose)

        // pre-roll: cuadros previos al inicio detectado
        preRollBuffer.addLast(frame.timestampMs to vector)
        while (preRollBuffer.size > PRE_ROLL_FRAMES) preRollBuffer.removeFirst()

        if (segmenter.isCapturing()) {
            frameBuffer.addLast(frame.timestampMs to vector)
        }

        val event = segmenter.process(obs)
        when (event.type) {
            SegmentEvent.Type.ARMED -> visualState = VisualChannelState.ARMADO
            SegmentEvent.Type.STARTED -> {
                visualState = VisualChannelState.CAPTURANDO_SENA
                frameBuffer.clear()
                for ((ts, v) in preRollBuffer) {
                    if (ts >= event.segmentStartMs) frameBuffer.addLast(ts to v)
                }
            }
            SegmentEvent.Type.ENDED -> {
                visualState = VisualChannelState.REARMANDO
                classifySegment(event.segmentStartMs, event.segmentEndMs)
            }
            SegmentEvent.Type.ABORTED -> {
                visualState = VisualChannelState.REARMANDO
                frameBuffer.clear()
                if (event.abortReason == SegmentEvent.AbortReason.TRACKING_LOST) {
                    visualState = VisualChannelState.CALIDAD_INSUFICIENTE
                    conversation.systemNote(
                        "No vi bien tus manos. Dejá las manos quietas un momento y volvé a intentar.",
                        now(),
                    )
                }
            }
            SegmentEvent.Type.NONE -> {
                if (visualState == VisualChannelState.BUSCANDO_ENCUADRE &&
                    framing == FramingEvaluator.Issue.OK
                ) {
                    visualState = VisualChannelState.ESPERANDO_REPOSO
                }
            }
        }
        publish()
    }

    private fun classifySegment(startMs: Long, endMs: Long) {
        val cls = classifier ?: return
        val policy = acceptancePolicy ?: return
        val catalog = bundle?.catalog ?: return

        if (classifying) {
            // sin cola ilimitada: el intento no se traduce tardíamente sin aviso
            conversation.systemNote("Ese intento no se procesó: había otro en curso.", now())
            frameBuffer.clear()
            return
        }
        val frames = frameBuffer.filter { it.first in startMs..endMs }.map { it.second }
        frameBuffer.clear()
        if (frames.isEmpty()) return

        val turn = conversation.open(Speaker.DEAF, startMs)
        val gen = generation
        val thresholdUsed = confidenceThreshold
        classifying = true
        classifierExecutor.execute {
            val result = runCatching {
                val tensor = KeypointContract.buildInputTensor(frames)
                policy.evaluate(cls.classify(tensor))
            }
            submit(gen) {
                classifying = false
                if (!isActive()) {
                    conversation.markRejected(turn.id())
                    publish()
                    return@submit
                }
                result.fold(
                    onSuccess = { decision ->
                        val acceptedGloss = if (decision.accepted) {
                            catalog.gloss(decision.classIndex)
                        } else {
                            null
                        }
                        lastRecognition = RecognitionFeedback(
                            confidence = decision.confidence,
                            threshold = thresholdUsed,
                            accepted = decision.accepted,
                            gloss = acceptedGloss,
                        )
                        if (decision.accepted) {
                            val text = catalog.displayText(decision.classIndex)
                            if (text == null) {
                                conversation.markRejected(turn.id())
                            } else {
                                conversation.publishSign(turn.id(), text)
                                arbiter.enqueue(turn.id(), catalog.gloss(decision.classIndex)!!, now())
                            }
                        } else {
                            conversation.markRejected(turn.id())
                            conversation.systemNote(
                                    "No reconocí la seña. No dije nada en voz alta. " +
                                    "Confianza ${(decision.confidence * 100).roundToInt()} %, " +
                                    "umbral ${(thresholdUsed * 100).roundToInt()} %. " +
                                    "Dejá las manos quietas un momento y volvé a intentar. " +
                                    "Solo reconozco las señas del catálogo.",
                                now(),
                            )
                        }
                    },
                    onFailure = {
                        conversation.markRejected(turn.id())
                        notice = "No se pudo ejecutar el modelo LSA: ${it.message ?: "error desconocido"}"
                    },
                )
                publish()
            }
        }
    }

    /** "No quise decir eso". */
    fun markIncorrect(turnId: Long) = submit(generation) {
        val cancelled = arbiter.cancel(turnId)
        if (cancelled) {
            conversation.updateVoice(turnId, VoiceState.CANCELLED)
            conversation.get(turnId) // el turno queda, marcado abajo
        }
        conversation.markIncorrect(turnId, now())
        if (!cancelled) {
            // ya se pronunció: aviso determinístico por la misma cola
            val note = conversation.ordered().last()
            arbiter.enqueue(note.id(), "La traducción anterior fue marcada como incorrecta", now())
        }
        publish()
    }

    /** Ajusta la compuerta sin cambiar ni volver a cargar el modelo. */
    fun setConfidenceThreshold(value: Float) = submit(generation) {
        confidenceThreshold = value.coerceIn(MIN_THRESHOLD, MAX_THRESHOLD)
        bundle?.let { loaded ->
            acceptancePolicy = SignAcceptancePolicy(
                confidenceThreshold,
                loaded.manifest.outputsProbabilities,
                loaded.manifest.numClasses,
            )
        }
        publish()
    }

    /** Texto escrito por la persona sorda: se publica y se pronuncia por TTS. */
    fun submitTyped(text: String) = submit(generation) {
        val normalized = text.trim()
        if (!isActive() || normalized.isEmpty()) return@submit
        val turn = conversation.open(Speaker.DEAF, now())
        conversation.publishTyped(turn.id(), normalized)
        if (capabilities.tts) {
            arbiter.enqueue(turn.id(), normalized, now())
        } else {
            conversation.updateVoice(turn.id(), VoiceState.NOT_SPOKEN)
            notice = "El texto se mostró, pero no hay una voz española sin conexión instalada."
        }
        publish()
    }

    /** Repite por voz un turno final de la persona sorda. */
    fun repeatTurn(turnId: Long) = submit(generation) {
        if (!isActive() || !capabilities.tts) return@submit
        val turn = runCatching { conversation.get(turnId) }.getOrNull() ?: return@submit
        if (turn.speaker() != Speaker.DEAF || turn.text().isBlank()) return@submit
        val spoken = turn.text().removePrefix("Seña reconocida: ")
        conversation.updateVoice(turnId, VoiceState.PENDING)
        arbiter.enqueue(turnId, spoken, now())
        publish()
    }

    /** Un fallo real de CameraX/MediaPipe inhabilita el canal con causa. */
    fun reportVisionUnavailable(detail: String) = submit(generation) {
        capabilities = capabilities.copy(vision = false, visionDetail = detail)
        visualState = VisualChannelState.NO_DISPONIBLE
        notice = detail
        publish()
    }

    // ------------------------------------------------------------------
    // canal de audio
    // ------------------------------------------------------------------

    private fun onSttPartial(text: String) {
        if (!isActive()) return
        if (partialTurnId < 0) {
            partialTurnId = conversation.open(Speaker.HEARING, now()).id()
        }
        conversation.updatePartial(partialTurnId, text)
        audioState = AudioChannelState.STT_TRANSCRIBIENDO
        publish()
    }

    private fun onSttFinal(text: String) {
        if (!isActive()) return
        if (partialTurnId < 0) {
            partialTurnId = conversation.open(Speaker.HEARING, now()).id()
        }
        conversation.finalize(partialTurnId, text)
        partialTurnId = -1
        audioState = AudioChannelState.STT_ESCUCHANDO
        publish()
    }

    // ------------------------------------------------------------------
    // utilitarios
    // ------------------------------------------------------------------

    private fun targetActiveState(): SessionState =
        if (capabilities.vision && capabilities.stt && capabilities.tts) {
            SessionState.ACTIVA
        } else {
            SessionState.ACTIVA_LIMITADA
        }

    private fun isActive(): Boolean =
        stateMachine.current() == SessionState.ACTIVA ||
            stateMachine.current() == SessionState.ACTIVA_LIMITADA

    private fun hasTurn(id: Long): Boolean =
        runCatching { conversation.get(id) }.isSuccess

    private fun now(): Long = android.os.SystemClock.elapsedRealtime()

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun submit(gen: Int, block: () -> Unit) {
        executor.execute {
            if (gen == generation) block()
        }
    }

    private fun publish() {
        _uiState.value = UiState(
            session = stateMachine.current(),
            visual = visualState,
            audio = audioState,
            framing = framing,
            turns = conversation.ordered().toList(),
            capabilities = capabilities,
            confidenceThreshold = confidenceThreshold,
            lastRecognition = lastRecognition,
            notice = notice,
        )
    }

    companion object {
        /** Umbral inicial de laboratorio (D03); no aprobado de producción. */
        const val DEFAULT_THRESHOLD = 0.90f
        const val MIN_THRESHOLD = 0.50f
        const val MAX_THRESHOLD = 0.99f
        /** Vigencia de la pausa; pasada, la sesión se invalida (D07). */
        const val PAUSE_TTL_MS = 2L * 60 * 1000
        private const val PRE_ROLL_FRAMES = 8
    }
}
