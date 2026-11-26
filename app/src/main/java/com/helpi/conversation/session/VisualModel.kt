package com.helpi.conversation.session

import android.Manifest
import android.content.Context
import com.helpi.conversation.lsa.ModelBundle
import com.helpi.conversation.lsa.ModelBundleResult
import com.helpi.conversation.lsa.PhraseAssets
import com.helpi.conversation.lsa.SignAcceptancePolicy
import com.helpi.conversation.lsa.SignCatalog
import com.helpi.conversation.lsa.SignDecision
import com.helpi.conversation.lsa.SignLanguage
import com.helpi.conversation.lsa.VisualClassifier
import java.util.concurrent.Executors

/** Modelo de la lengua elegida: carga modelo y catálogo como unidad verificada y lo libera. */
internal class VisualModel(
    private val context: Context,
    private val data: SessionData,
    private val delivery: SentenceDelivery,
    private val loadBundle: (SignLanguage) -> ModelBundleResult,
    private val createClassifier: (ModelBundle) -> VisualClassifier
) {
    /** Piezas con las que se clasifica un segmento, tomadas juntas. */
    class Ready(
        private val classifier: VisualClassifier,
        private val policy: SignAcceptancePolicy,
        val catalog: SignCatalog
    ) {
        /** Clasifica y decide; corre en el executor de inferencia. */
        fun classify(tensor: FloatArray, contextWeights: FloatArray?): SignDecision =
            policy.evaluate(classifier.classify(tensor), contextWeights)
    }

    private val inferenceExecutor = Executors.newSingleThreadExecutor()
    private var classifier: VisualClassifier? = null
    private var policy: SignAcceptancePolicy? = null
    private var bundle: ModelBundle? = null

    /** El modelo cargado, o null si falta alguna pieza. */
    fun ready(): Ready? {
        val loadedClassifier = classifier
        val loadedPolicy = policy
        val catalog = bundle?.catalog
        return if (loadedClassifier != null && loadedPolicy != null && catalog != null) {
            Ready(loadedClassifier, loadedPolicy, catalog)
        } else {
            null
        }
    }

    /** Carga modelo y catálogo de la lengua elegida; devuelve si hay canal y por qué no. */
    fun load(): ChannelAvailability {
        if (!context.isGranted(Manifest.permission.CAMERA)) {
            return ChannelAvailability(false, "permiso de cámara denegado")
        }
        return when (val result = loadBundle(data.signLanguage)) {
            is ModelBundleResult.Ready -> open(result.bundle)
            is ModelBundleResult.Missing -> ChannelAvailability(false, "falta ${result.asset}")
            is ModelBundleResult.Invalid -> ChannelAvailability(false, result.cause)
        }
    }

    // LiteRT informa un modelo incompatible con excepciones de varios tipos.
    @Suppress("TooGenericExceptionCaught")
    private fun open(loaded: ModelBundle): ChannelAvailability {
        bundle = loaded
        delivery.phraseAssets = PhraseAssets.load(context, loaded.manifest, loaded.catalog)
        return try {
            classifier = createClassifier(loaded)
            policy = policyFor(loaded)
            ChannelAvailability(true)
        } catch (e: Exception) {
            ChannelAvailability(false, "modelo incompatible: ${e.message}")
        }
    }

    /** Aplica el umbral vigente sin volver a cargar el modelo. */
    fun applyThreshold() {
        bundle?.let { policy = policyFor(it) }
    }

    /** Suelta el modelo cargado; el intérprete se cierra después de la inferencia en curso. */
    fun release() {
        classifier?.let { closing -> inferenceExecutor.execute { closing.close() } }
        classifier = null
        policy = null
        bundle = null
        delivery.forgetPhraseAssets()
    }

    /** Corre una inferencia fuera del executor de la sesión. */
    fun infer(block: () -> Unit) = inferenceExecutor.execute { block() }

    fun shutdown() = inferenceExecutor.shutdown()

    // Letras y tiempo se aceptan con el umbral reducido; si el léxico no cubre justo las
    // clases del modelo, todas usan el general.
    private fun policyFor(loaded: ModelBundle): SignAcceptancePolicy {
        val general = data.thresholds.current
        val numClasses = loaded.manifest.numClasses
        val perClass = delivery.phraseAssets.classThresholds(general)
            ?.takeIf { it.size == numClasses }
        return SignAcceptancePolicy(
            general,
            loaded.manifest.outputsProbabilities,
            numClasses,
            perClass
        )
    }
}
