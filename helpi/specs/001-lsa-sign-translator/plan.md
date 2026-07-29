# Implementation Plan: Traductor LSA de señas aisladas (LSA64) con confianza explícita

**Branch**: `001-lsa-sign-translator` | **Date**: 2026-07-29 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/001-lsa-sign-translator/spec.md`

**Constitution**: [.specify/memory/constitution.md](../../.specify/memory/constitution.md) v1.0.1

**Artefactos de esta fase**: [research.md](./research.md) · [data-model.md](./data-model.md) ·
[contracts/](./contracts/) · [quickstart.md](./quickstart.md)

> **Principio XIV**: este documento y sus artefactos son documentación. No se implementa código de
> producción en esta fase. Todo bloque de código que aparece aquí o en `contracts/` es
> **pseudocódigo normativo**: define un comportamiento a implementar, no es el código a copiar.

---

## Discrepancia declarada con la entrada de planificación

La entrada del comando `/speckit-plan` describió `backend/` como **servicio de inferencia** con
WebSocket de keypoints hacia PyTorch. Eso contradice la spec vigente:

| Entrada del comando | Spec vigente | Resolución |
|---|---|---|
| Backend recibe keypoints por WS y clasifica | **FR-002**: extracción y clasificación íntegramente en el dispositivo | El clasificador corre en el navegador |
| Keypoints viajan al servidor | **NFR-006** / **DD-005**: los keypoints MUST NOT abandonar el dispositivo | Solo salen glosas, solo con pulido activado |
| — | *Out of Scope*: "Inferencia en servidor y cualquier servicio remoto de reconocimiento" | Confirmado fuera de alcance |
| La entrada no menciona el LLM de pulido | **DD-003/DD-004/DD-005**, FR-038–FR-041, NFR-023–NFR-027 | `backend/` es el **servicio de pulido**, no de inferencia |
| La entrada describe captura por seña ("hasta 3 intentos por captura") | **DD-002**: grabación continua, el sistema segmenta | El plan asume segmentación continua |

Por el **Principio I** (la especificación manda) el plan sigue la spec. El WebSocket de keypoints
solicitado se conserva, pero acotado a **arnés de desarrollo y evaluación** (`backend/devinfer`),
prohibido en el build de producción por SC-004 y SC-013. Su contrato está en
[contracts/inference-ws.md](./contracts/inference-ws.md).

Si la intención era mover el reconocimiento al servidor, eso requiere modificar la spec (sección
*Out of Scope* y NFR-006) antes de planificar, no resolverse en `plan.md`.

---

## Summary

Sistema web que reconoce las 64 señas de LSA64 sobre stream continuo de cámara, íntegramente en el
dispositivo, y las comunica por texto y voz con confianza explícita.

El trabajo técnico se ordena en tres frentes acoplados por un único contrato de datos:

1. **Pipeline de datos y modelo (`ml/`)** — migración de MediaPipe Holistic a Tasks API,
   descomposición del preprocesamiento en cuatro etapas independientes y testeables, regeneración
   del dataset, re-entrenamiento y re-validación contra el baseline 0.85 (NFR-001a), con módulo de
   evaluación por clase que alimenta la política de confianza.
2. **Cliente en dispositivo (`frontend/`)** — captura, extracción de keypoints con Tasks API for
   Web, segmentación de seña (DD-002), clasificación con el modelo exportado, política de confianza
   como módulo propio, y presentación por texto y voz.
3. **Servicio de pulido (`backend/polish`)** — único componente remoto; convierte una secuencia de
   glosas en frase en español bajo la restricción de clase cerrada de DD-004, y solo recibe glosas.

El eje que sostiene todo es el **contrato de 201 coordenadas** (Principio IV): un módulo de
referencia normativo, dos implementaciones (Python y TypeScript), fixtures versionados y un test de
equivalencia numérica bloqueante de CI.

Los dos riesgos que ordenan el calendario son la **segmentación temporal** (DD-002: si no funciona,
no funciona nada; puerta de decisión en NFR-022) y la **migración a Tasks API** (obliga a regenerar
dataset y re-entrenar antes de poder afirmar cualquier métrica).

---

## Technical Context

**Language/Version**:
Python 3.12 (`ml/`, `backend/`) · TypeScript 5.x + React 18 + Vite (`frontend/`)

**Primary Dependencies**:

- `ml/`: MediaPipe Tasks (Python) — `HandLandmarker` + `PoseLandmarker`; PyTorch (LSTM 2 capas,
  hidden 128, dropout 0.3); NumPy; scikit-learn (métricas y matriz de confusión); `onnx` +
  `onnxruntime` (export y verificación de paridad).
- `backend/polish`: FastAPI + `uvicorn[standard]`; runtime del LLM **NEEDS DECISION** (R-011).
- `backend/devinfer` (solo dev/eval): FastAPI + WebSocket + PyTorch en modo eval.
- `frontend/`: `@mediapipe/tasks-vision` (HandLandmarker + PoseLandmarker); `onnxruntime-web`
  (WASM + SIMD); Web Speech API (`SpeechSynthesis`); CSS propio, sin librería de componentes.

**Storage**:
Sin base de datos. `ml/` usa `.npy` para datos intermedios y JSON para metadatos y métricas.
`frontend/` usa memoria para el historial de sesión (efímero, FR-014/NFR-008) y `localStorage`
únicamente para preferencias (FR-027) y registro de descartes (FR-020). `backend/polish` es
**sin estado**: no persiste nada (NFR-024).

**Testing**:
`pytest` (`ml/`, `backend/`) · `vitest` (`frontend/`) · Playwright para los escenarios de UI y para
la verificación de tráfico saliente (SC-004, SC-023) · runner de equivalencia Python↔TypeScript
sobre fixtures compartidos (NFR-014 Nivel 1, bloqueante de CI).

**Target Platform**:
Navegador (Chromium y Firefox recientes) en teléfono de gama media y en notebook. El servicio de
pulido corre en Linux. El entrenamiento corre en Linux con GPU NVIDIA (Blackwell sm_120 → build de
PyTorch cu128+, ver `Claude.md`).

**Project Type**:
Aplicación web con inferencia en el cliente, más un servicio remoto acotado de post-procesamiento
lingüístico, más un proyecto de ML fuera de línea.

**Performance Goals**:

- NFR-001a: accuracy >= 0.85, LSA64 cut, sujeto 10 held-out, **medida sobre el modelo exportado que
  realmente corre en el navegador**, no solo sobre el checkpoint de PyTorch.
- NFR-001b: accuracy >= 0.70 extremo a extremo del sistema desplegado.
- NFR-003 L1 < 1 s, L2 < 2 s (p95, >= 50 capturas) en el dispositivo de referencia. Desglose del
  presupuesto en [research.md R-008](./research.md).
- NFR-023: < 3 s entre pausa detectada e inicio de la frase hablada, sobre 4G urbano.
- Captura y extracción de keypoints >= 15 fps efectivos (NFR-004).

**Constraints**:

- Vector de entrada de exactamente 201 coordenadas por frame, secuencia de largo fijo
  (Principio IV, NO NEGOCIABLE).
- Ni video ni keypoints cruzan la frontera del dispositivo (NFR-006, más estricto que el
  Principio VII).
- Vocabulario cerrado de 64 señas; nada fuera de él se fuerza a la clase más cercana (FR-003,
  FR-017).
- Métricas reportables solo con split por sujeto (Principio V, NFR-002).
- Licencia no comercial heredada de LSA64 (Principio X).

**Scale/Scope**:
64 clases · 10 sujetos · 5 repeticiones por seña y sujeto (3200 videos) · modelo LSTM de ~0,5 M
parámetros · secuencias de 40 × 201 floats · 8 historias de usuario, 41 FR, 27 NFR, 26 SC.

**Dispositivo de referencia (NFR-003) — a declarar antes de la primera medición de L2**:
sigue siendo **NEEDS DECISION**. El plan fija el procedimiento y el momento en
[research.md R-008](./research.md), no el modelo concreto: elegirlo requiere saber qué hardware
tiene disponible el equipo, dato que no está en la spec ni en el repo.

---

## Constitution Check

*GATE: evaluado antes de Phase 0 y re-evaluado tras Phase 1. Resultado de ambas pasadas abajo.*

| # | Gate | Pre-research | Post-design | Notas |
|---|------|--------------|-------------|-------|
| I | Toda capacidad planificada traza a una historia o `FR-###` | PASS | PASS | Matriz de trazabilidad en este documento, sección *Trazabilidad*. Ningún módulo del árbol de fuentes queda sin requisito asociado. |
| II | Orden del pipeline respetado; LLM solo glosa→frase | PASS | PASS | El servicio de pulido recibe **solo** la secuencia de glosas ya clasificadas (FR-039). Contrato en [contracts/polish-service.md](./contracts/polish-service.md): el schema de petición no admite keypoints ni imágenes, y el servidor rechaza todo lo que no sea una secuencia de glosas del vocabulario (NFR-026). |
| III | El reconocimiento consume keypoints, nunca píxeles | PASS | PASS | Ninguna etapa entre captura y clasificador manipula la imagen. El modo espejo (FR-026) es una transformación **de presentación**: se aplica al `<video>` por CSS y el detector recibe el frame sin espejar (R-001). |
| IV | Contrato de keypoints intacto (63+63+75=201, centrado en hombros, z sin centrar, largo fijo por linspace); tests para cada productor | PASS | PASS | La migración a Tasks API cambia el **productor**, no el vector. El contrato se versiona a `kp-contract 2.0.0` por cambio de productor y de criterio de muestreo (entero exacto, R-009), con dataset regenerado. Ver [contracts/keypoints.md](./contracts/keypoints.md). |
| V | Métricas reportables con split por sujeto; sin regresión bajo 0.85 | PASS | **RIESGO ACEPTADO** | La migración a Tasks API puede bajar de 0.85. El plan define el procedimiento de justificación escrita y recuperación en *Fase B* y en [research.md R-001](./research.md), y cierra además la deuda de CV por sujeto (Principio XII.3) con leave-one-subject-out sobre los 10 sujetos. |
| VI | Cada experimento registra config, seed, dataset y dependencias; artefactos separados | PASS | PASS | Formato del artefacto de modelo y de la corrida de experimento en [data-model.md](./data-model.md) §7–§8. Directorio por corrida, nunca sobrescrito. |
| VII | El video crudo nunca sale del dispositivo; solo keypoints cruzan la red | PASS | PASS | El plan es **más estricto**: tampoco salen keypoints (NFR-006). Lo único que cruza es la secuencia de glosas, y solo con el pulido activado (DD-005). El arnés `backend/devinfer` sí transporta keypoints, y por eso está prohibido en el build de producción (ver *Complexity Tracking*). |
| VIII | Umbral configurable; bajo umbral dice "no entendí"; UI declara asistencia | PASS | PASS | Política de confianza como **módulo propio** (`frontend/src/confidence/`), no lógica dispersa. Cubre umbral, regla de parada de 3 intentos y compensación de optional stopping (NFR-019). |
| IX | Latencia percibida < 2 s; toda concesión justificada en `research.md` | PASS | PASS | Desglose del presupuesto en [research.md R-008](./research.md). Hallazgo: el costo dominante no son los 3 intentos sino la histéresis del detector de fin de seña. |
| X | Licencia no comercial y atribución de LSA64 preservadas | PASS | PASS | FR-030. El `Readme.md` de la raíz del repo sigue **pendiente** del aviso de asistencia y la atribución (marcado en el Sync Impact Report de la constitution); queda como tarea de la fase `tasks`. |
| XI | Captura / keypoints / clasificación / glosa→texto / presentación con contratos separados | PASS | PASS | Árbol de fuentes por etapa; contratos entre etapas en [contracts/pipeline-stages.md](./contracts/pipeline-stages.md). El cliente no conoce arquitectura ni pesos; recibe un artefacto opaco más un `labels.json`. |
| XII | El plan no agrava la deuda heredada de la POC | PASS | PASS | La **cierra**: (1) migración a Tasks API en Fase A; (2) segmentación temporal en Fase D; (3) CV por sujeto en Fase B; (4) augmentation en Fase B. Ninguna queda diferida. |
| XIII | Reglas de negocio y de pipeline con tests; contrato de datos en CI bloqueante | PASS | PASS | Tres puertas bloqueantes definidas en *Estrategia de pruebas*. |
| XIV | Sin código de producción en fases documentales | PASS | PASS | Esta fase produce únicamente `plan.md`, `research.md`, `data-model.md`, `contracts/`, `quickstart.md`. |

