package com.helpi.conversation.session

import android.content.Context
import com.helpi.conversation.chat.Speaker
import com.helpi.conversation.chat.VoiceState
import com.helpi.conversation.lsa.ModelBundle
import com.helpi.conversation.lsa.ModelBundleResult
import com.helpi.conversation.lsa.PhraseAssets
import com.helpi.conversation.lsa.SignClassifier
import com.helpi.conversation.lsa.SignDecision
import com.helpi.conversation.lsa.SignLanguage
import com.helpi.conversation.lsa.SignLanguageStore
import com.helpi.conversation.lsa.VisualClassifier
import com.helpi.conversation.lsa.sequence.RecognitionMode
import com.helpi.conversation.observation.NoResultCause
import com.helpi.conversation.observation.RecognitionObserver
import com.helpi.conversation.phrase.PhraseBuilder
import com.helpi.conversation.phrase.RecognizedSign
import com.helpi.conversation.vision.CameraCaptureMetrics
import com.helpi.conversation.vision.FramingEvaluator
import com.helpi.conversation.vision.LandmarkExtractionMetrics
import com.helpi.conversation.vision.LandmarkFrame
import com.helpi.conversation.vision.SamplingProfile
import com.helpi.conversation.vision.VisionMetrics
import java.util.concurrent.ScheduledFuture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Único coordinador de la sesión: recibe eventos de ambos canales, mantiene
 * el ciclo de vida y publica el estado. Cada canal vive en su propia clase;
 * todo el estado muta en [SessionLoop], un executor serial (único escritor).
 *
 * La conversación vive exclusivamente en memoria y se descarta al cerrar.
 */
