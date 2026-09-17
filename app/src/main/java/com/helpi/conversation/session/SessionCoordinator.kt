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
import com.helpi.conversation.lsa.sequence.PhraseCaptureState
import com.helpi.conversation.lsa.sequence.RecognitionMode
import com.helpi.conversation.lsa.sequence.SequenceCaptureBuffer
import com.helpi.conversation.lsa.sequence.SequenceAppendResult
import com.helpi.conversation.lsa.sequence.SequenceAcceptancePolicy
import com.helpi.conversation.lsa.sequence.SequenceModelBundle
import com.helpi.conversation.lsa.sequence.SequenceModelBundleResult
import com.helpi.conversation.lsa.sequence.SequenceTranslationCandidate
import com.helpi.conversation.lsa.sequence.SequenceTranslator
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
        val phraseVision: Boolean = false,
        val phraseVisionDetail: String = "",
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
        val recognitionMode: RecognitionMode = RecognitionMode.SINGLE_SIGN,
        val phraseCapture: PhraseCaptureState = PhraseCaptureState.UNAVAILABLE,
        val phraseCandidate: SequenceTranslationCandidate? = null,
        val notice: String? = null,
        val microphoneEnabled: Boolean = true,
        val cameraEnabled: Boolean = true,
    )

    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private val classifierExecutor = Executors.newSingleThreadExecutor()
    private val sequenceClassifierExecutor = Executors.newSingleThreadExecutor()

    private var stateMachine = SessionStateMachine()
    private val conversation = Conversation()
    private var segmenter = SignSegmenter(SegmenterConfig.defaults())
    private val framingEvaluator = FramingEvaluator()
    private val observationFactory = ObservationFactory()
    private val phraseBuffer = SequenceCaptureBuffer()

    /** Buffer de cuadros del segmento en curso (timestamps + 201 coords). */
    private val frameBuffer = ArrayDeque<Pair<Long, FloatArray>>()
    private val preRollBuffer = ArrayDeque<Pair<Long, FloatArray>>()

    private var classifier: SignClassifier? = null
    private var acceptancePolicy: SignAcceptancePolicy? = null
    private var bundle: ModelBundle? = null
    private var speech: VoskSpeechSource? = null
    private var speechOutput: OfflineSpeechOutput? = null
    private var classifying = false
    private var sequenceTranslator: SequenceTranslator? = null
    private var sequenceBundle: SequenceModelBundle? = null
    private val sequenceAcceptancePolicy = SequenceAcceptancePolicy()
    private var sequenceClassifying = false
    private var ticker: ScheduledFuture<*>? = null
    private var confidenceThreshold = DEFAULT_THRESHOLD
    private var lastRecognition: RecognitionFeedback? = null
    private var recognitionMode = RecognitionMode.SINGLE_SIGN
    private var phraseCaptureState = PhraseCaptureState.UNAVAILABLE
    private var phraseCandidate: SequenceTranslationCandidate? = null
    private var phraseCandidateStartMs: Long = -1L
    private var lastVisionTimestampMs: Long = 0L
    private var microphoneEnabled = true
    private var cameraEnabled = true
    private var visionRevision = 0

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
                if (!isActive() || arbiter.state() != AudioArbiter.State.WAITING_GATE) return@submit
                audioState = AudioChannelState.TTS_HABLANDO
                arbiter.sttGateClosed(now())
                publish()
            }
        }

        override fun openSttGate() {
            if (microphoneEnabled && isActive()) speech?.openGate()
            audioState = if (capabilities.stt && microphoneEnabled && isActive()) {
                AudioChannelState.STT_ESCUCHANDO
            } else {
                AudioChannelState.STT_LISTO
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

    fun prepare(autoStart: Boolean = false) = submit(generation) {
        if (stateMachine.current() == SessionState.CERRADA) {
            stateMachine = SessionStateMachine()
            visualState = VisualChannelState.NO_DISPONIBLE
            audioState = AudioChannelState.NO_DISPONIBLE
            capabilities = Capabilities()
            lastRecognition = null
            recognitionMode = RecognitionMode.SINGLE_SIGN
            phraseCaptureState = PhraseCaptureState.UNAVAILABLE
            phraseCandidate = null
            phraseCandidateStartMs = -1L
            lastVisionTimestampMs = 0L
            notice = null
            microphoneEnabled = true
            cameraEnabled = true
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

        // Canal experimental de frases: su ausencia nunca inhabilita a Eva.
        val phraseCap = if (!hasPermission(Manifest.permission.CAMERA)) {
            false to "permiso de cámara denegado"
        } else when (val result = SequenceModelBundle.load(context)) {
            is SequenceModelBundleResult.Ready -> {
                try {
                    sequenceBundle = result.bundle
                    sequenceTranslator = SequenceTranslator(result.bundle)
                    phraseCaptureState = PhraseCaptureState.READY
                    true to ""
                } catch (e: Exception) {
                    sequenceTranslator = null
                    sequenceBundle = null
                    phraseCaptureState = PhraseCaptureState.UNAVAILABLE
                    false to "paquete de frases incompatible: ${e.message}"
                }
            }
            is SequenceModelBundleResult.Missing -> {
                phraseCaptureState = PhraseCaptureState.UNAVAILABLE
                false to "modo frase pendiente: falta ${result.asset}"
            }
            is SequenceModelBundleResult.Invalid -> {
                phraseCaptureState = PhraseCaptureState.UNAVAILABLE
                false to result.cause
            }
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
                                if (!isActive() || !microphoneEnabled) return@submit
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
            phraseVision = phraseCap.first, phraseVisionDetail = phraseCap.second,
            stt = sttCap.first, sttDetail = sttCap.second,
        )
        audioState = if (sttCap.first) AudioChannelState.STT_LISTO else AudioChannelState.NO_DISPONIBLE

        // El teclado siempre es un canal disponible, aun si se deniegan los
        // sensores. La sesión no queda bloqueada por una capacidad opcional.
        stateMachine.transition(SessionState.LISTA)
        publish()
        if (autoStart) startConversation()
    }

    fun startConversation() = submit(generation) {
        if (!stateMachine.canTransition(targetActiveState())) return@submit
        stateMachine.transition(targetActiveState())
        if (capabilities.stt) {
            speech?.start()
            audioState = AudioChannelState.STT_ESCUCHANDO
        }
        visualState = if (activeVisionAvailable()) {
            VisualChannelState.BUSCANDO_ENCUADRE
        } else {
            VisualChannelState.NO_DISPONIBLE
        }
        // reloj del árbitro (vencimientos, guarda, watchdog)
        val gen = generation
        ticker?.cancel(false)
        ticker = executor.scheduleWithFixedDelay(
            { if (gen == generation) submit(gen) { if (isActive()) { arbiter.tick(now()); publish() } } },
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
        speech?.stop()
        conversation.ordered().filter { it.voiceState() == VoiceState.PENDING }.forEach {
            conversation.updateVoice(it.id(), VoiceState.NOT_SPOKEN)
        }
        arbiter.reset()
        visionRevision++
        segmenter = SignSegmenter(SegmenterConfig.defaults())
        frameBuffer.clear()
        preRollBuffer.clear()
        phraseBuffer.cancel()
        phraseCandidate = null
        phraseCandidateStartMs = -1L
        phraseCaptureState = if (capabilities.phraseVision) {
            PhraseCaptureState.READY
        } else {
            PhraseCaptureState.UNAVAILABLE
        }
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
        if (capabilities.stt && microphoneEnabled) {
            speech?.start()
            audioState = AudioChannelState.STT_ESCUCHANDO
        }
        if (activeVisionAvailable() && cameraEnabled) {
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
        classifier?.let { closing -> classifierExecutor.execute { closing.close() } }
        classifier = null
        classifying = false
        sequenceTranslator?.let { closing -> sequenceClassifierExecutor.execute { closing.close() } }
        sequenceTranslator = null
        sequenceBundle = null
        sequenceClassifying = false
        partialTurnId = -1
        visionRevision++
        segmenter = SignSegmenter(SegmenterConfig.defaults())
        observationFactory.reset()
        acceptancePolicy = null
        bundle = null
        lastRecognition = null
        recognitionMode = RecognitionMode.SINGLE_SIGN
        phraseBuffer.cancel()
        phraseCaptureState = PhraseCaptureState.UNAVAILABLE
        phraseCandidate = null
        phraseCandidateStartMs = -1L
        lastVisionTimestampMs = 0L
        conversation.clear()
        frameBuffer.clear()
        preRollBuffer.clear()
        if (stateMachine.canTransition(SessionState.CERRADA)) {
            stateMachine.transition(SessionState.CERRADA)
        }
        publish()
    }

    fun shutdown() {
        executor.execute {
            doClose()
            classifierExecutor.shutdown()
            sequenceClassifierExecutor.shutdown()
            executor.shutdown()
        }
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
        val visualAvailable = when (recognitionMode) {
            RecognitionMode.SINGLE_SIGN -> capabilities.vision
            RecognitionMode.PHRASE_EXPERIMENTAL -> capabilities.phraseVision
        }
        if (!visualAvailable || !cameraEnabled || !isActive()) return

        lastVisionTimestampMs = frame.timestampMs

        val obs = observationFactory.observe(frame)
        framing = framingEvaluator.evaluate(obs)

        if (recognitionMode == RecognitionMode.PHRASE_EXPERIMENTAL) {
            processPhraseLandmarks(frame)
            publish()
            return
        }

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

    private fun processPhraseLandmarks(frame: LandmarkFrame) {
        if (phraseCaptureState != PhraseCaptureState.CAPTURING) {
            if (visualState == VisualChannelState.BUSCANDO_ENCUADRE &&
                framing == FramingEvaluator.Issue.OK
            ) {
                visualState = VisualChannelState.ESPERANDO_REPOSO
            }
            return
        }

        when (phraseBuffer.append(frame.timestampMs, frame.leftHand, frame.rightHand)) {
            SequenceAppendResult.ACCEPTED -> {
                visualState = VisualChannelState.CAPTURANDO_SENA
            }
            SequenceAppendResult.TOO_LONG -> {
                phraseBuffer.cancel()
                phraseCaptureState = PhraseCaptureState.READY
                visualState = VisualChannelState.REARMANDO
                notice = "La captura superó los 12 segundos. Empezá la frase de nuevo."
            }
            SequenceAppendResult.NOT_CAPTURING -> {
                phraseCaptureState = PhraseCaptureState.READY
            }
        }
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
        val revision = visionRevision
        classifying = true
        classifierExecutor.execute {
            val result = runCatching {
                val tensor = KeypointContract.buildInputTensor(frames)
                policy.evaluate(cls.classify(tensor))
            }
            submit(gen) {
                classifying = false
                if (!isActive() || !cameraEnabled || revision != visionRevision) {
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
                                    "No reconocí la seña. Volvé a intentarlo. " +
                                    "Confianza ${(decision.confidence * 100).roundToInt()} %, " +
                                    "umbral ${(thresholdUsed * 100).roundToInt()} %.",
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

    /** Cambia de motor sin mezclar buffers ni contratos de entrada. */
    fun setRecognitionMode(mode: RecognitionMode) = submit(generation) {
        if (mode == recognitionMode) return@submit
        if (mode == RecognitionMode.PHRASE_EXPERIMENTAL && !capabilities.phraseVision) {
            notice = capabilities.phraseVisionDetail.ifBlank {
                "El modo frase todavía no está disponible."
            }
            publish()
            return@submit
        }
        visionRevision++
        phraseBuffer.cancel()
        phraseCandidate = null
        phraseCandidateStartMs = -1L
        phraseCaptureState = if (mode == RecognitionMode.PHRASE_EXPERIMENTAL) {
            PhraseCaptureState.READY
        } else {
            PhraseCaptureState.UNAVAILABLE
        }
        frameBuffer.clear()
        preRollBuffer.clear()
        segmenter = SignSegmenter(SegmenterConfig.defaults())
        observationFactory.reset()
        recognitionMode = mode
        visualState = if (cameraEnabled) {
            VisualChannelState.BUSCANDO_ENCUADRE
        } else {
            VisualChannelState.NO_DISPONIBLE
        }
        publish()
    }

    /** Inicia una captura de frase; nunca se activa automáticamente. */
    fun startPhraseCapture() = submit(generation) {
        if (!isActive() || !cameraEnabled || !capabilities.phraseVision ||
            recognitionMode != RecognitionMode.PHRASE_EXPERIMENTAL
        ) return@submit
        if (phraseCaptureState != PhraseCaptureState.READY) return@submit
        val start = lastVisionTimestampMs.takeIf { it > 0L } ?: now()
        phraseCandidate = null
        phraseCandidateStartMs = -1L
        phraseBuffer.start(start)
        phraseCaptureState = PhraseCaptureState.CAPTURING
        visualState = VisualChannelState.CAPTURANDO_SENA
        notice = null
        publish()
    }

    /** Termina la captura y deja el texto como candidato, sin publicarlo. */
    fun finishPhraseCapture() = submit(generation) {
        if (phraseCaptureState != PhraseCaptureState.CAPTURING) return@submit
        val capture = phraseBuffer.stop(lastVisionTimestampMs.takeIf { it > 0L } ?: now())
        if (capture == null || capture.frames.size < MIN_PHRASE_FRAMES) {
            phraseCaptureState = PhraseCaptureState.READY
            visualState = VisualChannelState.REARMANDO
            notice = "La frase fue demasiado corta. Volvé a intentarlo."
            publish()
            return@submit
        }
        if (capture.validFrameRatio < MIN_PHRASE_VALID_RATIO) {
            phraseCaptureState = PhraseCaptureState.READY
            visualState = VisualChannelState.CALIDAD_INSUFICIENTE
            notice = "No vi suficientes manos durante la frase. Volvé a intentarlo."
            publish()
            return@submit
        }
        val translator = sequenceTranslator
        if (translator == null || sequenceClassifying) {
            phraseCaptureState = PhraseCaptureState.READY
            notice = "La frase anterior todavía se está procesando."
            publish()
            return@submit
        }

        val tensor = runCatching { capture.buildInputTensor() }.getOrElse { error ->
            phraseCaptureState = PhraseCaptureState.READY
            notice = "No se pudo preparar la frase: ${error.message ?: "entrada inválida"}"
            publish()
            return@submit
        }
        val gen = generation
        val revision = visionRevision
        sequenceClassifying = true
        phraseCaptureState = PhraseCaptureState.PROCESSING
        visualState = VisualChannelState.REARMANDO
        publish()
        sequenceClassifierExecutor.execute {
            val result = runCatching { translator.translate(tensor) }
            submit(gen) {
                sequenceClassifying = false
                if (!isActive() || !cameraEnabled || revision != visionRevision ||
                    recognitionMode != RecognitionMode.PHRASE_EXPERIMENTAL
                ) return@submit
                result.fold(
                    onSuccess = { candidate ->
                        val decision = sequenceAcceptancePolicy.evaluate(candidate)
                        if (!decision.accepted) {
                            phraseCaptureState = PhraseCaptureState.READY
                            notice = decision.reason
                        } else {
                            phraseCandidate = candidate
                            phraseCandidateStartMs = capture.startTimestampMs
                            phraseCaptureState = PhraseCaptureState.CANDIDATE
                        }
                    },
                    onFailure = { error ->
                        phraseCaptureState = PhraseCaptureState.READY
                        notice = "No se pudo ejecutar el modelo de frases: " +
                            (error.message ?: "error desconocido")
                    },
                )
                publish()
            }
        }
    }

    fun cancelPhraseCapture() = submit(generation) {
        visionRevision++
        phraseBuffer.cancel()
        phraseCandidate = null
        phraseCandidateStartMs = -1L
        phraseCaptureState = if (capabilities.phraseVision) {
            PhraseCaptureState.READY
        } else {
            PhraseCaptureState.UNAVAILABLE
        }
        visualState = if (cameraEnabled) VisualChannelState.BUSCANDO_ENCUADRE else VisualChannelState.NO_DISPONIBLE
        publish()
    }

    /** Publica y ofrece a voz un candidato ya confirmado por la persona. */
    fun confirmPhraseCandidate(editedText: String? = null) = submit(generation) {
        val candidate = phraseCandidate ?: return@submit
        val text = editedText?.trim() ?: candidate.text
        if (text.isBlank()) {
            notice = "El texto de la frase no puede estar vacío."
            publish()
            return@submit
        }
        val turn = conversation.open(Speaker.DEAF, phraseCandidateStartMs.takeIf { it >= 0L } ?: now())
        conversation.publishTyped(turn.id(), text)
        if (capabilities.tts) {
            arbiter.enqueue(turn.id(), text, now())
        } else {
            conversation.updateVoice(turn.id(), VoiceState.NOT_SPOKEN)
            notice = "La frase se mostró, pero no hay una voz española sin conexión instalada."
        }
        phraseCandidate = null
        phraseCandidateStartMs = -1L
        phraseCaptureState = if (capabilities.phraseVision) {
            PhraseCaptureState.READY
        } else {
            PhraseCaptureState.UNAVAILABLE
        }
        publish()
    }

    fun rejectPhraseCandidate() = submit(generation) {
        phraseCandidate = null
        phraseCandidateStartMs = -1L
        phraseCaptureState = if (capabilities.phraseVision) {
            PhraseCaptureState.READY
        } else {
            PhraseCaptureState.UNAVAILABLE
        }
        notice = "Frase descartada."
        publish()
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

    fun toggleMicrophone() = submit(generation) {
        if (!isActive() || !capabilities.stt) return@submit
        microphoneEnabled = !microphoneEnabled
        if (microphoneEnabled) {
            speech?.start()
            if (arbiter.state() != AudioArbiter.State.IDLE) speech?.closeGate()
        } else {
            speech?.stop()
            if (partialTurnId >= 0) {
                conversation.markIncomplete(partialTurnId)
                partialTurnId = -1
            }
            arbiter.hearingSpeechActive(false, now())
        }
        if (arbiter.state() == AudioArbiter.State.IDLE) {
            audioState = if (microphoneEnabled) AudioChannelState.STT_ESCUCHANDO else AudioChannelState.STT_LISTO
        }
        publish()
    }

    fun toggleCamera() = submit(generation) {
        if (!isActive() || !activeVisionAvailable()) return@submit
        cameraEnabled = !cameraEnabled
        visionRevision++
        frameBuffer.clear()
        preRollBuffer.clear()
        phraseBuffer.cancel()
        phraseCandidate = null
        phraseCandidateStartMs = -1L
        observationFactory.reset()
        segmenter = SignSegmenter(SegmenterConfig.defaults())
        phraseCaptureState = if (cameraEnabled && capabilities.phraseVision &&
            recognitionMode == RecognitionMode.PHRASE_EXPERIMENTAL
        ) {
            PhraseCaptureState.READY
        } else if (capabilities.phraseVision && recognitionMode == RecognitionMode.PHRASE_EXPERIMENTAL) {
            PhraseCaptureState.READY
        } else {
            PhraseCaptureState.UNAVAILABLE
        }
        framing = FramingEvaluator.Issue.SIN_PERSONA
        visualState = if (cameraEnabled) VisualChannelState.BUSCANDO_ENCUADRE else VisualChannelState.NO_DISPONIBLE
        publish()
    }

    fun dismissNotice() = submit(generation) { notice = null; publish() }

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
        capabilities = capabilities.copy(
            vision = false,
            visionDetail = detail,
            phraseVision = false,
            phraseVisionDetail = detail,
        )
        phraseCaptureState = PhraseCaptureState.UNAVAILABLE
        visualState = VisualChannelState.NO_DISPONIBLE
        notice = detail
        publish()
    }

    // ------------------------------------------------------------------
    // canal de audio
    // ------------------------------------------------------------------

    private fun onSttPartial(text: String) {
        if (!isActive() || !microphoneEnabled) return
        if (partialTurnId < 0) {
            partialTurnId = conversation.open(Speaker.HEARING, now()).id()
        }
        conversation.updatePartial(partialTurnId, text)
        audioState = AudioChannelState.STT_TRANSCRIBIENDO
        publish()
    }

    private fun onSttFinal(text: String) {
        if (!isActive() || !microphoneEnabled) return
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

    private fun activeVisionAvailable(): Boolean = when (recognitionMode) {
        RecognitionMode.SINGLE_SIGN -> capabilities.vision
        RecognitionMode.PHRASE_EXPERIMENTAL -> capabilities.phraseVision
    }

    private fun hasTurn(id: Long): Boolean =
        runCatching { conversation.get(id) }.isSuccess

    private fun now(): Long = android.os.SystemClock.elapsedRealtime()

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun submit(gen: Int, block: () -> Unit) {
        if (executor.isShutdown) return
        try {
            executor.execute { if (gen == generation) block() }
        } catch (_: java.util.concurrent.RejectedExecutionException) {
            // Callback de un sensor que terminó durante onCleared.
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
            recognitionMode = recognitionMode,
            phraseCapture = phraseCaptureState,
            phraseCandidate = phraseCandidate,
            notice = notice,
            microphoneEnabled = microphoneEnabled,
            cameraEnabled = cameraEnabled,
        )
    }

    companion object {
        /** Umbral inicial de laboratorio (D03); no aprobado de producción. */
        const val DEFAULT_THRESHOLD = 0.90f
        const val MIN_THRESHOLD = 0.50f
        const val MAX_THRESHOLD = 0.99f
        /** Vigencia de la pausa; pasada, la sesión se invalida (D07). */
        const val PAUSE_TTL_MS = 2L * 60 * 1000
        private const val MIN_PHRASE_FRAMES = 3
        private const val MIN_PHRASE_VALID_RATIO = 0.10f
        private const val PRE_ROLL_FRAMES = 8
    }
}