**Resultado**: sin violaciones que requieran enmienda constitucional. El único gate con riesgo
material es el V, cuyo procedimiento de manejo está definido y no diferido.

---

## Project Structure

### Documentation (this feature)

```text
specs/001-lsa-sign-translator/
├── spec.md                      # Especificación (existente)
├── plan.md                      # Este archivo
├── research.md                  # Phase 0 — decisiones e investigación
├── data-model.md                # Phase 1 — entidades, formatos intermedios, artefactos
├── quickstart.md                # Phase 1 — guía de validación ejecutable
├── contracts/
│   ├── README.md                # Índice y reglas de versionado de contratos
│   ├── keypoints.md             # Contrato de 201 coordenadas (Principio IV)
│   ├── pipeline-stages.md       # Contratos entre etapas S0–S4 del pipeline de datos
│   ├── inference-ws.md          # Protocolo WebSocket (arnés dev/eval) + puerto de clasificación
│   └── polish-service.md        # Contrato HTTP del servicio de pulido glosa→frase
├── checklists/                  # Existentes (requirements, testability)
└── tasks.md                     # Phase 2 — generado por /speckit-tasks, NO por este comando
```

### Source Code (repository root)

```text
contracts/keypoints/                     # NORMATIVO, compartido, versionado
├── kp-contract.json                     # Parámetros del contrato (única fuente de verdad)
├── landmark-map.json                    # Índices y orden de landmarks Tasks API → vector 201
└── fixtures/
    ├── raw/                             # Landmarks crudos de entrada (64 clases × >=1 muestra)
    ├── expected/                        # Vectores 201 esperados, congelados
    └── MANIFEST.json                    # Hashes, versión de contrato, procedencia

ml/                                      # Python 3.12
├── src/lsa_ml/
│   ├── contract/                        # Lectura de kp-contract.json; validadores de forma
│   ├── stages/
│   │   ├── s1_extract.py                # video → keypoints crudos por frame (largo variable)
│   │   ├── s2_temporal.py               # largo variable → largo fijo (linspace entero)
│   │   ├── s3_spatial.py                # centrado en punto medio de hombros; z sin centrar
│   │   └── s4_assemble.py               # secuencias + etiqueta + sujeto + repetición + procedencia
│   ├── segment/                         # Segmentador de seña (offline, para evaluar NFR-022)
│   ├── train/                           # Entrenamiento, seeds, augmentation
│   ├── eval/
│   │   ├── metrics.py                   # accuracy por sujeto, LOSO
│   │   ├── confusion.py                 # matriz completa + pares más confundidos
│   │   ├── per_class.py                 # precisión/recall por clase
│   │   ├── confidence.py                # distribución de confianza aciertos vs errores
│   │   └── stopping.py                  # inflación por optional stopping (NFR-019)
│   ├── export/                          # PyTorch → ONNX + verificación de paridad
│   └── report/                          # Informe de evaluación reproducible
├── tests/
│   ├── contract/                        # Fixtures del contrato (Nivel 1)
│   ├── stages/                          # Cada etapa aislada
│   └── regression/                      # Regresión de métricas contra baseline
└── artifacts/<experiment-id>/           # Nunca se sobrescribe (Principio VI)

frontend/                                # React + TypeScript + Vite
├── src/
│   ├── capture/                         # Cámara, permisos, fps, espejo (presentación)
│   ├── keypoints/                       # Tasks API for Web → landmarks crudos
│   ├── contract/                        # Implementación TS del contrato (S2 + S3)
│   ├── segment/                         # Detección de inicio/fin de seña (DD-002, FR-008)
│   ├── classify/                        # onnxruntime-web; puerto de clasificación
│   ├── confidence/                      # Umbral, 3 intentos, decisión mostrar/callar
│   ├── presentation/
│   │   ├── text/                        # Texto, confianza categórica, historial
│   │   └── voice/                       # Puerto de voz + adaptador Web Speech API
│   ├── polish/                          # Cliente del servicio de pulido + degradación
│   ├── session/                         # Historial, descartes, borrado
│   ├── prefs/                           # Preferencias persistentes
│   └── eval/                            # Instrumentación — EXCLUIDA del build de producción
└── tests/

backend/
├── polish/                              # PRODUCCIÓN — FastAPI, glosa → frase (DD-003)
│   ├── src/
│   │   ├── api/                         # Endpoint, límite de uso, tamaño máximo
│   │   ├── validate/                    # Vocabulario cerrado + trazabilidad por lema (FR-040)
│   │   ├── llm/                         # Adaptador del modelo
│   │   └── metrics/                     # Métricas agregadas sin contenido (NFR-027)
│   └── tests/
└── devinfer/                            # SOLO DEV/EVAL — WS de keypoints → PyTorch
    ├── src/
    └── tests/

tools/
└── contract-equivalence/                # Runner Python↔TypeScript sobre fixtures (CI bloqueante)
```

