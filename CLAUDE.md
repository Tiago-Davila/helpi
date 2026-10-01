# CLAUDE.md — helpi-android · Traducción de LSA

Contexto estable para agentes de código (Claude Code / Codex) en **este
repositorio**. Es la aplicación Android. No contiene entrenamiento ni datos.

> Alcance de este archivo: la cadena de traducción de Lengua de Señas
> Argentina. Rutinas, pictogramas, notificaciones y emergencias tienen su
> propia documentación y no se tratan acá.

---

## 1. Qué construye este repositorio

La app Android que reconoce señas de LSA con la cámara y las comunica por
texto y voz. **Todo el reconocimiento ocurre en el dispositivo, sin red.**

Cadena de inferencia:

```
CameraX → MediaPipe Tasks HolisticLandmarker → vector de 168 coords
        → ventana → LiteRT (.tflite) → índice de clase + confianza
        → catálogo local → glosa → texto y voz
```

Eva es el nombre del clasificador. **Eva reconoce, no interpreta.** Convertir
la glosa en frase y sintetizarla son componentes determinísticos separados.

Herramienta de **asistencia**, no reemplazo de intérpretes humanos. Esto debe
reflejarse en la interfaz, no solo en la documentación.

---

## 2. CONTRATO DE KEYPOINTS (crítico — replicado en los tres repositorios)

Es la única definición que este repo comparte con `helpi-ml`. Una divergencia
**no produce ninguna excepción**: produce traducciones incorrectas.

### Vector de 168 coordenadas por cuadro

| Rango | Contenido | Landmarks |
|---|---|---|
| `[0, 63)` | mano izquierda | 21 × (x, y, z) |
| `[63, 126)` | mano derecha | 21 × (x, y, z) |
| `[126, 168)` | pose 11..24 | 14 × (x, y, z) |

- **Orden de aplanado**: agrupado por landmark, ejes intercalados.
  `[x₀, y₀, z₀, x₁, y₁, z₁, …]` — NO todos los x, después todos los y.
- Se descartan pose 0..10 (cara) y pose 25..32 (piernas).
- **No detectado → ceros.** Cualquier otro relleno rompe el contrato.
- `HolisticLandmarker` entrega `leftHandLandmarks` / `rightHandLandmarks`
  explícitos: no hay que resolver la mano por handedness.

### Normalización espacial — contrato v3

Sobre los 40 cuadros ya muestreados, en este orden y en float32:

1. Landmarks presentes a píxeles: `X = x·W`, `Y = y·H`, `Z = z·W`, con W×H el
   tamaño del cuadro que analizó MediaPipe (ya rotado).
2. Se resta el punto medio de los hombros (pose 11 y 12) a **X e Y**.
   **Z NO se centra.** Un cuadro sin ambos hombros usa el centro del cuadro
   válido más cercano (el anterior si hay empate).
3. Todo landmark presente se divide por la **mediana del ancho de hombros**
   (distancia X/Y) de los cuadros válidos; con cantidad par, promedio de los
   dos centrales.
4. Sin hombros en ningún cuadro: secuencia inválida, no se clasifica.

La entrada queda en "anchos de hombro": no depende de la cámara, la
orientación ni la distancia. El contrato v2 (solo centrado en coordenadas
normalizadas) hacía que el modelo, entrenado con video 1920×1080, recibiera
en el teléfono valores ~3 veces más grandes y deformados.

`lsa-manifest.json` declara `contractVersion`; la app rechaza un modelo cuya
versión no coincide con `KeypointContract.CONTRACT_VERSION`.

```
offset hombro izquierdo = 126
offset hombro derecho   = 129
```

### Muestreo temporal — aritmética entera EXACTA

```kotlin
// T = frames disponibles, N = 40, división entera
idx[i] = (i * (T - 1)) / (N - 1)   // T >= N
idx[i] = (i * T) / N               // T <  N: estira repitiendo cuadros parejo
```

**Nunca usar punto flotante acá.** `linspace` en float difiere del entero en
~3% de los largos de video (ej. T=46, i=13: float da 14, entero da 15, porque
13*45/39 = 14.999999999999998). El entero es reproducible bit a bit entre
Python y Kotlin, que es lo que hace verificable el contrato.

