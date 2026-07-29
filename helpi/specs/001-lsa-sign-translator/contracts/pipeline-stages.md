# Contratos entre etapas del pipeline de datos

**Versión**: `1.0.0` | **Estado**: Normativo

El pipeline de preparación de datos se descompone en **cuatro etapas independientes**. Cada una
tiene entrada y salida explícitas, es ejecutable por separado, es testeable de forma aislada y traza
a un requisito.

> La POC concentró el preprocesamiento en un script monolítico. Eso hizo imposible testear la
> normalización temporal —el punto de desalineación conocido entre entrenamiento e inferencia— sin
> ejecutar todo el pipeline sobre video real. Aquí cada etapa se verifica con datos sintéticos, sin
> video y sin modelo.

```text
S0 videos LSA64
   │
   ├─ S1  extracción de keypoints      →  secuencia cruda de largo variable
   ├─ S2  normalización temporal       →  secuencia de largo fijo
   ├─ S3  normalización espacial       →  secuencia normalizada
   └─ S4  ensamblado del dataset       →  X, y, sujeto, repetición, procedencia
```

Formatos de archivo en [data-model.md §3–§4](../data-model.md). Este documento define los
**contratos de interfaz**: precondiciones, postcondiciones e invariantes.

---

## Correspondencia con el pipeline en vivo

