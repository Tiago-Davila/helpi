# Implementation Plan: Reconocimiento en varias lenguas de señas

**Branch**: `003-multilengua-senas` | **Spec**: [spec.md](spec.md)

## Resumen técnico

- `domain`: enum `SignLanguage` (Java puro) con código de carpeta, nombre del modelo y
  del manifiesto, y umbral recomendado.
- `ModelBundle.load(context, language)` lee `<código>/`, exige `language` en el
  manifiesto igual a la carpeta y expone `license` y `attribution`. Se mantienen todas
  las verificaciones actuales.
- `SignLanguageStore` (SharedPreferences) guarda la lengua elegida y el umbral de cada
  lengua.
- `ClassifierSlot` contiene el único clasificador cargado y una época que cambia en cada
  reemplazo; al reemplazar cierra el anterior en el executor de LiteRT.
- `SessionCoordinator.setSignLanguage`: descarta buffers, sube `visionRevision`, cierra
  el clasificador, carga el bundle nuevo y publica el motivo si falla. Modo frase y
  plantillas solo con LSA.

## Estructura de assets

```
assets/lsa/modelo_lsa.tflite  catalogo_senas.json  lsa-manifest.json
assets/asl/modelo_asl.tflite  catalogo_senas.json  manifest.json
assets/lse/modelo_lse.tflite  catalogo_senas.json  manifest.json
```

`noCompress += listOf("tflite", "task")` filtra por extensión: cubre las carpetas nuevas.

## Pruebas

- `ModelBundleTest`: cada lengua carga desde su carpeta; manifiesto de otra lengua →
  `Invalid`; carpeta faltante → `Missing` solo para esa lengua.
- `ClassifierSlotTest`: reemplazar cierra el anterior y cambia la época.
- `SignLanguageTest` (domain) y `SessionCoordinatorTest`: cambiar de lengua publica la
  lengua y su umbral, y una lengua sin paquete inhabilita la visión sin volver a LSA.
- Memoria: medición manual en dispositivo (Android Studio Profiler) antes y después.

## Fuera de alcance

Descarga bajo demanda de modelos; modo frase para ASL/LSE; entrenamiento (vive en
`entrenamiento-modelo`).