Si `T < N` (lo habitual en el teléfono a 12–24 fps), cada cuadro se repite
N/T o N/T+1 veces en orden. Repetir solo el último (v2) dejaba la seña
comprimida al principio, algo que el modelo nunca vio en LSA64.

### Ventana

40 cuadros. `FRAMES = 40`, `COORDS = 168`.

### Regla de implementación

Todo esto vive en **un solo archivo** (`keypoints/KeypointContract.kt`), no
disperso entre la clase de cámara y la de inferencia. Ese archivo espeja al
productor de Python y es el que verifican los tests.

---

## 3. Decisiones de Android (no negociables)

- **LiteRT** (`com.google.ai.edge.litert:litert`) para inferencia.
  `org.tensorflow:tensorflow-lite` está deprecado, no usarlo.
- **MediaPipe Tasks HolisticLandmarker** para keypoints. Es la misma task que
  produce el dataset de entrenamiento: mismo extractor en ambos extremos.
- **CameraX**, no la API de cámara de bajo nivel.
- El modelo `.tflite` se convierte con `unroll=True` y dropout como capas
  separadas, de modo que use solo ops elementales. **No debe requerir el
  delegado Flex** (`tensorflow-lite-select-tf-ops`). Si un modelo nuevo lo
  exige, es un defecto del modelo, no algo a resolver agregando la librería.
- **El video crudo nunca sale del dispositivo.** No hay servidor de
  inferencia en producción.

### Configuración obligatoria de Gradle

```kotlin
android {
    androidResources {
        noCompress += listOf("tflite", "task")
    }
}
```

Sin esto, Gradle comprime los modelos en el APK y LiteRT no puede mapearlos a
memoria. Falla de forma poco descriptiva.

### Modelo y catálogo son una unidad

`modelo_lsa.tflite` y `catalogo_senas.json` se versionan y actualizan
**juntos, siempre**. El clasificador devuelve un índice, no una glosa: si el
modelo se actualiza y el catálogo no, el índice pasa a significar otra cosa y
la app traduce mal **sin arrojar ningún error**.

---

## 4. Estado del modelo

- Vocabulario: **64 señas de LSA64**. Vocabulario cerrado.
- Línea base: **≈0.85 de exactitud con partición por sujeto** (se entrena con
  nueve señantes y se evalúa con el décimo, nunca visto).
- Tamaño del artefacto: ~1,4 MB.

### Limitaciones conocidas que la interfaz debe respetar

- Los sujetos de LSA64 eran **oyentes, diestros**, y aprendieron las señas en
  la propia sesión de grabación. El modelo reconoce la imitación de una seña,
  no la seña de un señante nativo.
- **Un usuario zurdo falla de forma sistemática.** Es un problema de
  accesibilidad dentro de una aplicación de accesibilidad.
- Grabado en laboratorio: pared blanca, guantes fluorescentes, trípode a dos
  metros. En uso real el extractor puede comportarse distinto.
- El vocabulario de LSA64 no fue diseñado para comunicación asistida: incluye
  "Opaco", "Fideos", "Aspirina", y **ninguna seña de emergencia**.

Estas limitaciones se comunican al usuario; no se disimulan.

---

## 5. Confianza explícita

El sistema **nunca presenta una traducción como certeza cuando no la tiene**.

- Umbral de confianza configurable.
- Por debajo del umbral: comunicar que no se entendió. Nunca mostrar la
  adivinanza como resultado.
- La interfaz puede mostrar las tres probabilidades más altas únicamente como
  predicciones no confirmadas. Solo la primera, si supera el umbral, se publica
  en la conversación y se ofrece a voz.
- Preferimos silencio a traducción incorrecta: una traducción errada puede
  dañar la comunicación de una persona sorda más que la ausencia de traducción.
- Vocabulario cerrado: una seña fuera de las 64 se resuelve como
  no-reconocida, nunca se fuerza a la clase más cercana.

