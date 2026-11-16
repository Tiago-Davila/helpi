package com.helpi.conversation.phrase;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** Lee los JSON reales de app/src/main/assets/lse para probar los datos que se despliegan. */
final class LseAssets {

    private LseAssets() {
    }

    /** Devuelve el contenido de un asset; la ruta la fija domain/build.gradle.kts. */
    static String read(String name) {
        String dir = System.getProperty("lse.assets");
        if (dir == null) {
            throw new IllegalStateException("falta la propiedad lse.assets");
        }
        try {
            return new String(Files.readAllBytes(Paths.get(dir, name)), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("no se pudo leer " + name, e);
        }
    }

    /** Glosas del catálogo de LSE, en orden de índice. */
    static List<String> catalog() {
        JSONArray array = new JSONObject(read("catalogo_senas.json")).getJSONArray("glosas");
        List<String> glosses = new ArrayList<>(array.length());
        for (int i = 0; i < array.length(); i++) {
            glosses.add(array.getString(i));
        }
        return glosses;
    }

    /** Versiones que declara el manifiesto de LSE: modelo y catálogo. */
    static String[] manifestVersions() {
        JSONObject manifest = new JSONObject(read("manifest.json"));
        return new String[] {manifest.getString("modelVersion"), manifest.getString("catalogVersion")};
    }
}