**Structure Decision**: cuatro raíces de código con fronteras explícitas —`contracts/keypoints/`
(normativo y compartido), `ml/`, `frontend/`, `backend/`— más `tools/`. La separación responde al
Principio XI y a la exigencia de que el contrato de keypoints tenga **una sola fuente de verdad**
consumida por dos implementaciones. `contracts/keypoints/` vive en la raíz y no dentro de `ml/`
porque `frontend/` lo consume con el mismo derecho: si viviera dentro del proyecto de Python, la
implementación de Python sería implícitamente la autoridad y el test de equivalencia degeneraría en
"el cliente se parece bastante al preprocesamiento".

`backend/` contiene **dos** servicios porque cumplen funciones incompatibles: `polish` se
distribuye y es el único componente remoto de producción; `devinfer` transporta keypoints y por eso
no puede existir en producción. Mantenerlos como un solo servicio con banderas haría que la
propiedad verificable de SC-004 ("cero keypoints en el tráfico saliente") dependiera de una
configuración en vez de la ausencia del código.

---

## Decisiones de arquitectura

### AD-01 — El contrato tiene una fuente de verdad declarativa, no un módulo compartido de código

Python y TypeScript no pueden compartir código. Lo que sí pueden compartir es la **especificación
declarativa** del contrato (`contracts/keypoints/kp-contract.json` + `landmark-map.json`) y los
**fixtures**. Cada entorno implementa contra el mismo documento y se verifica contra los mismos
vectores esperados.