La misma cadena, con la misma semántica, corre en el cliente. Es la exigencia del Principio IV
("el pipeline de keypoints de entrenamiento y el de inferencia deben ser el mismo código o
probadamente equivalentes, etapa por etapa"):

| Etapa | Entrenamiento (`ml/`) | Cliente (`frontend/`) | Verificación |
|---|---|---|---|
| S1 | `stages/s1_extract` sobre archivo de video | `keypoints/` sobre stream de cámara | Nivel 2 (informativo) |
| — | — | `segment/` recorta el evento (FR-008) | NFR-022 |
| S2 | `stages/s2_temporal` | `contract/` (mismo criterio) | **Nivel 1, bloqueante** |
| S3 | `stages/s3_spatial` | `contract/` (mismo criterio) | **Nivel 1, bloqueante** |
| S4 | `stages/s4_assemble` | No aplica (no hay dataset en vivo) | — |

S1 es la única etapa que **no** puede verificarse con igualdad numérica: dos implementaciones
distintas de MediaPipe no producen los mismos landmarks. S2 y S3 son deterministas y por eso son
las que la puerta G1 exige idénticas.

En el cliente aparece una etapa que no existe en entrenamiento —la segmentación— porque en
entrenamiento el recorte ya viene hecho: LSA64 versión cut. **Esa asimetría es exactamente la
desalineación de distribución de DD-002**, y es lo que NFR-022 obliga a medir.

---

## S1 — Extracción de keypoints

**Traza a**: FR-002, FR-003, Principio III (la imagen termina aquí)

```text
Entrada
  video_path : ruta a archivo de video
  config     : { hand_model, pose_model, min_detection_confidence, min_tracking_confidence }

Salida
  keypoints  : float32[T, 201]     # sin normalizar espacialmente, T variable
  presencia  : uint8[T, 3]         # [mano_izq, mano_der, pose] detectada por frame
  fps_efectivo : float32
  T          : int
```

**Precondiciones**

- El video existe y tiene al menos 1 frame decodificable.
- La imagen entregada al detector **no está espejada**.

**Postcondiciones**

- `T` es igual a la cantidad de frames procesados. Ningún frame se descarta en esta etapa: un frame
  sin detección produce ceros en las ranuras correspondientes y un `0` en `presencia`, no una fila
  faltante. Descartar frames aquí alteraría la base temporal de S2 de forma invisible.
- El orden de landmarks y la asignación de handedness cumplen
  [keypoints.md §2](./keypoints.md).
- Los timestamps que se pasan al detector son **estrictamente crecientes**. `detectForVideo` de
  MediaPipe falla con timestamps repetidos; es un error ya sufrido en la POC (`Claude.md`).

**Invariantes**

- Esta es la **única** etapa que ve píxeles. Aguas abajo no hay imagen (Principio III).
- No aplica ningún filtro de imagen, corrección de color, recorte ni aumento de contraste.

**Tests aislados**

Video corto conocido → `T` esperado, forma `(T, 201)`, política de relleno cuando falta una mano,
asignación correcta izquierda/derecha, y colisión de handedness resuelta por score.

---

## S2 — Normalización temporal

**Traza a**: NFR-014, edge case "seña mucho más rápida o más lenta que en el dataset"

```text
Entrada
  keypoints : float32[T, 201],  T >= 1
  seq_len   : int               # leído de kp-contract.json, NUNCA escrito en el código

Salida
  keypoints : float32[seq_len, 201]
  indices   : int32[seq_len]    # los índices efectivamente muestreados
  T_original: int
```

**Criterio**: aritmética entera exacta, `idx[i] = (i * (T-1)) DIV (seq_len-1)`. Especificación
completa y casos borde en [keypoints.md §3](./keypoints.md).

**Precondiciones**: `T >= 1`. `T == 0` se **rechaza** con error explícito.

**Postcondiciones**

- `indices[0] == 0` e `indices[seq_len-1] == T-1`.
- `indices` es no decreciente.
- Toda fila de salida es una **copia exacta** de una fila de entrada. S2 no interpola, no promedia y
  no altera ningún valor.

**Invariantes**

- Etapa pura: misma entrada, misma salida, siempre, en cualquier lenguaje.
- `indices` se persiste deliberadamente: hace auditable la normalización sin volver a ejecutarla y
  es lo que se compara contra la tabla congelada.

**Por qué es una etapa propia**

Es la fuente conocida de desalineación entre entrenamiento e inferencia: la POC usaba una ventana
deslizante de 40 frames crudos en vivo contra secuencias muestreadas con `linspace` en
entrenamiento, y eso produjo confianza inestable. Aislarla permite testear los tres casos
—más larga, más corta, igual— sin montar video ni modelo, y garantiza que el cliente invoque
exactamente el mismo criterio.

**Tests aislados (obligatorios)**

`T > seq_len`, `T < seq_len`, `T == seq_len`, `T == 1`, `T == 0` (rechazo), y comparación de
`indices` contra la tabla congelada de fixtures.

---

## S3 — Normalización espacial

**Traza a**: NFR-014, Principio IV

```text
Entrada
  keypoints : float32[seq_len, 201]
Salida
  keypoints : float32[seq_len, 201]
```

**Criterio**: centrado en el punto medio de los hombros (pose landmarks 11 y 12) aplicado a `x` e
`y`; `z` sin centrar. Especificación completa en [keypoints.md §4](./keypoints.md).

**Precondiciones**: los landmarks 11 y 12 están presentes en el frame. Si no lo están, el frame no
puede normalizarse y el evento se descarta con `TORSO_NO_VISIBLE` (FR-006); no se sustituye por un
centro por defecto ni por el del frame anterior.

**Postcondiciones**

- La componente `z` de las 201 coordenadas es **idéntica** a la de entrada. Test de contrato propio,
  independiente del test de equivalencia.
- Las ranuras de manos ausentes quedan en cero: S3 no las toca.
- El punto medio de los hombros de salida es `(0, 0)` en `x, y`.

**Invariantes**: etapa pura. No hay escalado de ningún tipo.

---

## S4 — Ensamblado del dataset

**Traza a**: NFR-001a, NFR-002, Principios V y VI

```text
Entrada
  directorio de secuencias S3 + convención de nombres de LSA64
Salida
  X: float32[M, seq_len, 201] · y: int64[M] · subject: int64[M] · repetition: int64[M]
  source.json · MANIFEST.json
```

**Precondiciones**

- Todas las secuencias de entrada tienen forma `(seq_len, 201)` y provienen de la misma versión de
  contrato.
- Etiqueta, sujeto y repetición se derivan **exclusivamente** de la convención de nombres de LSA64
  (`<clase>_<sujeto>_<repeticion>.mp4`). No se infieren de ningún otro lado.

**Postcondiciones**

- `M == len(y) == len(subject) == len(repetition)`.
- `y ∈ [0, 63]`, `subject ∈ [1, 10]`, `repetition ∈ [1, 5]`.
- `MANIFEST.json` completo: versión de dataset, versión de contrato, `seq_len`, versiones exactas de
  dependencias, seed, hash de contenido y **splits por sujeto**.
- `source.json` conserva, por muestra, el archivo de origen, `T_original` e `indices`. Sin eso no se
  puede volver de un error de clasificación al video que lo produjo, que es la operación básica del
  análisis de pares confundidos.

**Invariantes (con test)**

- **Ningún sujeto aparece en más de un split.** El split es por sujeto y es una propiedad del
  dataset declarada en el manifiesto, no una opción de la corrida de entrenamiento. El split
  aleatorio está prohibido para todo número reportable (NFR-002, Principio V).
- Re-ejecutar S1..S4 con la misma seed y las mismas versiones produce el mismo `content_hash`.

---

## Reglas comunes a las cuatro etapas

1. **Ejecutables por separado.** Cada etapa lee de disco y escribe a disco. Ninguna invoca a otra.
2. **Idempotentes.** Re-ejecutar con la misma entrada produce la misma salida.
3. **Sin parámetros escondidos.** Todo valor que gobierna el resultado (`seq_len`, índices de
   hombros, dimensiones de bloques, umbrales del detector) vive en `kp-contract.json` o en la
   configuración de la etapa, y queda registrado en el manifiesto.
4. **Fallan ruidosamente.** Ninguna etapa produce un resultado degradado en silencio: entrada
   inválida → error explícito. Un vector de ceros es una secuencia perfectamente válida en forma y
   sin significado, y el clasificador le asignará alguna clase.
5. **Trazabilidad de procedencia.** La salida de cada etapa permite reconstruir de qué entrada vino.
