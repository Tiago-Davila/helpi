# Data Model: Traductor LSA de señas aisladas (LSA64)

**Feature**: `001-lsa-sign-translator` | **Date**: 2026-07-29 | **Plan**: [plan.md](./plan.md)

Fase 1 del flujo de planificación. Define las entidades del dominio, los formatos intermedios entre
etapas del pipeline de datos, el estado local del cliente y los artefactos versionados.

Los **contratos de interfaz** (qué mensajes cruzan cada frontera) están en
[contracts/](./contracts/). Este documento define **qué son los datos**; `contracts/` define **cómo
se transmiten**.

**Notación**: los tipos se escriben en pseudo-esquema. `f32[a, b]` es un arreglo de float32 de forma
`(a, b)`. Los campos marcados `!` son obligatorios.

---

## Índice

1. [Entidades del dominio](#1-entidades-del-dominio)
2. [Vector de keypoints (el contrato)](#2-vector-de-keypoints-el-contrato)
3. [Formatos intermedios del pipeline de datos](#3-formatos-intermedios-del-pipeline-de-datos)
4. [Dataset ensamblado](#4-dataset-ensamblado)
5. [Estado local del cliente](#5-estado-local-del-cliente)
6. [Registro local de sesión y de descartes](#6-registro-local-de-sesión-y-de-descartes)
7. [Artefacto de modelo](#7-artefacto-de-modelo)
8. [Registro de experimento e informe de evaluación](#8-registro-de-experimento-e-informe-de-evaluación)
9. [Registro del build de evaluación](#9-registro-del-build-de-evaluación)
10. [Trazabilidad](#10-trazabilidad)

---

## 1. Entidades del dominio

Derivadas de *Key Entities* de la [spec](./spec.md), con atributos y transiciones explícitas.

### 1.1 Sesión de grabación

Intervalo entre la acción humana de iniciar y la de detener (FR-007). Es la **única** unidad que
requiere acción humana (DD-002). Contiene cero o más eventos de seña detectada.

```text
SesionGrabacion {
  id!            : string            # local, efímero, NUNCA sale del dispositivo (DD-005)
  iniciada_en!   : timestamp
  detenida_en    : timestamp | null
  estado!        : DETENIDA | GRABANDO_DETECTANDO | GRABANDO_SIN_DETECTAR | PROCESANDO
  persona_seguida: TrackId | null    # mayor área de torso al iniciar; no alterna (edge case)
  eventos!       : EventoSenaDetectada[]
}
```

**Estados y transiciones** — los cuatro estados de FR-010 son mutuamente excluyentes y cada uno tiene
indicador visual distinto en color **y** en forma:

```text
DETENIDA ──(acción humana: grabar)──► GRABANDO_SIN_DETECTAR
GRABANDO_SIN_DETECTAR ◄──(persona sale de cuadro)──► GRABANDO_DETECTANDO
GRABANDO_DETECTANDO ──(segmentador declara fin de seña)──► PROCESANDO
PROCESANDO ──(política de confianza resuelve)──► GRABANDO_DETECTANDO
cualquiera ──(acción humana: detener)──► DETENIDA   # apaga la cámara, no solo el reconocimiento (FR-001)
```

Iniciar dos veces (edge case) no produce un estado ambiguo: la segunda acción detiene o se ignora,
nunca deja el sistema en un estado indeterminado.

### 1.2 Evento de seña detectada

Tramo del stream que el sistema identificó como actividad de señado (FR-008). **Existe con
independencia de que se logre clasificarlo**: el sistema puede saber que hubo una seña sin saber
cuál. Es la distinción que separa FR-008 de FR-003 y la que hace medible NFR-022.

```text
EventoSenaDetectada {
  id!            : string
  inicio_frame!  : int
  fin_frame!     : int
  duracion_ms!   : int
  intentos!      : IntentoReconocimiento[]    # 1..3, nunca más (SC-012)
  resultado!     : RECONOCIDA | NO_ENTENDIDA | DESCARTADA_POR_ENCUADRE
  motivo_descarte: MotivoDescarte | null      # solo si DESCARTADA_POR_ENCUADRE
}
```

`DESCARTADA_POR_ENCUADRE` cubre: manos fuera de cuadro a mitad de la seña, seña bimanual con una
mano ocluida, dispositivo girado durante la seña, detección perdida. En todos, el evento **no se
clasifica** (SC-011).

### 1.3 Intento de reconocimiento

Cada una de las hasta 3 segmentaciones candidatas que se evalúan sobre un mismo evento (FR-009,
R-004). Los 3 se generan y clasifican **en un solo lote**, no son tres esperas sucesivas.

```text
IntentoReconocimiento {
  numero!        : 1 | 2 | 3
  inicio_frame!  : int
  fin_frame!     : int
  etiqueta!      : GlosaId            # SIEMPRE presente internamente
  confianza!     : f32                # [0, 1], normalizada
  supera_umbral! : bool
}
```

**Invariante de privacidad de resultado**: `etiqueta` existe internamente en todo intento, pero
**ninguna etiqueta de un evento cuyo resultado sea `NO_ENTENDIDA` puede salir del módulo de política
de confianza** — ni a la vista, ni al historial, ni a la voz, ni al registro local (FR-017, SC-005).
Esta es una regla de negocio con test propio (puerta G2), no una convención.

### 1.4 Seña reconocida

Resultado de un intento que superó el umbral.

```text
SenaReconocida {
  id!            : string
  etiqueta!      : GlosaId
  confianza!     : f32
  categoria!     : ALTA | MEDIA            # "no entendí" NO es una SenaReconocida (FR-013)
  momento!       : timestamp
  estado!        : PRESENTADA | DESCARTADA
  evento_id!     : string
}
```

`categoria` se deriva de `confianza` y de **dos** fronteras, ambas provenientes de FR-016: **`ALTA`**
si `confianza >= umbral del nivel estricto`; **`MEDIA`** si está entre el umbral activo y el de
estricto. Por debajo del umbral activo no hay `SenaReconocida`: el evento resuelve `NO_ENTENDIDA`.
No hay ningún valor propio de esta capa — las dos fronteras se recalibran junto con los umbrales
según NFR-019, y con el nivel estricto activo toda seña aceptada es `ALTA`.

FR-013 prohíbe presentar la confianza **únicamente** como número: la categoría nombrada más su
indicador visual son obligatorios; el número es opcional y acompaña, nunca sustituye.

### 1.5 Vocabulario LSA64

Conjunto **cerrado** de 64 glosas. Es la frontera de lo reconocible: toda entrada fuera de él se
resuelve como no-reconocida, nunca se fuerza a la clase más cercana (FR-003, FR-017).

```text
Glosa {
  id!            : int                # 0..63, índice de clase del modelo
  clave!          : string            # identificador estable, p. ej. "agua"
  etiqueta_es!   : string            # significado en español para FR-028
  lema!          : string            # para la validación de FR-040
  flexiones!     : string[]          # formas admitidas en la frase pulida (FR-040)
}
```

El vocabulario se versiona junto con el artefacto de modelo: el mapa `id → clave` **es parte del
artefacto** (§7). Un modelo nuevo con un `labels.json` viejo produce traducciones sistemáticamente
equivocadas sin ningún error visible — el fallo silencioso del Principio IV aplicado a las etiquetas.

`lema` y `flexiones` son consumidos por el servicio de pulido y por el cliente para la doble
validación de FR-040 (AD-07).

### 1.6 Bloque de voz

Unidad de pronunciación (FR-038). La voz se agrupa por pausa; el texto no espera.

```text
BloqueVoz {
  glosas!        : GlosaId[]          # 1..5 (FR-038, NFR-026)
  cerrado_por!   : PAUSA | MAXIMO_ALCANZADO | FIN_DE_GRABACION
  frase_pulida   : string | null      # null si el pulido está desactivado o no disponible
  origen_frase   : SERVICIO | GLOSA_CRUDA
  estado!        : ACUMULANDO | EN_COLA | PRONUNCIANDO | PRONUNCIADO | CANCELADO
}
```

Solo entran glosas que superaron el umbral (FR-039(d)). La política de cola y no solapamiento está
en [research.md R-010](./research.md).

### 1.7 Condición de entorno

Estado detectado que impide o degrada el reconocimiento, con su mensaje accionable (FR-023).

```text
CondicionEntorno {
  tipo!          : SIN_PERSONA | MANOS_FUERA_DE_CUADRO | DEMASIADO_CERCA | DEMASIADO_LEJOS
                 | TORSO_NO_VISIBLE | LUZ_INSUFICIENTE | DETECCION_INTERMITENTE
                 | PERMISO_DENEGADO | CAMARA_NO_DISPONIBLE | CAMARA_OCLUIDA
                 | RENDIMIENTO_INSUFICIENTE | MULTIPLES_PERSONAS_AMBIGUAS
  mensaje!       : string             # nombra la causa Y una acción concreta ejecutable
  detectada_en!  : timestamp
}
```

Cada tipo tiene un mensaje distinto, verificable por revisión del catálogo (FR-023). La distancia a
la cámara **no se prescribe como número** (FR-035): `DEMASIADO_CERCA` / `DEMASIADO_LEJOS` indican
dirección de corrección, no una medida.

---

## 2. Vector de keypoints (el contrato)

Definición normativa completa en [contracts/keypoints.md](./contracts/keypoints.md). Resumen del
modelo de datos:

```text
FrameKeypoints = f32[201]

Disposición (orden fijo, no negociable — Principio IV):
  [  0 ..  62]  mano izquierda : 21 landmarks × (x, y, z)
  [ 63 .. 125]  mano derecha   : 21 landmarks × (x, y, z)
  [126 .. 200]  pose 0..24     : 25 landmarks × (x, y, z)

SecuenciaKeypoints = f32[SEQ_LEN, 201]      # SEQ_LEN parámetro del contrato, inicial 40
```

Reglas, idénticas para todo productor:

- **Centrado** en el punto medio de los hombros (pose landmarks 11 y 12), aplicado a `x` e `y`.
- **`z` no se centra.**
- **Largo fijo** por muestreo determinista de índices enteros ([research.md R-009](./research.md)).
- **Mano ausente**: 63 ceros en su ranura, aplicados **después** de la normalización espacial.

`SecuenciaKeypoints` es la entidad *Secuencia de keypoints* de la spec: representación numérica
anónima, producida y consumida **dentro del dispositivo** (NFR-006). Nunca se transmite, nunca se
persiste.

---

## 3. Formatos intermedios del pipeline de datos

Cuatro etapas independientes, cada una con entrada y salida explícitas en disco, ejecutable y
verificable por separado. Los contratos de interfaz están en
[contracts/pipeline-stages.md](./contracts/pipeline-stages.md); aquí van los formatos.

```text
S0: videos LSA64            →  S1  →  keypoints crudos (largo variable)
                            →  S2  →  secuencias de largo fijo
                            →  S3  →  secuencias normalizadas espacialmente
                            →  S4  →  dataset ensamblado con etiquetas y procedencia
```

> **Por qué separadas**: la POC concentró el preprocesamiento en un script monolítico y eso hizo
> imposible testear la normalización temporal —el punto de desalineación conocido— sin ejecutar todo
> el pipeline. Cada etapa aquí puede verificarse con datos sintéticos, sin video y sin modelo.

### S0 — Entrada: video de LSA64

```text
Archivo: <root>/lsa64_cut/<NN>_<SS>_<RR>.mp4
  NN = clase   (01..64)
  SS = sujeto  (01..10)
  RR = repetición (01..05)
```

La convención de nombres de LSA64 es la **única** fuente de etiqueta, sujeto y repetición. No se
infiere de ningún otro lado.

### S1 — Salida: keypoints crudos de largo variable

Un archivo por video. Es la única etapa que ve píxeles (Principio III: la imagen termina aquí).

```text
artifacts/<dataset-version>/s1_raw/<NN>_<SS>_<RR>.npz
{
  keypoints!   : f32[T, 201]     # T variable, sin normalizar espacialmente
  presencia!   : u8[T, 3]        # por frame: [mano_izq, mano_der, pose] detectada 0/1
  fps_efectivo!: f32
  T!           : int
}
```

`presencia` **no** forma parte del vector de 201 y no llega al modelo: es metadato de diagnóstico
para el desglose de NFR-022 y para el criterio de forma del rechazo de no-seña (R-004). Meterlo en
el vector rompería el contrato.

### S2 — Salida: secuencias de largo fijo

```text
artifacts/<dataset-version>/s2_fixed/<NN>_<SS>_<RR>.npz
{
  keypoints!   : f32[SEQ_LEN, 201]
  T_original!  : int
  indices!     : i32[SEQ_LEN]    # los índices efectivamente muestreados
}
```

`indices` se guarda deliberadamente: es lo que hace auditable la normalización temporal sin volver a
ejecutarla, y lo que se compara contra la tabla congelada de tests.

### S3 — Salida: secuencias normalizadas espacialmente

Misma forma que S2. Es una transformación pura, sin metadatos nuevos.

```text
artifacts/<dataset-version>/s3_norm/<NN>_<SS>_<RR>.npz
{
  keypoints!   : f32[SEQ_LEN, 201]    # centrado en hombros aplicado a x,y; z intacta
}
```

**El orden S2 → S3 es normativo.** Normalizar espacialmente antes de muestrear produce el mismo
resultado con más cómputo, pero fija el orden porque el cliente en vivo debe ejecutar exactamente la
misma cadena y una divergencia de orden es una divergencia de contrato aunque el resultado coincida
hoy.

### S4 — Salida: dataset ensamblado

Ver §4.

---

## 4. Dataset ensamblado

Salida de S4. Es lo que consume el entrenamiento.

```text
artifacts/<dataset-version>/dataset/
├── X.npy            : f32[M, SEQ_LEN, 201]
├── y.npy            : i64[M]              # id de clase 0..63
├── subject.npy      : i64[M]              # 1..10
├── repetition.npy   : i64[M]              # 1..5
├── source.json      : procedencia por muestra
└── MANIFEST.json    : metadatos del dataset
```

```text
MANIFEST.json {
  dataset_version!    : string        # p. ej. "lsa64-tasks-2.0.0"
  kp_contract_version!: string        # "2.0.0"
  seq_len!            : int
  n_classes!          : 64
  n_samples!          : int
  producer!           : { mediapipe_version, hand_model, pose_model, min_detection_confidence, ... }
  deps!               : { python, numpy, mediapipe, opencv }   # versiones exactas
  seed!               : int
  generated_at!       : timestamp
  content_hash!       : string
  splits! : {
    train: [1,2,3,4,5,6,7,8],
    val:   [9],
    test:  [10]
  }
}
```

**Reglas invariantes, con test en `ml/tests/stages/`**:

- `splits` es **por sujeto**. Ningún sujeto aparece en más de un split. El split aleatorio está
  prohibido para todo número reportable (NFR-002, Principio V). Que exista como campo del manifiesto
  y no como argumento de línea de comandos es deliberado: el split es una propiedad del dataset, no
  una opción de la corrida.
- `source.json` conserva la procedencia de cada muestra (archivo de origen, `T_original`, `indices`).
  Sin ella no se puede volver de un error de clasificación al video que lo produjo, que es la
  operación básica del análisis de R-013.
- `content_hash` cubre `X`, `y`, `subject` y `repetition`. Es lo que permite afirmar que dos
  experimentos corrieron sobre el mismo dataset (Principio VI).

**Variante de ablación (R-002)**: la configuración de 134 coordenadas **no genera un dataset
distinto**. Se deriva de `X` eliminando las 67 componentes `z` en tiempo de carga, con la selección
de índices declarada en el registro del experimento. Duplicar el dataset multiplicaría las
oportunidades de que las dos copias se desincronicen.

---

## 5. Estado local del cliente

Todo local al dispositivo. Nada de esta sección sale por la red (NFR-006, DD-005).

| Dato | Dónde vive | Persiste al cerrar | Requisito |
|---|---|---|---|
| Historial de sesión | Memoria | **No** (FR-014, US5 esc. 3) | FR-014, FR-015 |
| Registro de descartes | `localStorage` | Sí, hasta que se borra | FR-020, NFR-008 |
| Preferencias | `localStorage` | Sí | FR-027 |
| Secuencias de keypoints | Memoria, buffer circular | **No** | NFR-006, NFR-007 |
| Frames de video | **Ningún lado** | — | NFR-007 |

```text
Preferencias {
  umbral!            : ESTRICTO | NORMAL | PERMISIVO    # predeterminado NORMAL (FR-016)
  voz_activada!      : bool
  voz_id             : string | null                    # revalidada al arrancar (R-010)
  modo_espejo!       : bool                             # predeterminado true (FR-026)
  pulido_activado!   : bool                             # predeterminado false hasta el aviso de FR-041
  aviso_pulido_visto!: bool                             # FR-041, SC-023
  modo_captura!      : CONTINUO | MANUAL                # FR-037
}
```

`pulido_activado` arranca en `false` y solo puede pasar a `true` **después** de que
`aviso_pulido_visto` sea `true`. Es la garantía verificable de SC-023: en una instalación nueva,
ninguna glosa sale del dispositivo antes de que la persona haya visto el aviso y decidido.

`modo_espejo` afecta **solo** a la presentación del `<video>`. El frame que recibe el detector nunca
va espejado ([research.md R-001](./research.md)); hay un test de contrato para esto porque espejar
la entrada intercambia las dos mitades de 63 valores del vector.

**Buffer de keypoints**: circular, de duración acotada (suficiente para la seña más larga admitida
más margen para las 3 candidatas). Se sobrescribe continuamente y se vacía al detener la grabación.
Nunca se serializa a disco.

---

## 6. Registro local de sesión y de descartes

FR-020 exige registrar cada descarte para poder calcular la tasa de error percibida (SC-008), y
NFR-008 exige que sea local y borrable.

### 6.1 Historial de sesión (efímero, en memoria)

```text
HistorialSesion {
  entradas! : SenaReconocida[]    # orden cronológico, solo estado PRESENTADA
}
```

- Los descartes **no figuran** en el historial (US5 *Independent Test*).
- Se limpia con acción explícita **con confirmación** (FR-015).
- **No se restaura** al reabrir la aplicación (US5 esc. 3). No se persiste en ningún medio.

### 6.2 Registro de descartes (persistente, `localStorage`)

```text
localStorage["helpi.discards.v1"] = {
  schema_version! : 1
  entradas!       : [
    {
      etiqueta!   : GlosaId       # la que se PRESENTÓ, es decir, superó el umbral
      confianza!  : f32
      momento!    : timestamp
    }
  ]
}
```

**Solo se registran descartes de señas que fueron presentadas.** Un evento resuelto como
`NO_ENTENDIDA` nunca llega al registro, porque su etiqueta no puede salir del módulo de confianza
(§1.3). Esto es lo que impide que el registro local se convierta en la puerta trasera por donde la
etiqueta candidata prohibida por FR-017 termina siendo observable.

**Tasa de error percibida** (SC-008), calculable sin instrumentación adicional:

```text
tasa = |descartes| / |reconocimientos presentados|
```

### 6.3 Borrado

Una sola acción de "borrar los datos de la sesión" elimina, de forma conjunta y verificable
(US4 esc. 4, FR-020, NFR-008):

1. El historial de sesión en memoria.
2. `helpi.discards.v1` de `localStorage`.
3. El buffer de keypoints.

Las preferencias **no** se borran con esta acción: son una decisión distinta, con su propio control.

**Lo que nunca hay que borrar porque nunca se escribió**: video, frames y miniaturas. NFR-007 es
verificable justamente así — tras una sesión de >= 10 capturas, la inspección de `localStorage`,
`IndexedDB`, `Cache Storage` y del sistema de archivos no revela ningún artefacto de video.

---

## 7. Artefacto de modelo

Un modelo sin sus metadatos no es citable (Principio VI). El artefacto es el **directorio completo**,
no el archivo de pesos.

```text
ml/artifacts/<experiment-id>/model/
├── model.onnx           # el que corre en el dispositivo — es el que NFR-001a mide
├── model.pt             # checkpoint PyTorch, para reproducir y re-exportar
├── labels.json          # id → clave → etiqueta_es, para las 64 glosas
├── thresholds.json      # umbral global o vector por clase (R-007), por nivel
└── model-card.json      # metadatos obligatorios
```

### `model-card.json` — metadatos obligatorios

```text
{
  model_id!            : string
  created_at!          : timestamp
  experiment_id!       : string

  dataset! : {
    dataset_version!   : string
    content_hash!      : string
    kp_contract_version!: string
    splits!            : { train: [...], val: [9], test: [10] }
  }

  training! : {
    seed!              : int
    architecture!      : { type: "lstm", layers: 2, hidden: 128, dropout: 0.3 }
    input_dim!         : 201            # o 134 si la enmienda de R-002 prosperara
    seq_len!           : int
    optimizer!         : { name: "adam", lr: 3e-4 }
    early_stopping!    : { patience: 15, monitor: "val_accuracy" }
    augmentation!      : { border_jitter: 0.15, ... }   # explícito, incluso si está vacío
  }

  deps!                : { python, torch, numpy, onnx, onnxruntime, mediapipe }  # versiones exactas

  metrics! : {
    per_subject! : {
      protocol!        : "subject-holdout"     # NUNCA "random"
      test_subject!    : 10
      accuracy!        : f32                   # NFR-001a
      baseline!        : 0.85
      delta!           : f32
      justification    : string | null         # OBLIGATORIA si delta < 0 (Principio V)
    }
    loso              : { per_subject_accuracy: {1..10}, mean, std }   # deuda XII.3
    per_class!        : { <glosa>: { precision, recall, support } }
    confusion_matrix! : ref                     # ruta al artefacto de evaluación
    confidence!       : ref
    exported_parity!  : { max_abs_logit_diff, accuracy_delta_vs_pt }   # R-003
  }

  runtime! : { format: "onnx", opset: int, size_bytes: int }
}
```

### Reglas de versionado

- **Directorio por corrida, nunca se sobrescribe** (Principio VI). `<experiment-id>` incluye fecha y
  un identificador corto.
- `labels.json` y `thresholds.json` viajan **con** el modelo y se cargan juntos. Cargar un modelo y
  unas etiquetas de corridas distintas es un fallo silencioso: el sistema traduce todo mal sin
  reportar ningún error.
- El cliente valida al cargar que `kp_contract_version` del modelo coincide con la del contrato que
  él implementa. Si no coinciden, **falla de forma ruidosa** en vez de clasificar.
- `metrics.per_subject.protocol` solo admite `"subject-holdout"`. Un artefacto con métrica de split
  aleatorio no es publicable (NFR-002).
- `justification` es obligatoria cuando `delta < 0`: sin ella, el artefacto no se mergea
  (Principio V).

---

## 8. Registro de experimento e informe de evaluación

### 8.1 Registro de experimento

```text
ml/artifacts/<experiment-id>/
├── config.json      # configuración completa: hiperparámetros, arquitectura, seq_len, augmentation
├── env.json         # versiones exactas de dependencias, GPU, driver
├── logs/
├── model/           # §7
└── eval/            # §8.2
```

Un experimento sin `config.json` y `env.json` **no existe**: sus números no son citables
(Principio VI). Esto incluye las corridas de ablación de R-002 y las 10 corridas de LOSO.

### 8.2 Informe de evaluación

Se **regenera en cada evaluación** del modelo, no es un análisis puntual. Cinco salidas obligatorias:

```text
ml/artifacts/<experiment-id>/eval/
├── summary.json          # accuracy global con split por sujeto (NFR-001a) + LOSO
├── confusion.npy         # i64[64, 64], filas = verdadero, columnas = predicho
├── confusion.csv         # misma matriz, legible
├── per_class.json        # { <glosa>: { precision, recall, f1, support } } × 64
├── confused_pairs.json   # pares ordenados por conf(i→j) + conf(j→i), con conteos
├── confidence.json       # distribución de confianza: aciertos vs errores, global y por clase
├── stopping.json         # curva FPR/cobertura × umbral × k∈{1,2,3} (NFR-019, R-005)
└── report.md             # informe legible que enlaza lo anterior
```

```text
summary.json {
  protocol!        : "subject-holdout"
  test_subject!    : 10
  accuracy!        : f32
  baseline!        : 0.85
  delta!           : f32
  per_class_floor! : { min_recall: f32, worst_classes: [...] }   # piso de regresión por clase
  loso             : { per_subject: {...}, mean, std }
}
```

```text
confused_pairs.json [
  { a: GlosaId, b: GlosaId, a_to_b: int, b_to_a: int, total: int, hypothesis: string | null }
]
```

`hypothesis` se completa con la clasificación de causa de [research.md R-013](./research.md)
(trayectoria compartida / keypoints insuficientes / datos insuficientes). Es el campo que convierte
el diagnóstico en trabajo priorizado.

```text
confidence.json {
  correct!   : { histogram: [...], mean, p05, p25, p50, p75, p95 }
  incorrect! : { histogram: [...], mean, p05, p25, p50, p75, p95 }
  overlap!   : f32     # solapamiento de ambas distribuciones — decide si el umbral separa (R-007)
  per_class  : { <glosa>: { correct: {...}, incorrect: {...} } }
}
```

`overlap` es el número que decide si algún umbral puede cumplir el Principio VIII. Si las dos
distribuciones se solapan fuertemente en todas las clases, el problema es de modelo y ninguna
política de confianza lo arregla; ese hallazgo se reporta como tal en vez de esconderse detrás de un
umbral más alto.

### 8.3 Regresión de métricas

El test de regresión (`ml/tests/regression/`) compara contra el `summary.json` de la **corrida de
referencia**, no contra números escritos en el test:

- Accuracy global por sujeto no cae por debajo del baseline registrado.
- **Ninguna clase individual** cae por debajo de `per_class_floor.min_recall`. Un promedio estable
  puede esconder que una seña pasó de 0,9 a 0,2 de recall — y para la persona que usa esa seña, el
  sistema dejó de funcionar.

### 8.4 Desglose de error de la segmentación (NFR-022)

Producido por la evaluación del sistema desplegado, contra la anotación humana de referencia:

```text
segmentation_error.json {
  reference!      : { annotator, n_events, source }
  a_no_detectada! : int      # señas realizadas que el sistema no detectó
  b_falso_positivo!: int     # segmentos detectados que no eran señas (FR-034)
  c_mal_delimitada!: int     # detectadas pero recortadas o fusionadas
  d_mal_clasificada!: int    # bien delimitadas y mal clasificadas
  nfr_001a!       : f32
  nfr_001b!       : f32
  attribution!    : string   # la diferencia entre ambas, explicada (SC-020)
}
```

Sin este desglose, la diferencia entre NFR-001a y NFR-001b queda reportada pero no explicada, que es
exactamente lo que SC-020 prohíbe.

---

## 9. Registro del build de evaluación

NFR-017. Build **separado** del de producción; el de producción no contiene esta instrumentación
(SC-013, verificable por inspección del artefacto distribuido).

```text
RegistroMedicion {
  consentimiento! : { otorgado_en, version_texto, revocable: true }
  sesion! : {
    entorno!        : E1 | E2 | E3
    lux!            : f32
    distancia_m!    : f32          # se registra, NO es criterio de admisión (NFR-004)
    resolucion!     : { w, h }     # >= 640×480 o sesión inválida
    fps_efectivo!   : f32          # >= 15 o sesión inválida
    tipo_fondo!     : ESTATICO_LISO | ESTATICO_TEXTURA | DINAMICO_PERSONAS
    dispositivo!    : string
    lateralidad_declarada : DIESTRA | ZURDA | NO_DECLARADA
  }
  eventos! : [
    { sena_esperada!, sena_reconocida!, confianza!, n_intentos!, resultado! }
  ]
}
```

**Nunca** se registra ni se exporta video, **incluida** la medición de L2, que usa un dispositivo
externo a la aplicación (NFR-003, NFR-017(c)). La exportación contiene **solo** métricas.

El subconjunto de señas es la muestra congelada de 10 de NFR-018, sorteada con semilla registrada
antes de la primera medición e idéntica en los tres entornos y en toda medición posterior.

---

## 10. Trazabilidad

| Sección | Entidad / formato | Requisitos |
|---|---|---|
| §1.1 | Sesión de grabación | FR-001, FR-004, FR-007, FR-010 |
| §1.2 | Evento de seña detectada | FR-008, FR-034, FR-036, SC-011, SC-018, SC-019 |
| §1.3 | Intento de reconocimiento | FR-009, FR-017, SC-005, SC-012 |
| §1.4 | Seña reconocida | FR-003, FR-011, FR-013 |
| §1.5 | Vocabulario LSA64 | FR-003, FR-028, FR-040 |
| §1.6 | Bloque de voz | FR-012, FR-038, FR-039, NFR-023 |
| §1.7 | Condición de entorno | FR-006, FR-023, FR-035, SC-009 |
| §2 | Vector de keypoints | NFR-014, SC-017, Principio IV |
| §3 | Formatos S1–S4 | NFR-014, NFR-002 |
| §4 | Dataset ensamblado | NFR-001a, NFR-002, Principios V y VI |
| §5 | Estado local | FR-024–FR-027, FR-041, NFR-006, NFR-007, SC-023 |
| §6 | Historial y descartes | FR-014, FR-015, FR-019, FR-020, NFR-008, SC-008 |
| §7 | Artefacto de modelo | NFR-001a, NFR-014, Principios V y VI |
| §8 | Experimento y evaluación | NFR-001a, NFR-019, NFR-022, SC-001, SC-016, SC-020 |
| §9 | Build de evaluación | NFR-004, NFR-005, NFR-017, NFR-018, SC-003, SC-013 |
