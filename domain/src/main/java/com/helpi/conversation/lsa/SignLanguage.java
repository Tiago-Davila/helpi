package com.helpi.conversation.lsa;

/** Lengua de señas del canal visual. Cada una tiene su propio modelo y catálogo. */
public enum SignLanguage {
    /** LSA: umbral conservador de Eva desde LSA64. */
    LSA("lsa", 0.90f),
    /** ASL: todavía sin modelo; mismo criterio conservador que LSA. */
    ASL("asl", 0.90f),
    /**
     * LSE: eva-lse300-v1 recomienda 0,47 (90 % de exactitud en lo aceptado en
     * validación); se usa el mínimo que admite la app.
     */
    LSE("lse", 0.50f);

    private final String code;
    private final float defaultConfidenceThreshold;

    SignLanguage(String code, float defaultConfidenceThreshold) {
        this.code = code;
        this.defaultConfidenceThreshold = defaultConfidenceThreshold;
    }

    /** Código que declaran el manifiesto y la carpeta de artefactos de la lengua. */
    public String code() {
        return code;
    }

    /** Umbral inicial de la compuerta de confianza para el modelo de esta lengua. */
    public float defaultConfidenceThreshold() {
        return defaultConfidenceThreshold;
    }
}
