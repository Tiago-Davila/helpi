package com.helpi.conversation.lsa;

/** Lengua de señas del canal visual. Cada una tiene su propio modelo y catálogo. */
public enum SignLanguage {
    LSA("lsa"),
    ASL("asl"),
    LSE("lse");

    private final String code;

    SignLanguage(String code) {
        this.code = code;
    }

    /** Código que declaran el manifiesto y la carpeta de artefactos de la lengua. */
    public String code() {
        return code;
    }
}
