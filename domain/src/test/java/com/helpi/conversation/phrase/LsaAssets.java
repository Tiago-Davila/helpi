package com.helpi.conversation.phrase;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/** Lee los JSON reales de app/src/main/assets/lsa para probar los datos que se despliegan. */
final class LsaAssets {

    private LsaAssets() {
    }

    /** Devuelve el contenido de un asset; la ruta la fija domain/build.gradle.kts. */
    static String read(String name) {
        String dir = System.getProperty("lsa.assets");
        if (dir == null) {
            throw new IllegalStateException("falta la propiedad lsa.assets");
        }
        try {
            return new String(Files.readAllBytes(Paths.get(dir, name)), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("no se pudo leer " + name, e);
        }
    }
}