Alternativa descartada: compilar el preprocesamiento de Python a WASM y usarlo también en el
cliente. Elimina la divergencia por construcción, pero acopla el cliente al proyecto de ML,
introduce un artefacto binario en la ruta crítica de latencia y hace que el test de equivalencia
—la única defensa contra el fallo silencioso del Principio IV— deje de tener dos implementaciones
que comparar.

Consecuencia: **NFR-014 Nivel 1 es bloqueante de CI** y el fixture congelado es parte del contrato,
no del test.

### AD-02 — El muestreo temporal usa aritmética entera, no `linspace` de punto flotante

La POC usa `np.linspace(0, T-1, 40).astype(int)`. Reproducir eso en JavaScript con la misma
semántica de redondeo y los mismos errores de punto flotante es frágil: una diferencia de un ULP en
un índice frontera cambia un frame entero de la secuencia, y ese error es exactamente el fallo
silencioso contra el que advierte el Principio IV.

El contrato fija `idx[i] = (i * (T-1)) // (N-1)` en enteros. Es exacto, idéntico en ambos lenguajes
y no depende de la representación de punto flotante. Difiere de la versión de la POC en un
subconjunto de valores de `T`, lo que obliga a regenerar el dataset — costo ya asumido por la
migración a Tasks API, que lo obliga de todos modos.

Detalle y casos borde en [research.md R-009](./research.md) y
[contracts/keypoints.md](./contracts/keypoints.md).

### AD-03 — La normalización temporal es una etapa propia, con contrato y tests propios

Es el punto de desalineación conocido entre entrenamiento e inferencia (`Claude.md`, deuda técnica:
"la ventana deslizante de 40 frames crudos no coincide con el entrenamiento"). Aislarla como etapa
con entrada y salida explícitas permite testear los tres casos —secuencia más larga, más corta e
igual al largo objetivo— sin montar un video ni un modelo, y permite que el cliente en vivo invoque
exactamente el mismo criterio.

El largo objetivo (`SEQ_LEN = 40`) es un **parámetro del contrato**, declarado en
`kp-contract.json` y leído por ambas implementaciones. Ningún módulo puede tener el 40 escrito.

### AD-04 — La segmentación produce candidatos; la política de confianza decide

La detección de inicio/fin de seña (FR-008) y la decisión de aceptar o callar (FR-017) son módulos
distintos con una frontera explícita: el segmentador emite hasta 3 **segmentaciones candidatas** del
mismo evento y no sabe nada de umbrales; la política de confianza evalúa las 3, aplica el umbral
compensado (NFR-019) y decide qué se muestra y qué se pronuncia.

