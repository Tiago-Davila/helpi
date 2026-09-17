package com.helpi.conversation.lsa.sequence

/** Motor visual elegido por la persona; Eva sigue siendo el valor inicial. */
enum class RecognitionMode {
    SINGLE_SIGN,
    PHRASE_EXPERIMENTAL,
}

/** Estado visible de la captura explícita del modo frase. */
enum class PhraseCaptureState {
    UNAVAILABLE,
    READY,
    CAPTURING,
    PROCESSING,
    CANDIDATE,
}
