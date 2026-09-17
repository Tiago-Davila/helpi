# Integración en Android

Guía para integrar el paquete experimental LSA-T en la app Android. Esta
integración es independiente de Eva: el modelo estable sigue viviendo en
`app/src/main/assets/lsa/` y no se reemplaza ni se usa como fallback.

El paquete LSA-T solo se habilita después de validar encoder, decoder,
vocabulario, manifiesto y fixture como una unidad. Los archivos actuales de
`exports/` son evidencia de exportaciones anteriores; no se deben copiar al
APK como si fueran un paquete compatible.

---

## 1. Paquete aprobado a copiar

Cuando exista una exportación aprobada, copiar sus archivos a
`app/src/main/assets/lsa_t/`:

| Archivo                | Origen                          | Descripcion |
|-------------------------|----------------------------------|-------------|
| `encoder_int8.tflite` | exportación aprobada | Encoder del paquete, sin Flex ni ops personalizadas |
| `decoder_int8.tflite` | exportación aprobada | Decoder autoregresivo del mismo checkpoint |
| `vocab.json` | exportación aprobada | Vocabulario con los mismos IDs del decoder |
| `android-manifest.json` | exportación aprobada | Contrato, layouts, hashes y política |
| `fixture_android.json` | exportación aprobada | Fixture para la prueba instrumentada |

El manifiesto que consume Android debe declarar `artifactVersion` con prefijo
`lsa-t-`, entrada `B,T,126`, entrada del encoder `B,126,T`, memoria del
decoder `B,256,T`, muestreo entero y padding cero. La app verifica los hashes
antes de crear los intérpretes.

---

## 2. Dependencias en `build.gradle`

```gradle
dependencies {
    // MediaPipe Tasks HolisticLandmarker (compartido con Eva)
    implementation "com.google.mediapipe:tasks-vision:0.10.14"

    // LiteRT, runtime usado por la app
    implementation "com.google.ai.edge.litert:litert:1.1.2"
}
```

No agregar Select TF Ops, Flex ni ONNX Runtime como solución a un export
incompatible. Si el modelo necesita una operación no soportada, se debe
corregir la exportación.

---

## 3. Pipeline de inferencia

```
Camara (30 fps)
   │
   ▼
MediaPipe HolisticLandmarker  →  21 landmarks x mano x frame (x,y,z)
   │
   ▼
Captura explícita (hasta 12 s) → buffer separado de Eva
   │
   ▼
Preprocesar (contrato compartido Python/Kotlin)
   │
   ▼
Encoder LiteRT  →  embeddings de 256 por cuadro
   │
   ▼
Decoder LiteRT (loop autoregresivo, token a token)
   │
   ▼
Vocabulario (vocab.json)  →  texto en español
```

### 3.1 Captura y buffer de keypoints

- Reutilizar `HolisticLandmarker` de MediaPipe Tasks en modo `LIVE_STREAM`,
  igual que Eva.
- Por cada frame, extraer los 21 landmarks de mano izquierda y 21 de mano
  derecha (si una mano no se detecta, rellenar con ceros para esa mano).
- Concatenar en un array `[42, 3]` por frame (`x, y, z`), igual que el
  formato de `SequenceKeypointContract`.
- La captura comienza y termina con acciones explícitas; no se reutiliza el
  segmentador de señas aisladas.

### 3.2 Preprocesamiento (debe reproducir `sequence_contract.py` exactamente)

Antes de pasarle la ventana al encoder, replicar en Kotlin/Java la misma
normalización del contrato `75×126`:

1. Por cada frame, centrar `x,y` restando el centro de los 42 puntos,
   incluidos los ceros de una mano ausente.
2. Calcular la escala global = máximo valor absoluto de `x,y` en toda
   la ventana, y dividir todos los `x,y` por ese valor.
3. Mantener `z` sin centrar ni escalar.
4. Si hay menos de 75 frames, rellenar con cuadros cero al final.
5. Si hay más, elegir `idx[i] = (i * (T - 1)) // (75 - 1)` con división
   entera. No usar `linspace` de punto flotante.

El tensor final de entrada al encoder es `[1, 75, 126]` float32
(`126 = 42 landmarks * 3 coords`).

### 3.3 Encoder

```kotlin
// El contrato produce B,T,126. El manifiesto puede declarar para LiteRT
// B,126,T; SequenceTranslator realiza la transposición explícita.
// El encoder devuelve embeddings de 256 por cuadro; la cabeza CTC no se usa.
```

### 3.4 Decoder (loop autoregresivo)

El decoder se ejecuta token por token (no hay beam search embebido en
el modelo exportado). Reproducir en Android el mismo `greedy_decode`
que usa `model.py` en Python:

```kotlin
val BOS_ID = 1   // config.py
val EOS_ID = 2
val MAX_DEC_LEN = 64

val generated = mutableListOf(BOS_ID)
val decoderInterpreter = Interpreter(loadModelFile("decoder_int8.tflite"))

for (step in 0 until MAX_DEC_LEN) {
    // tgt_tokens: [1, generated.size] int32 o int64
    // memory: [1, 256, 75] float32 según el manifiesto
    // logits:     [1, generated.size, vocab_size]
    // SequenceTranslator ejecuta esta llamada en un executor serial y obtiene
    // los logits del último timestep.
    val nextId = argmax(outputLogits[0].last())  // ultimo timestep
    generated.add(nextId)
    if (nextId == EOS_ID) break
}
```

> Nota de rendimiento: reejecutar el decoder completo en cada paso
> (sin cache de atención) es más simple de portar pero más lento.
> Para 64 pasos máximo con `d_model=256` y 3 capas, es viable en CPU
> mobile; si hace falta más velocidad, se puede exportar el decoder
> con KV-cache más adelante.

### 3.5 Vocabulario y post-proceso

`vocab.json` es una lista de tokens donde el índice = el ID usado por
el modelo (mismo formato que `Vocabulary` en [dataset.py](dataset.py)):

```json
["<PAD>", "<BOS>", "<EOS>", "<UNK>", "hola", "como", ...]
```

Para convertir la secuencia de IDs generada a texto:
1. Cargar `vocab.json` como `List<String>`.
2. Recorrer los IDs generados, **omitiendo** `PAD_ID=0`, `BOS_ID=1`,
   `EOS_ID=2`, `UNK_ID=3` (tokens especiales, igual que `Vocabulary.decode`).
3. Unir las palabras restantes con espacios.

La salida no se publica automáticamente: si no hay texto, si el decoder no
termina con EOS o si ocurre un error, se rechaza. Un candidato válido aparece
en pantalla para confirmación explícita antes de crear un turno o enviarlo a
voz.

---

## 4. Estado de la integración y checklist

- [x] Mantener Eva y sus assets sin cambios.
- [x] Implementar el contrato Kotlin, el buffer y el selector de modo.
- [x] Mantener puntos de MediaPipe visibles en la preview.
- [ ] Copiar el paquete aprobado a `assets/lsa_t/`.
- [ ] Ejecutar la prueba instrumentada con `fixture_android.json`.
- [ ] Verificar encoder y decoder reales en un dispositivo sin red.
- [ ] Comparar la salida del fixture con Python antes de habilitar el modo.

---

## 5. Verificación de consistencia (recomendado)

Antes de integrar en la app, es útil correr `03_evaluate.py --examples N`
y guardar unos pares (keypoints de entrada -> texto esperado). Reproducir
esos mismos ejemplos en Android permite confirmar que la normalización y
el loop de decodificación están bien portados antes de probar con la
cámara en vivo.
