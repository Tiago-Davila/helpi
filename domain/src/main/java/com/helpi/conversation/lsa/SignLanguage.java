package com.helpi.conversation.lsa;

import java.util.Locale;

/**
 * Lengua de señas que reconoce Eva. Cada una tiene su carpeta de assets con modelo,
 * catálogo y manifiesto propios: un índice de clase solo significa algo junto al catálogo
 * de su misma lengua.
 */
public enum SignLanguage {
    LSA("lsa", "Lengua de Señas Argentina", "lsa-manifest.json", 0.90f),
    ASL("asl", "American Sign Language", "manifest.json", 0.90f),
    LSE("lse", "Lengua de Signos Española", "manifest.json", 0.47f);

    private final String code;
    private final String displayName;
    private final String manifestFile;
    private final float recommendedThreshold;

    SignLanguage(String code, String displayName, String manifestFile, float recommendedThreshold) {
        this.code = code;
        this.displayName = displayName;
        this.manifestFile = manifestFile;
        this.recommendedThreshold = recommendedThreshold;
    }

    /** Código del manifiesto y nombre de la carpeta de assets. */
    public String code() {
        return code;
    }

    /** Nombre completo para mostrar en la interfaz. */
    public String displayName() {
        return displayName;
    }

    /** Ruta del modelo LiteRT dentro de assets. */
    public String modelAsset() {
        return code + "/modelo_" + code + ".tflite";
    }

    /** Ruta del catálogo de glosas dentro de assets. */
    public String catalogAsset() {
        return code + "/catalogo_senas.json";
    }

    /** Ruta del manifiesto dentro de assets; LSA conserva su nombre histórico. */
    public String manifestAsset() {
        return code + "/" + manifestFile;
    }

    /** Umbral de confianza que recomendó el entrenamiento de esta lengua. */
    public float recommendedThreshold() {
        return recommendedThreshold;
    }

    /** Lengua con ese código, o null si no es ninguna conocida. */
    public static SignLanguage fromCode(String code) {
        if (code == null) {
            return null;
        }
        String normalized = code.toLowerCase(Locale.ROOT);
        for (SignLanguage language : values()) {
            if (language.code.equals(normalized)) {
                return language;
            }
        }
        return null;
    }
}