Esto es lo que hace verificable SC-012 (nunca más de 3 intentos) y SC-016 (compensación de optional
stopping) sin instrumentar la UI.

### AD-05 — El clasificador se consume por un puerto, no por una implementación

`frontend/src/classify/` expone un puerto `Classifier` cuya entrada es una secuencia conforme al
contrato y cuya salida es un top-k con confianza. Detrás hay hoy `onnxruntime-web`; podría haber
mañana WebGPU, TF.js, o —solo en dev/eval— el WebSocket de `backend/devinfer`. Ningún módulo aguas
arriba conoce la arquitectura, los pesos ni el índice de clases (Principio XI).

Contrato del puerto en [contracts/inference-ws.md](./contracts/inference-ws.md) §1.

### AD-06 — La voz es un puerto reemplazable; Web Speech API es un adaptador

`presentation/voice/` expone `SpeechPort { speak(text, opts), cancel(), listVoices() }`. El
adaptador `WebSpeechAdapter` implementa la degradación es-AR → es-UY → es-* → solo texto (FR-012).
Cambiar de proveedor de TTS no debe tocar la política de confianza ni la presentación de texto.

Texto y voz consumen **el mismo** resultado del clasificador y de la política de confianza: son dos
salidas de un canal, no dos caminos con lógica propia. El texto se emite por seña; la voz se agrupa
por pausa (FR-038).

### AD-07 — El servicio de pulido valida por vocabulario cerrado, no por confianza en el modelo

FR-040 exige que toda palabra de contenido de la frase trace por lema a una glosa de entrada. Como
el vocabulario tiene 64 entradas conocidas, la verificación se hace con una **tabla curada de
flexiones y lemas de las 64 glosas** más una **lista blanca de palabras funcionales**, no con un
lematizador estadístico. Es determinista, testeable con las 50 secuencias de SC-021, y no agrega
una dependencia de NLP al servicio.

La misma validación corre **en el cliente** antes de pronunciar (defensa en profundidad): si el
servicio devolviera una frase con una palabra de contenido no trazable, el cliente la descarta y
pronuncia la glosa cruda (FR-040). Ver [contracts/polish-service.md](./contracts/polish-service.md).

---

## Fases de implementación

Las fases son de **ejecución**, posteriores a este documento. `/speckit-tasks` las convierte en
tareas. El orden está determinado por dependencias reales, no por conveniencia: nada aguas abajo del
contrato puede empezar antes de que el contrato tenga fixtures congelados.

### Fase A — Contrato y pipeline de datos (migración a Tasks API)

**Objetivo**: que exista un dataset regenerado con Tasks API, producido por cuatro etapas
independientes y verificado contra el contrato.

1. Congelar `contracts/keypoints/kp-contract.json` y `landmark-map.json` (versión `2.0.0`).
2. Implementar S1 (extracción con `HandLandmarker` + `PoseLandmarker`), S2 (normalización temporal),
   S3 (normalización espacial), S4 (ensamblado) como módulos con entrada y salida en disco,
   ejecutables por separado.
3. Resolver el mapeo de handedness y la escala de `z` (R-001) **midiendo**, no asumiendo.
4. Regenerar el dataset completo desde los 3200 videos de LSA64.
5. Generar los fixtures del contrato desde el preprocesamiento de referencia y congelarlos.

**Cierra**: deuda XII.1 (Holistic → Tasks API).
**Puerta de salida**: los tests de las cuatro etapas pasan de forma aislada, y `S1..S4` reproducen
byte a byte el mismo dataset al re-ejecutarse con la misma seed y versión.

### Fase B — Entrenamiento, evaluación y re-validación del baseline

**Objetivo**: recuperar (o explicar) el 0.85 con el dataset regenerado, y producir el diagnóstico
por clase que la política de confianza necesita.

1. Re-entrenar con los hiperparámetros vigentes (lr=3e-4, Adam, early stopping paciencia 15,
   `SEQ_LEN=40`, hidden=128, 2 capas, dropout 0.3) y seeds registradas.
2. **Re-validar contra 0.85** con split por sujeto (train 1-8, val 9, test 10). Si cae por debajo:
   justificación escrita según Principio V, con la métrica medida, el motivo atribuido y el plan de
   recuperación — antes de continuar, no al final.
3. Módulo de evaluación completo: accuracy global, matriz de confusión, precisión/recall por clase,
   pares más confundidos, distribución de confianza de aciertos vs errores. Se regenera en **cada**
   evaluación, no es un análisis puntual.
4. Cross-validation leave-one-subject-out sobre los 10 sujetos (cierra deuda XII.3).
5. Data augmentation contra el overfitting medido (train 0.98 vs val 0.80) — cierra deuda XII.4.
   Incluye el jitter de bordes de segmento de R-006.
6. **Experimento de ablación de z** (R-002): 201 vs 134 coordenadas, mismas seeds, mismo split.

**Puerta de salida**: informe de evaluación reproducible con las cinco salidas del punto 3, y
decisión escrita sobre z. Si la ablación indica que z no aporta, eso **no** cambia el contrato en
esta fase: dispara el procedimiento de enmienda del Principio IV (que es NO NEGOCIABLE y exige
justamente evidencia empírica que contradiga la razón original).

### Fase C — Exportación al dispositivo y equivalencia de productores

**Objetivo**: que el modelo que corre en el navegador sea el modelo medido, y que el cliente
produzca los mismos vectores que el entrenamiento.