class SessionCoordinator(
    context: Context,
    recognitionObserver: RecognitionObserver = RecognitionObserver.NO_OP,
    initialDeliveryMode: DeliveryMode = DeliveryMode.CONVERSATION,
    private val languageStore: SignLanguageStore = SignLanguageStore(context),
    loadBundle: (SignLanguage) -> ModelBundleResult = { ModelBundle.load(context, it) },
    createClassifier: (ModelBundle) -> VisualClassifier = ::SignClassifier
) {
    private val loop = SessionLoop()
    private val data = SessionData(languageStore.load())

    private val speechInput = SpeechInputChannel(context, loop, data, ::publish)
    private val voice = VoiceChannel(context, loop, data, speechInput, ::publish)
    private val delivery = SentenceDelivery(data, voice)
    private val sentence = SentenceChannel(data, delivery)

    private val outlet = RecognitionOutlet(recognitionObserver, initialDeliveryMode)
    private val model = VisualModel(context, data, delivery, loadBundle, createClassifier)
    private val recognition = SignRecognition(loop, data, model, outlet, sentence, ::publish)
    private val signs = SignCapture(loop, data, outlet, sentence, recognition)
    private val phrase = PhraseChannel(context, loop, data, ::publish)
    private val visual = VisualChannel(data, signs, phrase, ::publish)

    private var ticker: ScheduledFuture<*>? = null
    private var pausedAtMs: Long = 0

    private val _uiState = MutableStateFlow(
        SessionUiState(
            confidenceThreshold = data.thresholds.current,
            signLanguage = data.signLanguage
        )
    )
    val uiState: StateFlow<SessionUiState> = _uiState.asStateFlow()

    init {
        speechInput.onSpeechActivity = voice::hearingSpeechActive
    }

    // ------------------------------------------------------------------
    // ciclo de vida
    // ------------------------------------------------------------------

    fun prepare(autoStart: Boolean = false) = loop.submit {
        if (data.stateMachine.current() == SessionState.CERRADA) {
            data.resetAfterClose()
            phrase.clearCandidate(available = false)
        }
        data.visionMetrics = VisionMetrics()
        data.samplingProfile = SamplingProfile.IDLE
        if (!data.stateMachine.canTransition(SessionState.PREPARANDO)) return@submit
        data.stateMachine.transition(SessionState.PREPARANDO)
        publish()

        val visionChannel = model.load()
        val phraseChannel = phrase.prepare()
        val sttChannel = speechInput.prepare()
        voice.prepare()
        data.capabilities = data.capabilities.copy(
            vision = visionChannel.available,
            visionDetail = visionChannel.detail,
            phraseVision = phraseChannel.available,
            phraseVisionDetail = phraseChannel.detail,
            stt = sttChannel.available,
            sttDetail = sttChannel.detail
        )
        data.audioState = if (sttChannel.available) {
            AudioChannelState.STT_LISTO
        } else {
            AudioChannelState.NO_DISPONIBLE
        }

        // El teclado siempre es un canal disponible, aun si se deniegan los
        // sensores. La sesión no queda bloqueada por una capacidad opcional.
        data.stateMachine.transition(SessionState.LISTA)
        publish()
        if (autoStart) startConversation()
    }

    fun startConversation() = loop.submit {
        if (!data.stateMachine.canTransition(data.targetActiveState)) return@submit
        data.stateMachine.transition(data.targetActiveState)
        if (data.capabilities.stt) {
            speechInput.start()
            data.audioState = AudioChannelState.STT_ESCUCHANDO
        }
        data.restartVisualState(data.activeVisionAvailable)
        // reloj del árbitro (vencimientos, guarda, watchdog)
        ticker?.cancel(false)
        ticker = loop.every(TICK_MS) {
            if (data.isActive) {
                voice.tick()
                publish()
            }
        }
        publish()
    }

    /** Segundo plano / bloqueo: pausa sensores y descarta lo pendiente. */
    fun pause() = loop.submit {
        if (!data.stateMachine.canTransition(SessionState.PAUSADA)) return@submit
        pausedAtMs = loop.now()
        data.stateMachine.transition(SessionState.PAUSADA)
        voice.stopSpeaking()
        speechInput.stop()
        sentence.close()
        voice.discardPending()
        visual.discardCapture()
        phrase.discard(data.capabilities.phraseVision)
        data.samplingProfile = SamplingProfile.IDLE
        speechInput.interruptPartial()
        publish()
    }

    /**
     * Reanudación EXPLÍCITA ("Reanudar"): nunca automática. Si la pausa
     * superó la vigencia, la sesión se invalida y el historial se descarta;
     * se verifica el vencimiento aunque Android haya suspendido los timers.
     */
    fun resume() = loop.submit {
        if (data.stateMachine.current() != SessionState.PAUSADA) return@submit
        if (loop.now() - pausedAtMs > PAUSE_TTL_MS) {
            data.notice = "La pausa superó los 2 minutos: la conversación se descartó."
            doClose()
            return@submit
        }
        // se re-verifica el estado de los canales antes de volver a mostrar
        data.stateMachine.transition(SessionState.PREPARANDO)
        data.stateMachine.transition(SessionState.LISTA)
        data.stateMachine.transition(data.targetActiveState)
        if (data.capabilities.stt && data.microphoneEnabled) {
            speechInput.start()
            data.audioState = AudioChannelState.STT_ESCUCHANDO
        }
        if (data.activeVisionAvailable && data.cameraEnabled) {
            data.visualState = VisualChannelState.BUSCANDO_ENCUADRE
        }
        publish()
    }

    /** Cierre explícito: descarta el historial y libera recursos. */
    fun closeSession() = loop.submit { doClose() }

    private fun doClose() {
        loop.startNewGeneration()
        if (data.stateMachine.canTransition(SessionState.CERRANDO)) {
            data.stateMachine.transition(SessionState.CERRANDO)
        }
        voice.close()
        ticker?.cancel(false)
        ticker = null
        speechInput.close()
        recognition.release()
        phrase.close()
        sentence.reset()
        visual.discardCapture()
        data.lastRecognition = null
        data.visionMetrics = VisionMetrics()
        data.samplingProfile = SamplingProfile.IDLE
        data.recognitionMode = RecognitionMode.SINGLE_SIGN
        data.lastVisionTimestampMs = 0L
        delivery.clear()
        data.conversation.clear()
        if (data.stateMachine.canTransition(SessionState.CERRADA)) {
            data.stateMachine.transition(SessionState.CERRADA)
        }
        publish()
    }

    fun shutdown() = loop.enqueue {
        doClose()
        model.shutdown()
        phrase.shutdown()
        loop.shutdown()
    }

    // ------------------------------------------------------------------
    // canal visual
    // ------------------------------------------------------------------

    /** Entrada desde HolisticExtractor (worker de MediaPipe): solo publica. */
    fun onLandmarks(frame: LandmarkFrame) = loop.submit { visual.process(frame) }

    /** Telemetría agregada de CameraX: no conserva cuadros ni video. */
    fun onCameraMetrics(metrics: CameraCaptureMetrics) = loop.submit {
        data.visionMetrics = data.visionMetrics.withCamera(metrics)
        publish()
    }

    /** Telemetría agregada del extractor: permite separar cámara de MediaPipe. */
    fun onLandmarkMetrics(metrics: LandmarkExtractionMetrics) = loop.submit {
        data.visionMetrics = data.visionMetrics.withLandmarks(metrics)
        publish()
    }

    /** Un fallo real de CameraX/MediaPipe inhabilita el canal con causa. */
    fun reportVisionUnavailable(detail: String) = loop.submit {
        sentence.close()
        data.capabilities = data.capabilities.copy(
            vision = false,
            visionDetail = detail,
            phraseVision = false,
            phraseVisionDetail = detail
        )
        phrase.markUnavailable()
        data.visualState = VisualChannelState.NO_DISPONIBLE
        data.notice = detail
        publish()
    }

    /**
     * Cambia el destino de lo reconocido. El protocolo de evaluación usa
     * [DeliveryMode.OBSERVER_ONLY] mientras está en pantalla; la conversación
     * vuelve a publicar al salir. La oración abierta se cierra antes y el
     * segmento en curso se descarta: ninguno cruza de un destino al otro.
     *
     * No depende de la generación: el cierre de una sesión no puede hacer que
     * el cambio se pierda y la conversación quede muda.
     */
    fun setDeliveryMode(mode: DeliveryMode) = loop.enqueue {
        if (mode == outlet.mode) return@enqueue
        sentence.close()
        outlet.mode = mode
        visual.discardSegment()
        data.lastRecognition = null
        publish()
    }

    /** Cambia de motor sin mezclar buffers ni contratos de entrada. */
    fun setRecognitionMode(mode: RecognitionMode) = loop.submit {
        if (mode == data.recognitionMode) return@submit
        if (mode == RecognitionMode.PHRASE_EXPERIMENTAL && !data.capabilities.phraseVision) {
            data.notice = data.capabilities.phraseVisionDetail.ifBlank {
                "El modo frase todavía no está disponible."
            }
            publish()
            return@submit
        }
        sentence.close()
        visual.discardCapture()
        phrase.discard(available = mode == RecognitionMode.PHRASE_EXPERIMENTAL)
        data.lastRecognition = null
        data.recognitionMode = mode
        data.samplingProfile = SamplingProfile.IDLE
        data.restartVisualState(data.cameraEnabled)
        publish()
    }

    /**
     * Cambia la lengua de señas: guarda la elección, descarta la ventana y la
     * clasificación en curso, libera el intérprete anterior y carga el modelo
     * nuevo. Si el modelo de la lengua elegida falla, el canal visual queda
     * inhabilitado con el motivo: nunca se vuelve a otra lengua en silencio.
     */
    fun setSignLanguage(language: SignLanguage) = loop.submit {
        if (language == data.signLanguage) return@submit
        languageStore.save(language)
        // La frase en curso se cierra con el catálogo de la lengua anterior.
        sentence.close()
        data.signLanguage = language
        data.thresholds.use(language)
        visual.discardSegment()
        data.lastRecognition = null
        if (data.isPrepared) reloadModel()
        publish()
    }

    /** Cambia el modelo cargado por el de la lengua vigente. */
    private fun reloadModel() {
        recognition.release()
        val channel = model.load()
        data.capabilities =
            data.capabilities.copy(vision = channel.available, visionDetail = channel.detail)
        if (!data.isActive) return
        data.restartVisualState(data.activeVisionAvailable && data.cameraEnabled)
        val target = data.targetActiveState
        if (data.stateMachine.current() != target && data.stateMachine.canTransition(target)) {
            data.stateMachine.transition(target)
        }
    }

    /** Ajusta la compuerta de la lengua actual sin cambiar ni volver a cargar el modelo. */
    fun setConfidenceThreshold(value: Float) = loop.submit {
        data.thresholds.set(data.signLanguage, value)
        model.applyThreshold()
        publish()
    }

    fun toggleCamera() = loop.submit {
        if (!data.isActive || !data.activeVisionAvailable) return@submit
        sentence.close()
        data.cameraEnabled = !data.cameraEnabled
        visual.discardCapture()
        phrase.discard(available = data.capabilities.phraseVision && phrase.isSelected())
        data.lastRecognition = null
        data.framing = FramingEvaluator.Issue.SIN_PERSONA
        data.restartVisualState(data.cameraEnabled)
        publish()
    }

    // ------------------------------------------------------------------
    // modo frase
    // ------------------------------------------------------------------

    /** Inicia una captura de frase; nunca se activa automáticamente. */
    fun startPhraseCapture() = loop.submit { phrase.start() }

    /** Termina la captura y deja el texto como candidato, sin publicarlo. */
    fun finishPhraseCapture() = loop.submit { phrase.finish() }

    fun cancelPhraseCapture() = loop.submit {
        data.visionRevision++
        phrase.discard(data.capabilities.phraseVision)
        data.samplingProfile = SamplingProfile.IDLE
        data.restartVisualState(data.cameraEnabled)
        publish()
    }

    /** Publica y ofrece a voz un candidato ya confirmado por la persona. */
    fun confirmPhraseCandidate(editedText: String? = null) = loop.submit {
        val candidate = phrase.candidate ?: return@submit
        val text = editedText?.trim() ?: candidate.text
        if (text.isBlank()) {
            data.notice = "El texto de la frase no puede estar vacío."
        } else {
            publishTyped(
                text,
                startMs = phrase.candidateStart(),
                noticeWithoutVoice =
                "La frase se mostró, pero no hay una voz española sin conexión instalada."
            )
            phrase.clearCandidate(data.capabilities.phraseVision)
        }
        publish()
    }

    fun rejectPhraseCandidate() = loop.submit {
        phrase.clearCandidate(data.capabilities.phraseVision)
        data.notice = "Frase descartada."
        publish()
    }

    // ------------------------------------------------------------------
    // conversación
    // ------------------------------------------------------------------

    /** "No quise decir eso". */
    fun markIncorrect(turnId: Long) = loop.submit {
        val cancelled = voice.cancel(turnId)
        if (cancelled) data.conversation.updateVoice(turnId, VoiceState.CANCELLED)
        data.conversation.markIncorrect(turnId, loop.now())
        if (!cancelled) {
            // ya se pronunció: aviso determinístico por la misma cola
            val note = data.conversation.ordered().last()
            voice.enqueue(note.id(), "La traducción anterior fue marcada como incorrecta")
        }
        publish()
    }

    /** Texto escrito por la persona sorda: se publica y se pronuncia por TTS. */
    fun submitTyped(text: String) = loop.submit {
        val normalized = text.trim()
        if (!data.isActive || normalized.isEmpty()) return@submit
        publishTyped(
            normalized,
            startMs = loop.now(),
            noticeWithoutVoice =
            "El texto se mostró, pero no hay una voz española sin conexión instalada."
        )
        publish()
    }

    /** Publica un texto de la persona sorda, lo ofrece a voz y avisa si no hay voz. */
    private fun publishTyped(text: String, startMs: Long, noticeWithoutVoice: String) {
        val turn = data.conversation.open(Speaker.DEAF, startMs)
        data.conversation.publishTyped(turn.id(), text)
        if (!voice.offer(turn.id(), text)) data.notice = noticeWithoutVoice
    }

    /** Publica la oración en espera, con el texto editado si la persona lo cambió. */
    fun confirmSentenceCandidate(editedText: String?) = loop.submit {
        delivery.confirm(editedText)
        publish()
    }

    /** Descarta la oración en espera: no se publica ni se vocaliza. */
    fun rejectSentenceCandidate() = loop.submit {
        delivery.reject()
        publish()
    }

    /** Repite por voz un turno final de la persona sorda. */
    fun repeatTurn(turnId: Long) = loop.submit {
        if (!data.isActive || !data.capabilities.tts || !data.hasTurn(turnId)) return@submit
        val turn = data.conversation.get(turnId)
        val speakable = turn.speaker() == Speaker.DEAF && turn.text().isNotBlank()
        if (!speakable || PhraseBuilder.GAP in turn.glosses()) return@submit
        data.conversation.updateVoice(turnId, VoiceState.PENDING)
        voice.enqueue(turnId, turn.text().removePrefix("Seña reconocida: "))
        publish()
    }

    fun toggleMicrophone() = loop.submit {
        if (!data.isActive || !data.capabilities.stt) return@submit
        data.microphoneEnabled = !data.microphoneEnabled
        if (data.microphoneEnabled) {
            speechInput.start()
            if (!voice.isIdle) speechInput.setGate(open = false)
        } else {
            speechInput.stop()
            speechInput.interruptPartial()
            voice.hearingSpeechActive(false)
        }
        if (voice.isIdle) {
            data.audioState = if (data.microphoneEnabled) {
                AudioChannelState.STT_ESCUCHANDO
            } else {
                AudioChannelState.STT_LISTO
            }
        }
        publish()
    }

    fun dismissNotice() = loop.submit {
        data.notice = null
        publish()
    }

    // ------------------------------------------------------------------
    // accesos para tests, sin modelo ni cámara
    // ------------------------------------------------------------------

    /**
     * Publica una oración ya cerrada como frase con sus glosas debajo. La voz
     * dice la frase recién ahora; si alguna seña quedó como hueco, no se
     * pronuncia nada para no decir en voz alta una oración incompleta.
     */
    fun publishSentence(signs: List<RecognizedSign>, startMs: Long) = loop.submit {
        if (!data.isActive || !outlet.reachesConversation || signs.isEmpty()) return@submit
        delivery.deliver(data.conversation.open(Speaker.DEAF, startMs).id(), signs)
        publish()
    }

    internal fun reportSegmentStarted(startMs: Long) = outlet.segmentStarted(startMs)

    internal fun reportNoResult(cause: NoResultCause) = outlet.noResult(cause)

    internal fun dispatchDecision(
        decision: SignDecision,
        thresholdUsed: Float,
        segmentEndMs: Long,
        conversationDelivery: () -> Unit
    ) = outlet.decision(decision, thresholdUsed, segmentEndMs, conversationDelivery)

    /** Suma un intento a la oración en curso; para probar el armado sin modelo ni cámara. */
    internal fun recognizeForSentence(sign: RecognizedSign?, startMs: Long, endMs: Long) =
        loop.submit {
            if (!data.isActive || !outlet.reachesConversation) return@submit
            sentence.turn(startMs)
            sentence.add(sign, endMs)
            publish()
        }

    /** Avanza el reloj de la cámara sin cuadros; para probar el cierre por pausa. */
    internal fun advanceVisionClock(timestampMs: Long) = loop.submit {
        sentence.closeAfterPause(timestampMs)
        publish()
    }

    /** Reemplaza léxico y plantillas, para probar el armado sin cargar el modelo. */
    internal fun usePhraseAssets(assets: PhraseAssets) = loop.submit {
        delivery.phraseAssets = assets
    }

    /** Cuadros de la ventana en curso (pre-roll incluido). */
    internal fun bufferedVisionFrames(): Int = loop.query { signs.bufferedFrames }

    private fun publish() {
        _uiState.value = SessionUiState(
            session = data.stateMachine.current(),
            visual = data.visualState,
            audio = data.audioState,
            framing = data.framing,
            signingDistance = data.signingDistance,
            turns = data.conversation.ordered().toList(),
            capabilities = data.capabilities,
            confidenceThreshold = data.thresholds.current,
            signLanguage = data.signLanguage,
            lastRecognition = data.lastRecognition,
            visionMetrics = data.visionMetrics,
            samplingProfile = data.samplingProfile,
            recognitionMode = data.recognitionMode,
            phraseCapture = phrase.state,
            phraseCandidate = phrase.candidate,
            sentenceCandidate = delivery.candidate,
            notice = data.notice,
            microphoneEnabled = data.microphoneEnabled,
            cameraEnabled = data.cameraEnabled
        )
    }

    companion object {
        const val MIN_THRESHOLD = ConfidenceThresholds.MIN
        const val MAX_THRESHOLD = ConfidenceThresholds.MAX

        /** Vigencia de la pausa; pasada, la sesión se invalida (D07). */
        const val PAUSE_TTL_MS = 2L * 60 * 1000
        private const val TICK_MS = 200L
    }
}