La guía de distancia usa el ancho de hombros en píxeles como escala, no como
medición en metros. Con el contrato v3 el modelo no depende de la distancia:
la guía solo cuida que el espacio de señado entre en el cuadro. Ese espacio
(3,1 × 2,31 anchos de hombro) sale de los percentiles 1 y 99 de las manos en
LSA64; en 16:9 horizontal el rango queda en ~0,13..0,24 del ancho de la
imagen. No bloquea la captura.

La app está fija en horizontal (`sensorLandscape`) y la cámara analiza en
16:9 (960×540), el mismo formato que LSA64.

---

## 6. Estrategia de pruebas

**Orden obligatorio. No saltear el primer paso.**

### Etapa 1 — Modelo sin cámara (test instrumentado)

`fixture_android.json` (en `androidTest/assets/`) contiene secuencias reales
con los logits que produjo el mismo `.tflite` en la máquina de entrenamiento.
El test carga el modelo, le pasa esas secuencias y compara con tolerancia
`1e-3`.

Aísla el modelo del pipeline de captura. **Si este test pasa, el despliegue
del modelo está resuelto y toda falla posterior es de captura.**

Antecedente que justifica esta secuencia: en la prueba web se cableó cámara,
extractor y modelo de una sola vez, y el buffer llegaba en ceros mientras
todo *parecía* funcionar —forma correcta, predicciones con confianza, interfaz
normal—. Ningún test tradicional lo habría detectado.

### Etapa 2 — Keypoints sin modelo

Construir el vector de 168 desde la cámara y verificar su distribución contra
la del dataset de entrenamiento: proporción de ceros por bloque, rangos y
movimiento entre cuadros. **Comparar distribuciones, no solo revisar que no
haya excepciones.**

### Etapa 3 — Cadena completa

Recién cuando 1 y 2 pasan.

### Test de contrato (bloqueante de CI)

Verifica el muestreo entero y la normalización v3 contra
`app/src/test/resources/contract_v3_fixture.json`, generado por
`write_contract_fixture.py` en `helpi-ml` con el mismo `eva_contract.py` que
produce el dataset. Es la prueba más barata del proyecto y cubre un
error que ya se manifestó una vez.

---

## 7. Límites de este repositorio

**No pertenece acá** (vive en `helpi-ml`):

- entrenamiento, dataset, augmentation, experimentos
- preprocesamiento de videos, generación de `.npy`
- métricas de modelo y matrices de confusión

Este repo **consume** un `.tflite` y su catálogo. No los produce.

Tampoco pertenecen acá las rutinas, pictogramas, notificaciones ni
emergencias: son otras funcionalidades de Helpi, con su propia documentación.

---

## 8. Convenciones

- Kotlin para interfaz y ciclo de vida. Java puro para módulos de dominio sin
  dependencias de Android (se prueban sin emulador).
- **ktlint** y **detekt**: las discusiones de estilo las resuelve la
  herramienta, no la revisión de código.
- Conventional commits, con el número de funcionalidad en el cuerpo.
- Ramas: `NNN-nombre-funcionalidad`, con **el mismo número en los tres
  repositorios** cuando la funcionalidad los atraviesa.
- Una tarea, un diff, un commit.
- La especificación manda sobre la implementación: no se programa nada que no
  trace a un requisito.
- No implementar código durante las fases de especificación, planificación y
  generación de tareas.

### Legibilidad de `domain`

- Escribir un comentario de una sola línea sobre cada método para explicar su
  propósito. Preferir una frase concreta y evitar repetir literalmente el
  nombre o el cuerpo del método.
- Mantener juntas las reglas que cambian por el mismo motivo. Extraer los tipos
  públicos con identidad propia a archivos dedicados; dejar los auxiliares
  privados junto a la clase que los usa.
- Al reorganizar clases, preservar el comportamiento y actualizar todas las
  referencias afectadas. No dividir una clase solo para reducir su cantidad de
  líneas.

---

## 9. Licencia

El modelo se entrenó sobre **LSA64** (LIDI, UNLP), bajo licencia
**CC BY-NC-SA 4.0**: uso no comercial, atribución obligatoria, obras derivadas
bajo la misma licencia. La atribución debe figurar en la interfaz.

Se adopta la lectura conservadora de que el `.tflite` es obra derivada.s