1. Export PyTorch → ONNX; verificación de paridad de logits contra PyTorch (tolerancia en R-003).
2. **Re-medir NFR-001a sobre el modelo exportado.** El baseline aplica al que realmente corre
   (riesgo abierto 3 de la entrada de planificación).
3. Implementación TypeScript de S2 y S3 contra el mismo `kp-contract.json`.
4. Runner de equivalencia `tools/contract-equivalence/` sobre los fixtures congelados, tolerancia
   1e-6 (NFR-014 Nivel 1) → **cablear como gate bloqueante de CI**.
5. Medición empírica de la cota de Nivel 2 (mismo video por ambos caminos). Se mide, no se asume
   (NFR-014).

**Puerta de salida**: SC-017 verde, incluida la prueba de que el test **falla** cuando se altera el
contrato deliberadamente. Un test de contrato que nunca se vio fallar no es evidencia de nada.

### Fase D — Segmentación, política de confianza y US1

**Objetivo**: el sistema reconoce señas sobre stream continuo, con confianza explícita. Es el
riesgo mayor del proyecto (DD-002).

1. Segmentador de actividad de señado (R-004), con histéresis, duración mínima y estado de reposo.
2. Distinción seña / no-seña (FR-034): ante duda, callar.
3. Generación de hasta 3 segmentaciones candidatas por evento (FR-009).
4. Módulo de política de confianza: umbral configurable (FR-016), regla de parada, compensación de
   optional stopping medida en `ml/eval/stopping.py` (NFR-019).
5. Decisión documentada: umbral global vs calibrado por clase (R-007), calibrado sobre el **sujeto
   de validación 9**, nunca sobre el sujeto 10 de test.
6. Modo de respaldo manual (FR-037) construido sobre el mismo pipeline desde el inicio.

**Puerta de decisión (NFR-022)**: la primera medición completa se ejecuta **en cuanto US1 esté
operativa**, no al cierre. Si con segmentación continua la accuracy no alcanza 0.70 en E1, el modo
manual pasa a predeterminado y la segmentación continua queda opcional, con la decisión y la
evidencia documentadas.

### Fase E — Presentación, voz y servicio de pulido

1. Presentación de texto con confianza en 3 categorías (FR-013), historial (FR-014), descartes
   (FR-019/FR-020).
2. Puerto de voz + adaptador Web Speech API, con la cadena de degradación de FR-012 y la política de
   no solapamiento de locuciones (R-010).
3. Agrupación por pausa: 1,5 s, máximo 5 señas por bloque (FR-038, valores provisionales).
4. `backend/polish`: endpoint, límite de uso, tamaño máximo, validación de vocabulario cerrado
   (NFR-026), métricas agregadas sin contenido (NFR-027), sin persistencia (NFR-024).
5. Validador de trazabilidad por lema (FR-040) en servicio **y** cliente.
6. Aviso previo a la primera salida de glosas (FR-041) e interruptor de pulido (NFR-025).

### Fase F — Robustez, evaluación de campo y validación con personas sordas

1. US3 (feedback de encuadre), US4–US8.
2. Build de evaluación separado (NFR-017), con la garantía verificable de SC-013.
3. Protocolo de 3 entornos (NFR-004), subconjunto congelado de 10 señas por sorteo con semilla
   (NFR-018), primera corrida en cuanto US1 y US3 estén completas.
4. Protocolo de validación con personas sordas (NFR-021): **la gestión de reclutamiento arranca 6
   semanas antes de cada ronda**. Es la dependencia con mayor riesgo de calendario del proyecto y
   por eso su tarea de inicio no cuelga del avance técnico: se dispara por fecha.
5. Revisión de los valores provisionales según el calendario de [research.md R-012](./research.md).

---

## Estrategia de pruebas

### Puertas bloqueantes de CI (Principio XIII + constitution §Flujo de Trabajo)

| Puerta | Qué verifica | Requisito |
|---|---|---|
| **G1 — Contrato de keypoints Nivel 1** | Dados los mismos landmarks crudos, Python y TypeScript producen el mismo vector 201; error absoluto por coordenada <= 1e-6; fixtures cubren las 64 clases | NFR-014, SC-017, Principio IV |
| **G2 — Umbral de confianza** | Comportamiento sobre y bajo umbral; nunca se revela etiqueta candidata bajo umbral; jamás más de 3 intentos | FR-016, FR-017, SC-005, SC-012, Principio VIII |
| **G3 — No exfiltración** | Con pulido desactivado, cero peticiones de red; con pulido activado, solo glosas: cero frames, cero keypoints, cero identificadores | NFR-006, SC-004, SC-023, Principio VII |

G1 debe además **fallar** ante una alteración deliberada del contrato; esa prueba negativa forma
parte de la puerta.

### Tests por área

**Etapas del pipeline (aisladas, `ml/tests/stages/`)**

- S1: video corto conocido → cantidad de frames esperada, forma `(T, 201)`, política de relleno
  cuando falta una mano, asignación izquierda/derecha por handedness.
- S2 (**normalización temporal**): los tres casos obligatorios —`T > SEQ_LEN`, `T < SEQ_LEN`,
  `T == SEQ_LEN`— más `T == 1` y `T == 0` (rechazo). Verificación de que los índices producidos son
  los del contrato, comparados contra una tabla congelada, no contra otra implementación.
- S2 (**mismo criterio en entrenamiento e inferencia**): el test de equivalencia G1 incluye
  secuencias de largos que no son múltiplos de `SEQ_LEN`, que es donde la divergencia aparece.
- S3: centrado en el punto medio de los hombros; verificación explícita de que la componente `z`
  **no** se altera; comportamiento cuando los landmarks 11/12 no están presentes.
- S4: correspondencia secuencia ↔ etiqueta ↔ sujeto ↔ repetición; ningún sujeto de test aparece en
  train; metadatos de procedencia completos.

**Regresión de métricas (`ml/tests/regression/`)**

- Global: accuracy por sujeto no cae por debajo del baseline vigente registrado.
- Por clase: ninguna clase cae por debajo de un **piso por clase** definido al fijar el baseline
  (recall mínimo por clase, registrado junto con la corrida de referencia). Un promedio estable
  puede esconder que una seña pasó de 0,9 a 0,2 de recall.
- El test compara contra el artefacto de la corrida de referencia, no contra un número escrito a
  mano en el test.

**Política de confianza (`frontend/tests/`)**

- Por encima y por debajo del umbral, en los tres niveles (estricto/normal/permisivo).
- Los 3 intentos: que se detenga al primero que supera el umbral; que agotados los 3 comunique "no
  entendí"; que jamás ejecute un cuarto.
- Que ninguna etiqueta candidata de ningún intento se filtre a la vista, al historial ni a la voz.

**Canal WebSocket (`backend/devinfer/tests/`, arnés dev/eval)**

- Formato de mensajes contra el schema de [contracts/inference-ws.md](./contracts/inference-ws.md);
  rechazo de secuencias con forma distinta de `(SEQ_LEN, 201)`.
- Reconexión con backoff y reanudación de sesión.
- Pérdida de conexión **durante** un reconocimiento: el cliente resuelve como no-reconocido, nunca
  queda esperando ni inventa un resultado.

**Degradación de TTS (`frontend/tests/`)**

- Sin voz `es-AR` → cae a `es-UY`, luego a cualquier `es-*`.
- Sin ninguna voz en español → lo informa explícitamente y continúa solo con texto, sin bloquear
  (US2 escenario 5).
- Sin `speechSynthesis` en el navegador → mismo camino, sin excepción no capturada.
- Locuciones consecutivas: un bloque nuevo no se solapa con el anterior (R-010).

**Servicio de pulido (`backend/polish/tests/`)**

- SC-021: 50 secuencias, cero palabras de contenido no trazables por lema.
- SC-025: rechazo del 100% de peticiones adversarias con texto libre.
- Límite de uso, tamaño máximo de 5 glosas, ausencia de persistencia y de IP asociada a contenido.
- Cliente: caída, límite excedido y presupuesto de NFR-023 agotado → glosa cruda, sin reintentos en
  bucle (SC-026).

**Validación con personas sordas (NFR-021)** — no es un test automatizado y no se sustituye por
uno. Ronda formativa sobre prototipo navegable al completarse US1 y US3; ronda sumativa sobre el
sistema completo antes del cierre. Registro: por participante, tarea T1–T5, completada sin ayuda
sí/no, tiempo de T1, fluidez declarada, si es usuaria nativa o tardía, y si la comunicación fue por
escrito o con intérprete. El informe se versiona en `specs/001-lsa-sign-translator/` junto a la
evidencia. Si el reclutamiento no se concreta, se declara **no validado** y se documenta como
limitación; no se aprueba por juicio interno.

---

## Trazabilidad

| Módulo / artefacto | Requisitos | Principio |
|---|---|---|
| `contracts/keypoints/` | NFR-014, SC-017 | IV, XIII |
| `ml/stages/s1_extract` | FR-002, FR-003 | III, IV, XII.1 |
| `ml/stages/s2_temporal` | NFR-014, edge case "seña más rápida o más lenta" | IV |
| `ml/stages/s3_spatial` | NFR-014 | IV |
| `ml/stages/s4_assemble` | NFR-002, NFR-001a | V, VI |
| `ml/train` | NFR-001a | V, VI, XII.4 |
| `ml/eval/*` | NFR-001a, NFR-002, NFR-019, NFR-022, SC-001, SC-016, SC-020 | V, VI |
| `ml/export` | NFR-001a, NFR-003 | V, IX |
| `ml/segment` (offline) | NFR-022, SC-020 | XII.2 |
| `frontend/capture` | FR-001, FR-004, FR-007, FR-021, FR-026 | — |
| `frontend/keypoints` | FR-002, FR-005, FR-035 | III, IV, VII |
| `frontend/contract` | NFR-014, SC-017 | IV, XIII |
| `frontend/segment` | FR-008, FR-034, FR-036, SC-018, SC-019 | XII.2 |
| `frontend/classify` | FR-003, NFR-015 | XI |
| `frontend/confidence` | FR-009, FR-013, FR-016, FR-017, NFR-019, SC-005, SC-012, SC-016 | VIII, XIII |
| `frontend/presentation/text` | FR-011, FR-013, FR-014, FR-015, NFR-011 | VIII |
| `frontend/presentation/voice` | FR-012, FR-024, FR-032, FR-038, NFR-010 | VIII |
| `frontend/polish` | FR-031, FR-039, FR-040, FR-041, NFR-023, NFR-025, SC-022, SC-026 | II, VII |
| `frontend/session` | FR-014, FR-019, FR-020, NFR-008, SC-008 | VII |
| `frontend/prefs` | FR-024, FR-025, FR-026, FR-027 | VIII |
| `frontend/eval` | NFR-017, SC-013 | VII |
| `backend/polish` | FR-039, FR-040, NFR-024, NFR-026, NFR-027, SC-021, SC-025 | II, VII |
| `backend/devinfer` | Ninguno de producción — soporte de NFR-014 Nivel 2 y de la evaluación | — |
| `tools/contract-equivalence` | NFR-014, SC-017 | IV, XIII |

Requisitos **sin módulo asignado en este plan**, por depender de contenido y no de código:
FR-028 (lista de 64 señas con significado), FR-029 (aviso de asistencia), FR-030 (atribución
LSA64). Se resuelven como contenido de la aplicación y del `Readme.md` de la raíz, y quedan como
tareas de la fase `tasks`.

---

## Riesgos y su manejo

| # | Riesgo | Origen | Manejo en este plan | Puerta |
|---|---|---|---|---|
| 1 | La segmentación continua no alcanza calidad usable | DD-002 | Fase D; modo manual (FR-037) construido desde el inicio, no agregado al final | NFR-022, medición temprana |
| 2 | Tasks API baja el baseline de 0.85 | Migración obligada | Fase B punto 2: justificación escrita y plan de recuperación **antes** de seguir | Principio V |
| 3 | Divergencia silenciosa entre productor Python y TypeScript | Principio IV | Contrato declarativo + fixtures + G1 bloqueante + prueba negativa | SC-017 |
| 4 | Optional stopping infla la confianza aparente | NFR-019 | `ml/eval/stopping.py`; umbrales elegidos con la medición, no por analogía | SC-016 |
| 5 | Tiempo muerto y transiciones ausentes en LSA64 cut | DD-002 | Jitter de bordes en augmentation (R-006); desglose de error de NFR-022 | SC-020 |
| 6 | El modelo del dispositivo no es el modelo medido | Riesgo 3 de la entrada | Fase C punto 2: NFR-001a se re-mide sobre el ONNX exportado | Principio V |
| 7 | Presupuesto de latencia no alcanza | NFR-003 | Desglose en R-008; hallazgo: el costo dominante es la histéresis del detector, no los 3 intentos | SC-002 |
| 8 | No se consigue participantes sordos | NFR-021 | Gestión disparada por fecha, 6 semanas antes, independiente del avance técnico | SC-006 |
| 9 | Valores provisionales llegan al cierre sin revisar | Spec §Assumptions | Tabla con criterio y momento de revisión en R-012 | — |

---

## Complexity Tracking

> Elementos que agregan complejidad y necesitan justificación explícita. Ninguno constituye una
> violación constitucional; se registran porque rozan una frontera y la revisión de PR debe poder
> verificarlo.

| Elemento | Por qué es necesario | Alternativa más simple, y por qué se descartó |
|---|---|---|
| `backend/devinfer` transporta keypoints por red | Permite iterar el modelo en PyTorch contra el cliente real antes de que exista el export a ONNX, y sostiene la medición empírica de NFR-014 Nivel 2 | Un único backend con una bandera de configuración. Descartado porque SC-004 exige que el build de producción **no contenga** la ruta de código, no que la tenga apagada: una propiedad verificable por inspección del artefacto no puede depender de una variable de entorno |
| Dos artefactos de modelo (checkpoint PyTorch + ONNX) | El artefacto que corre en el dispositivo debe ser el medido (NFR-001a) | Reportar la métrica del checkpoint y confiar en que el export la preserva. Descartado: es exactamente el tipo de supuesto no verificado que el Principio IV declara catastrófico |
| Validación de trazabilidad por lema duplicada en servicio y cliente | El cliente no puede confiar en que el servicio esté sano; FR-040 exige descartar la frase y pronunciar glosa cruda | Validar solo en el servidor. Descartado: una respuesta corrupta o un servicio comprometido pondría palabras en boca de una persona sorda, que es el daño concreto que DD-004 evita |
| Posible umbral calibrado por clase (R-007) | Si el desempeño varía fuertemente entre clases, un umbral global no separa aciertos de errores | Umbral global único. Se mantiene como opción **preferida** y solo se abandona con la evidencia por clase de la Fase B; la decisión y su justificación se documentan antes de implementarla |
| `contracts/keypoints/` en la raíz del repo y no dentro de `ml/` | Dos consumidores con el mismo derecho sobre la fuente de verdad | Alojarlo en `ml/`. Descartado: convierte a Python en la autoridad implícita y degrada el test de equivalencia a una comparación contra una referencia sesgada |

---

## Qué NO resuelve este plan

Declarado para que no quede implícito:

- **El modelo concreto del dispositivo de referencia (NFR-003)**. Requiere saber qué hardware tiene
  el equipo. Procedimiento y momento en R-008; el valor, no.
- **El runtime y el modelo del servicio de pulido (R-011)**. Depende de infraestructura disponible.
- **La cota empírica del Nivel 2 del contrato (NFR-014)**. Se mide en la Fase C, por definición.
- **El rango de duración de seña con el que el reconocimiento se mantiene sobre el umbral**. Se mide
  en la Fase B con el dataset regenerado.
- **Los valores finales de los umbrales de confianza**. Salen de NFR-019, no de este documento.

Ninguno es una omisión: los cinco están declarados en la spec como diferidos a esta fase **con
dueño y con requisito que los obliga**, y lo que esta fase debía aportar es el procedimiento y el
momento de resolución, que están en `research.md`.
