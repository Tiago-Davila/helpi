---
description: "Tareas de implementación — Traductor LSA de señas aisladas (LSA64)"
---

# Tasks: Traductor LSA de señas aisladas (LSA64) con confianza explícita

**Feature**: `001-lsa-sign-translator` | **Date**: 2026-07-29

**Input**: [spec.md](./spec.md) · [plan.md](./plan.md) · [research.md](./research.md) ·
[data-model.md](./data-model.md) · [contracts/](./contracts/) · [quickstart.md](./quickstart.md)

**Constitution**: [.specify/memory/constitution.md](../../.specify/memory/constitution.md) v1.0.1

---

## Cómo leer este documento

### Formato

```text
- [ ] T### [P] [TIPO] [US#] Descripción con ruta de archivo concreta
      ↳ Traza: <requisitos>  ·  Dep: <IDs>  ·  Repo: <ml|frontend|backend|raíz>
```

| Marca | Significado |
|---|---|
| `[P]` | Paralelizable: archivos distintos, sin dependencia de tareas incompletas |
| `[IMPL]` | Implementación. Resultado determinista, con criterio de terminado objetivo |
| `[EXP]` | Experimento. **Puede fallar y eso es información válida.** Declara hipótesis, métrica con split por sujeto, qué se hace si el resultado es negativo, y dónde se documenta |
| `[US#]` | Historia de usuario de [spec.md](./spec.md) a la que traza |
| `[BLOQUEADA: X]` | Depende de un valor todavía no decidido. **No se asume el valor**: se resuelve X primero |

### Reglas que gobiernan esta lista

1. **Tests de contrato primero (regla C).** Los tests del contrato de keypoints y de la
   normalización temporal se escriben **antes** que los componentes que los consumen. La fase
   exploratoria demostró que una desalineación de este contrato es silenciosa: el sistema no falla,
   entrega resultados incorrectos con apariencia normal.
2. **El baseline heredado está invalidado (regla D).** La migración a Tasks API y el cambio a
   aritmética entera regeneran el dataset. **Ninguna tarea de esta lista cita 0.85 como dato
   vigente**: la Fase 3 produce un baseline nuevo, medido con el pipeline nuevo, y T044 lo fija como
   valor de referencia en la documentación. Hasta que T044 se complete, el proyecto **no tiene**
   número de referencia vigente.
3. **El contrato es de 201 coordenadas (regla E).** Ninguna tarea implementa el cambio a 134. La
   ablación de `z` (T049) es `[EXP]`, y su producto es **evidencia** para el procedimiento de
   enmienda del Principio IV, no un cambio de contrato.
4. **Dev y producción están separados por construcción (regla F).** `backend/devinfer` transporta
   keypoints por red, lo que en producción está prohibido (NFR-006, SC-004). T085–T087 construyen
   la barrera y T088 verifica que el build de producción no lo incluye ni lo alcanza por red.
5. **Una tarea `[EXP]` nunca bloquea a una `[IMPL]`**, salvo declaración explícita. En esta lista
   hay **una sola** excepción declarada: T042 (justificación escrita si el baseline cae) bloquea a
   T044, porque el Principio V lo exige.
6. **Trazabilidad (regla G).** Toda tarea referencia el requisito que implementa o la decisión que
   la origina. **Cero tareas sin referencia** en esta lista.

### Tests obligatorios (no opcionales)

Por el Principio XIII y las puertas de CI de la constitution, estos tests son **bloqueantes** y no
están sujetos a la regla general de "tests opcionales":

| Puerta | Qué verifica | Tareas |
|---|---|---|
| **G1** | Contrato de keypoints Nivel 1: equivalencia Python ↔ TypeScript, <= 1e-6, con prueba negativa | T012–T019, T036, T037 |
| **G2** | Umbral de confianza y comportamiento bajo umbral | T065–T070 |
| **G3** | No exfiltración: cero video, cero keypoints en el tráfico saliente | T088, T173–T175 |

---

## Decisiones abiertas y qué bloquean

Las cinco decisiones que [plan.md](./plan.md) §*Qué NO resuelve este plan* dejó abiertas por
depender de datos externos al repositorio. **Ninguna tarea asume un valor no decidido.**

| # | Decisión abierta | Qué hay que resolver para desbloquear | Tareas bloqueadas |
|---|---|---|---|
| **D1** | Modelo concreto del dispositivo de referencia (NFR-003) | Declarar teléfono de gama media y notebook con resolución y fps efectivos **medidos**, e incorporarlos a la lista de NFR-020 | T157, T160, T161 |
| **D2** | Runtime, modelo y despliegue del servicio de pulido (R-011) | Decidir dónde corre y con qué modelo. La spec descarta delegarlo a una API comercial de terceros | T144, T145 |
| **D3** | Cota empírica del Nivel 2 del contrato (NFR-014) | Se **mide** en T091. No se asume | T091 la produce; nadie la consume antes |
| **D4** | Rango de duración de seña sobre el umbral | Se mide en T048 con el dataset regenerado | T106 (parámetros de duración del segmentador) |
| **D5** | Valores finales de los umbrales de confianza (NFR-019) | Salen de la curva de T059–T061. Los nominales de FR-016 son punto de partida documentado, no valores finales | T062, T063 |

**D6 — dependencia externa, no técnica**: el reclutamiento de participantes sordos (NFR-021) es la
dependencia con mayor riesgo de calendario. T135 se dispara **por fecha**, 6 semanas antes de cada
ronda, y no cuelga del avance técnico.

---

## Fase 1 — Setup y contrato de datos

**Propósito**: dejar el contrato de keypoints definido, implementado en ambos entornos y verificado.
**Nada se construye antes de esto.**

**Orden interno**: los tests de S2 y S3 (T012–T015) se escriben **antes** que sus implementaciones,
que están en la Fase 2. Esto es deliberado (regla C).

### Estructura (raíz)

- [ ] T001 [IMPL] Crear la estructura de directorios del monorepo (`ml/`, `frontend/`, `backend/polish/`, `backend/devinfer/`, `tools/`, `contracts/keypoints/`) según [plan.md](./plan.md) §Project Structure
      ↳ Traza: Principio XI · Dep: — · Repo: raíz
- [ ] T002 [P] [IMPL] Inicializar el proyecto Python en `ml/pyproject.toml` (Python 3.12, PyTorch, NumPy, scikit-learn, mediapipe Tasks, onnx, onnxruntime, pytest, ruff) sin pin a `mediapipe==0.10.21`
      ↳ Traza: plan §Technical Context, deuda XII.1 · Dep: T001 · Repo: ml
- [ ] T003 [P] [IMPL] Inicializar el cliente web en `frontend/package.json` (React 18, TypeScript 5, Vite, `@mediapipe/tasks-vision`, `onnxruntime-web`, vitest, Playwright)
      ↳ Traza: plan §Technical Context · Dep: T001 · Repo: frontend
- [ ] T004 [P] [IMPL] Inicializar `backend/polish/pyproject.toml` (FastAPI, `uvicorn[standard]`, pytest) **sin** dependencia de PyTorch ni de MediaPipe
      ↳ Traza: DD-003, NFR-026 · Dep: T001 · Repo: backend
- [ ] T005 [P] [IMPL] Inicializar `backend/devinfer/pyproject.toml` como paquete **separado** de `backend/polish` (FastAPI, WebSocket, PyTorch en modo eval)
      ↳ Traza: [contracts/inference-ws.md](./contracts/inference-ws.md) §Advertencia de alcance, SC-004 · Dep: T001 · Repo: backend
- [ ] T006 [P] [IMPL] Configurar linting y formato: `ruff` en `ml/` y `backend/`, ESLint + Prettier en `frontend/`
      ↳ Traza: convenciones de `Claude.md` · Dep: T002, T003, T004, T005 · Repo: raíz

### Contrato declarativo (raíz) — fuente de verdad única

- [ ] T007 [IMPL] Crear `contracts/keypoints/kp-contract.json` versión `2.0.0`: `SEQ_LEN=40`, dimensiones de bloques (63/63/75), índices de hombros (11, 12), tolerancia Nivel 1 (1e-6), criterio de muestreo (`integer-linspace`)
      ↳ Traza: Principio IV, [contracts/keypoints.md](./contracts/keypoints.md) §1 · Dep: T001 · Repo: raíz
- [ ] T008 [IMPL] Crear `contracts/keypoints/landmark-map.json`: orden de los 21 landmarks por mano, correspondencia `handedness` → ranura izquierda/derecha, e índices de pose 0–24 con los 25–32 descartados
      ↳ Traza: [contracts/keypoints.md](./contracts/keypoints.md) §2, R-001 · Dep: T007 · Repo: raíz
- [ ] T009 [IMPL] Crear la **tabla congelada de índices temporales** `contracts/keypoints/fixtures/temporal-index-table.json` con pares `(T, N) → idx[]` para `T ∈ {1, 5, 39, 40, 41, 90, 137}` y `N=40`, calculada con `idx[i] = (i*(T-1)) DIV (N-1)`
      ↳ Traza: R-009, [contracts/keypoints.md](./contracts/keypoints.md) §3 · Dep: T007 · Repo: raíz
- [ ] T010 [P] [IMPL] Implementar el lector del contrato en `ml/src/lsa_ml/contract/loader.py` — **prohibido** escribir `SEQ_LEN`, dimensiones o índices de hombros en el código
      ↳ Traza: Principio IV, [contracts/keypoints.md](./contracts/keypoints.md) §6 · Dep: T007 · Repo: ml
- [ ] T011 [P] [IMPL] Implementar el lector del contrato en `frontend/src/contract/loader.ts`, leyendo el **mismo** `kp-contract.json`
      ↳ Traza: AD-01, Principio IV · Dep: T007 · Repo: frontend

### Tests del contrato — ANTES de las implementaciones (regla C)

- [ ] T012 [P] [IMPL] Escribir los tests aislados de normalización temporal en `ml/tests/stages/test_s2_temporal.py`: `T>N`, `T<N`, `T==N`, `T==1`, `T==0` (rechazo con error explícito), e `indices` comparados contra la tabla congelada de T009
      ↳ Traza: NFR-014, R-009, regla C · Dep: T009, T010 · Repo: ml
- [ ] T013 [P] [IMPL] Escribir los tests aislados de normalización temporal en `frontend/tests/contract/s2-temporal.test.ts`, con los mismos casos y contra la **misma** tabla congelada
      ↳ Traza: NFR-014, R-009, regla C · Dep: T009, T011 · Repo: frontend
- [ ] T014 [P] [IMPL] Escribir los tests aislados de normalización espacial en `ml/tests/stages/test_s3_spatial.py`: punto medio de hombros en `(0,0)`, **componente `z` idéntica a la entrada**, rechazo si faltan los landmarks 11/12, manos ausentes intactas en cero
      ↳ Traza: NFR-014, [contracts/keypoints.md](./contracts/keypoints.md) §4 · Dep: T010 · Repo: ml
- [ ] T015 [P] [IMPL] Escribir los tests aislados de normalización espacial en `frontend/tests/contract/s3-spatial.test.ts` con los mismos casos
      ↳ Traza: NFR-014 · Dep: T011 · Repo: frontend
- [ ] T016 [IMPL] Crear los **fixtures sintéticos** de landmarks crudos en `contracts/keypoints/fixtures/raw/synthetic/` que ejerciten los casos borde: mano izquierda ausente, mano derecha ausente, ambas ausentes, colisión de handedness, torso ausente
      ↳ Traza: NFR-014, R-001 · Dep: T008 · Repo: raíz
- [ ] T017 [IMPL] Implementar el runner de equivalencia en `tools/contract-equivalence/` que ejecuta ambos productores sobre los fixtures y compara con error absoluto por coordenada <= 1e-6, emitiendo `artifacts/contract-report.json` con la cobertura alcanzada
      ↳ Traza: NFR-014 Nivel 1, SC-017 · Dep: T012, T013, T014, T015, T016 · Repo: raíz
- [ ] T018 [IMPL] Implementar la **prueba negativa** del runner: `--inject-fault {center-z, round-indices, swap-hands}` debe hacer **fallar** la comparación en `tools/contract-equivalence/faults.py`
      ↳ Traza: SC-017 ("falla el build cuando se lo altera deliberadamente") · Dep: T017 · Repo: raíz
- [ ] T019 [IMPL] Cablear la puerta **G1** como job bloqueante de CI en `.github/workflows/ci.yml`, incluyendo la prueba negativa de T018
      ↳ Traza: Principio XIII, constitution §Puertas de CI · Dep: T018 · Repo: raíz

> **Checkpoint 1 — no se pasa a la Fase 2 hasta que**:
> `kp-contract.json` y `landmark-map.json` están congelados en `2.0.0`; los tests de S2 y S3 existen
> en **ambos** entornos y **fallan** por ausencia de implementación (no por error de setup); el
> runner de equivalencia corre y su prueba negativa falla como corresponde; G1 está en CI.

---

## Fase 2 — Pipeline de datos por etapas

**Propósito**: cuatro etapas independientes, cada una con entrada y salida explícitas, ejecutable y
verificable por separado. **No se concentra el preprocesamiento en un script monolítico.**

### S1 — Extracción de keypoints (ml)

- [ ] T020 [IMPL] Implementar `ml/src/lsa_ml/stages/s1_extract.py` con MediaPipe **Tasks API** (`HandLandmarker` + `PoseLandmarker`), eliminando toda dependencia de Holistic; salida `(T, 201)` + `presencia (T,3)` + `fps_efectivo` según [data-model.md §3](./data-model.md)
      ↳ Traza: FR-002, R-001, deuda XII.1 · Dep: T010 · Repo: ml
- [ ] T021 [IMPL] Implementar la asignación izquierda/derecha por `handedness` con resolución de colisión por score (se conserva la de mayor score, la otra ranura queda ausente) en `ml/src/lsa_ml/stages/s1_extract.py`
      ↳ Traza: R-001, [contracts/keypoints.md](./contracts/keypoints.md) §2.1 · Dep: T020 · Repo: ml
- [ ] T022 [IMPL] Implementar la política de relleno de mano ausente (63 ceros aplicados **después** de la normalización espacial) y el registro de `presencia` **fuera** del vector, en `ml/src/lsa_ml/stages/s1_extract.py`
      ↳ Traza: [contracts/keypoints.md](./contracts/keypoints.md) §2.3, R-001 · Dep: T020 · Repo: ml
- [ ] T023 [IMPL] Garantizar timestamps estrictamente crecientes en la invocación de `detectForVideo` y agregar el test de regresión en `ml/tests/stages/test_s1_timestamps.py` — bug ya sufrido en la POC (`Claude.md`)
      ↳ Traza: [contracts/pipeline-stages.md](./contracts/pipeline-stages.md) §S1 · Dep: T020 · Repo: ml
- [ ] T024 [P] [IMPL] Escribir los tests de S1 en `ml/tests/stages/test_s1_extract.py`: `T` = frames procesados (ningún frame se descarta), forma `(T,201)`, relleno de mano ausente, asignación de handedness, colisión resuelta por score
      ↳ Traza: [contracts/pipeline-stages.md](./contracts/pipeline-stages.md) §S1 · Dep: T021, T022 · Repo: ml

### S2, S3, S4 (ml)

- [ ] T025 [IMPL] Implementar `ml/src/lsa_ml/stages/s2_temporal.py` con aritmética entera exacta `idx[i] = (i*(T-1)) DIV (N-1)`, persistiendo `indices` y `T_original`; **hacer pasar T012**
      ↳ Traza: R-009, AD-02, NFR-014 · Dep: T012 · Repo: ml
- [ ] T026 [IMPL] Implementar `ml/src/lsa_ml/stages/s3_spatial.py` (centrado en punto medio de hombros sobre `x,y`; `z` sin tocar; rechazo si faltan 11/12); **hacer pasar T014**
      ↳ Traza: Principio IV, [contracts/keypoints.md](./contracts/keypoints.md) §4 · Dep: T014, T025 · Repo: ml
- [ ] T027 [IMPL] Implementar `ml/src/lsa_ml/stages/s4_assemble.py`: `X.npy`, `y.npy`, `subject.npy`, `repetition.npy`, `source.json` y `MANIFEST.json` con splits por sujeto, seed, versiones exactas de dependencias y `content_hash`
      ↳ Traza: NFR-002, Principios V y VI, [data-model.md §4](./data-model.md) · Dep: T026 · Repo: ml
- [ ] T028 [P] [IMPL] Escribir los tests de S4 en `ml/tests/stages/test_s4_assemble.py`: correspondencia secuencia↔etiqueta↔sujeto↔repetición desde la convención de nombres de LSA64, **ningún sujeto en más de un split**, manifiesto completo
      ↳ Traza: NFR-002, Principio V · Dep: T027 · Repo: ml
- [ ] T029 [IMPL] Escribir el test de reproducibilidad en `ml/tests/stages/test_pipeline_reproducible.py`: re-ejecutar S1→S4 con la misma seed y versiones produce el **mismo `content_hash`**
      ↳ Traza: Principio VI · Dep: T027 · Repo: ml

### Contrato en TypeScript y cierre de G1 (frontend + raíz)

- [ ] T030 [P] [IMPL] Implementar S2 en `frontend/src/contract/temporal.ts` contra el mismo `kp-contract.json`; **hacer pasar T013**
      ↳ Traza: NFR-014, R-009 · Dep: T013 · Repo: frontend
- [ ] T031 [P] [IMPL] Implementar S3 en `frontend/src/contract/spatial.ts`; **hacer pasar T015**
      ↳ Traza: NFR-014 · Dep: T015 · Repo: frontend
- [ ] T032 [IMPL] Implementar el test que verifica que **la imagen espejada nunca llega al detector** (el modo espejo de FR-026 es CSS sobre el `<video>`) en `frontend/tests/contract/mirror-isolation.test.ts`
      ↳ Traza: FR-026, R-001 — espejar la entrada intercambia las dos mitades de 63 valores · Dep: T031 · Repo: frontend

### Experimento de esta fase

- [ ] T033 [EXP] Medir la escala de la componente `z` de Tasks API vs Holistic sobre un subconjunto de videos, en `ml/src/lsa_ml/experiments/z_scale_compare.py`
      - **Hipótesis**: `HandLandmarker`/`PoseLandmarker` reportan `z` en una escala comparable a la de Holistic (factor < 1.5 en desviación estándar por bloque).
      - **Métrica**: media, desviación, IQR y correlación de `z` por bloque (mano izq, mano der, pose), sobre el mismo subconjunto procesado por ambos productores. No aplica split por sujeto: es una comparación de productores, no de desempeño.
      - **Si el resultado es negativo** (escalas divergentes): se documenta como cambio de distribución de entrada y se declara **causa candidata prioritaria** de cualquier caída del baseline en T041, antes de buscar la causa en el modelo.
      - **Se documenta en**: [research.md](./research.md) R-001, sección "Componente `z`".
      ↳ Traza: R-001, NFR-014 · Dep: T020 · Repo: ml

> **Checkpoint 2 — no se pasa a la Fase 3 hasta que**:
> las cuatro etapas se ejecutan y se verifican **por separado**; T012–T015 pasan en ambos entornos;
> T029 confirma reproducibilidad de `content_hash`; T033 está documentado en `research.md`.

---

## Fase 3 — Regeneración del dataset y restauración del baseline

**Propósito**: el modelo y el baseline heredados de la fase exploratoria **están invalidados** por la
migración a Tasks API y por el cambio a aritmética entera. Esta fase produce un baseline **nuevo**,
medido con el pipeline nuevo, y lo fija como valor de referencia.

> **Regla D en vigor**: hasta que T044 se complete, el proyecto **no tiene** número de referencia
> vigente. Ninguna tarea posterior puede citar el 0.85 heredado.

### Dataset (ml)

- [ ] T034 [IMPL] Descargar y verificar LSA64 **versión cut** (3200 videos), registrar su procedencia y checksum en `ml/data/LSA64.md`, con la atribución a LIDI-UNLP y la condición de uso no comercial
      ↳ Traza: NFR-012, NFR-013, FR-030, Principio X · Dep: — · Repo: ml
- [ ] T035 [IMPL] Ejecutar S1→S4 completo sobre los 3200 videos y producir `ml/artifacts/<ds>/dataset/` con `X` de forma `(3200, 40, 201)` y `MANIFEST.json` con versión de contrato `2.0.0`
      ↳ Traza: R-001, deuda XII.1, [data-model.md §4](./data-model.md) · Dep: T029, T034 · Repo: ml
- [ ] T036 [IMPL] Generar los **fixtures reales del contrato** desde el preprocesamiento de referencia, cubriendo las 64 clases al menos una vez, en `contracts/keypoints/fixtures/raw/` y `expected/`, y congelarlos con hash en `MANIFEST.json`
      ↳ Traza: NFR-014 ("fixture que cubra las 64 clases", congelado) · Dep: T035 · Repo: raíz
- [ ] T037 [IMPL] Ejecutar el runner de equivalencia sobre los fixtures reales y dejar **G1 en verde** con cobertura de las 64 clases documentada en `artifacts/contract-report.json`
      ↳ Traza: NFR-014 Nivel 1, SC-017 · Dep: T036, T030, T031 · Repo: raíz

### Entrenamiento y baseline (ml)

- [ ] T038 [IMPL] Implementar el entrenamiento en `ml/src/lsa_ml/train/train.py` con los hiperparámetros de partida (LSTM 2 capas, hidden 128, dropout 0.3, Adam lr=3e-4, early stopping paciencia 15, `SEQ_LEN` del contrato) y seed explícita
      ↳ Traza: plan §Fase B, Principio VI · Dep: T035 · Repo: ml
- [ ] T039 [IMPL] Implementar el registro de experimento en `ml/src/lsa_ml/train/experiment.py`: `config.json`, `env.json` (versiones exactas, GPU, driver), `logs/`, y directorio `ml/artifacts/<experiment-id>/` que **nunca se sobrescribe**
      ↳ Traza: Principio VI · Dep: T038 · Repo: ml
- [ ] T040 [IMPL] Implementar la métrica con split por sujeto en `ml/src/lsa_ml/eval/metrics.py` (train 1-8, val 9, test 10) y emitir `eval/summary.json` con `protocol = "subject-holdout"`; **el split aleatorio no tiene bandera de invocación**
      ↳ Traza: NFR-001a, NFR-002, Principio V · Dep: T039 · Repo: ml
- [ ] T041 [IMPL] Entrenar y evaluar el primer modelo sobre el dataset regenerado, y registrar el resultado como **candidato a baseline** en `ml/artifacts/<exp>/eval/summary.json`
      ↳ Traza: NFR-001a · Dep: T040 · Repo: ml
- [ ] T042 [IMPL] Si el candidato queda por debajo del valor heredado de la fase exploratoria, redactar la **justificación escrita** exigida por el Principio V en `specs/001-lsa-sign-translator/baseline-justification.md`: métrica medida, motivo atribuido (con el resultado de T033 como primera hipótesis) y plan de recuperación
      ↳ Traza: Principio V, R-001 · Dep: T041 · Repo: raíz
- [ ] T043 [IMPL] Ejecutar el plan de recuperación de T042 hasta agotarlo o hasta recuperar el valor heredado, documentando cada intento en `ml/artifacts/<exp-N>/`
      ↳ Traza: Principio V · Dep: T042 · Repo: ml
- [ ] T044 [IMPL] **Fijar el baseline nuevo** como valor de referencia vigente y actualizarlo en la documentación: `Claude.md` (fase exploratoria → nota de invalidación), `specs/001-lsa-sign-translator/spec.md` NFR-001a, y `ml/artifacts/BASELINE.md` con el `experiment-id` que lo sustenta. **La constitution NO se edita en esta tarea**: si el baseline nuevo es **>= 0.85**, el Principio V queda intacto y no hay nada que tocar; si es **< 0.85**, tramitar una **enmienda del Principio V según el procedimiento de Governance** (propuesta escrita con la evidencia de T042, aprobación explícita del responsable, plan de migración y bump de versión) como paso separado y explícito — nunca como actualización documental de rutina
      ↳ Traza: regla D, Principio V, NFR-001a, constitution §Governance · Dep: T043 · Repo: raíz
      ⚠ **Bloqueada explícitamente por T042** (única excepción declarada a la regla A5): sin la justificación escrita no se fija un baseline por debajo del heredado.

### Experimentos de esta fase

- [ ] T045 [EXP] Data augmentation contra el overfitting medido en la fase exploratoria, en `ml/src/lsa_ml/train/augment.py`
      - **Hipótesis**: la augmentation (ruido gaussiano sobre coordenadas, escalado y rotación leve en el plano de imagen) reduce la brecha train/val sin bajar la accuracy de test.
      - **Métrica**: accuracy de test con **split por sujeto** (sujeto 10 held-out) y brecha `train − val`, sobre 3 seeds. Éxito: brecha reducida en >= 5 puntos sin caída de test mayor a 0.01.
      - **Si el resultado es negativo**: se documenta qué transformaciones empeoraron y por qué; no se incorpora al entrenamiento de referencia. La deuda XII.4 queda declarada como **intentada y sin resultado**, no como pendiente silenciosa.
      - **Se documenta en**: [research.md](./research.md), entrada nueva R-016.
      ↳ Traza: deuda XII.4, Principio XII · Dep: T041 · Repo: ml
- [ ] T046 [EXP] Jitter de bordes de segmento como augmentation, en `ml/src/lsa_ml/train/augment.py`
      - **Hipótesis**: desplazar los bordes de cada secuencia ±15% **antes** de S2 hace al modelo tolerante a una delimitación imperfecta, que es el error que el segmentador de la Fase 8 no podrá evitar.
      - **Métrica**: accuracy con split por sujeto sobre secuencias de test **deliberadamente mal delimitadas** (±10%, ±20%), comparada contra el modelo sin jitter. Éxito: caída bajo mala delimitación reducida a la mitad.
      - **Si el resultado es negativo**: no se incorpora; el problema se traslada al segmentador y se refleja en la categoría (c) del desglose de NFR-022 (T053).
      - **Se documenta en**: [research.md](./research.md) R-006.
      ↳ Traza: R-006, DD-002, NFR-022 · Dep: T041 · Repo: ml
- [ ] T047 [EXP] Cross-validation leave-one-subject-out sobre los 10 sujetos, en `ml/src/lsa_ml/eval/loso.py`
      - **Hipótesis**: el desempeño con sujeto 10 held-out es representativo del promedio sobre los 10 sujetos, y no un sujeto particularmente fácil o difícil.
      - **Métrica**: accuracy por sujeto held-out para los 10, media y desviación. Éxito: el sujeto 10 cae dentro de una desviación de la media.
      - **Si el resultado es negativo** (el sujeto 10 es atípico): se reporta la media LOSO **junto** al número del sujeto 10 en toda comunicación posterior, y se declara la limitación. No se cambia el protocolo de la spec sin enmienda.
      - **Se documenta en**: [research.md](./research.md), entrada nueva R-017, y en `model-card.json`.
      ↳ Traza: deuda XII.3, Principio XII, Principio V · Dep: T041 · Repo: ml
- [ ] T048 [EXP] Medir el **rango de duración de seña** con el que el reconocimiento se mantiene sobre el umbral, en `ml/src/lsa_ml/experiments/duration_range.py`
      - **Hipótesis**: existe un rango de duración (en frames de seña original) fuera del cual el muestreo a largo fijo degrada el reconocimiento de forma medible.
      - **Métrica**: accuracy con split por sujeto, estratificada por `T_original` en deciles. Éxito: identificar el rango donde la accuracy no cae más de 0.05 respecto del decil central.
      - **Si el resultado es negativo** (no hay rango estable): se documenta que la duración no es un predictor útil y el segmentador no filtra por duración, solo por los criterios de forma de R-004.
      - **Se documenta en**: [research.md](./research.md), entrada nueva R-018. **Resuelve D4.**
      ↳ Traza: spec §Assumptions (decisión diferida c), edge case "seña mucho más rápida o más lenta" · Dep: T041 · Repo: ml
- [ ] T049 [EXP] Ablación de la componente `z`: 201 vs 134 coordenadas, en `ml/src/lsa_ml/experiments/z_ablation.py`
      - **Hipótesis**: la `z` de MediaPipe, estimación de profundidad monocular que ocupa 67 de las 201 coordenadas, no aporta desempeño.
      - **Métrica**: accuracy con **split por sujeto** (train 1-8, val 9, test 10), 5 seeds idénticas en ambas configuraciones, con desglose por clase. Criterio fijado **antes** de medir: `acc(201) − acc(134) <= 0.01` → `z` no aporta.
      - **Si el resultado es negativo** (`z` sí aporta): el contrato de 201 se mantiene sin más discusión y se cierra la pregunta.
      - **Producto**: **evidencia para el procedimiento de enmienda del Principio IV**, que está marcado NO NEGOCIABLE y exige justamente evidencia empírica que contradiga la razón original. **Esta tarea NO cambia el contrato** (regla E). Bajar a 134 requiere propuesta escrita, aprobación explícita, plan de migración y actualización de templates, fuera de esta lista.
      - **Se documenta en**: [research.md](./research.md) R-002.
      ↳ Traza: R-002, Principio IV, regla E · Dep: T041 · Repo: ml

> **Checkpoint 3 — no se pasa a la Fase 4 hasta que**:
> el dataset está regenerado con Tasks API y `content_hash` registrado; G1 pasa con las 64 clases
> cubiertas por fixtures reales; existe un **baseline nuevo fijado en la documentación (T044)** con
> su `experiment-id`; si cayó respecto del heredado, la justificación escrita existe. Los `[EXP]` de
> esta fase pueden quedar abiertos: no bloquean.

---

## Fase 4 — Módulo de evaluación y diagnóstico

**Propósito**: la evaluación no se reduce a un número. Produce el diagnóstico que alimenta la
política de confianza (Fase 5) y la priorización del trabajo de modelo.

**Regla transversal**: todo lo de esta fase **se regenera en cada evaluación**, no es un análisis
puntual.

- [ ] T050 [P] [IMPL] Implementar la matriz de confusión completa 64×64 en `ml/src/lsa_ml/eval/confusion.py`, emitiendo `eval/confusion.npy` y `eval/confusion.csv` (filas = verdadero, columnas = predicho)
      ↳ Traza: spec §DD, plan §Fase B, Principio VI · Dep: T040 · Repo: ml
- [ ] T051 [P] [IMPL] Implementar precisión, recall, F1 y soporte por clase para las 64 señas en `ml/src/lsa_ml/eval/per_class.py` → `eval/per_class.json`
      ↳ Traza: plan §Fase B, R-007 · Dep: T040 · Repo: ml
- [ ] T052 [P] [IMPL] Implementar el listado de pares más confundidos en `ml/src/lsa_ml/eval/confused_pairs.py`, ordenado por `conf(i→j) + conf(j→i)`, con campo `hypothesis` a completar → `eval/confused_pairs.json`
      ↳ Traza: R-013 · Dep: T050 · Repo: ml
- [ ] T053 [P] [IMPL] Implementar la distribución de confianza de aciertos vs errores en `ml/src/lsa_ml/eval/confidence.py`, con histogramas, percentiles y **`overlap`** (solapamiento de ambas distribuciones), global y por clase → `eval/confidence.json`
      ↳ Traza: R-007 — `overlap` decide si algún umbral puede cumplir el Principio VIII · Dep: T040 · Repo: ml
- [ ] T054 [IMPL] Implementar el informe de evaluación en `ml/src/lsa_ml/report/report.py` que genera `eval/report.md` enlazando las cinco salidas, y la bandera `--full-report` que las regenera todas juntas
      ↳ Traza: [data-model.md §8.2](./data-model.md), Principio VI · Dep: T050, T051, T052, T053 · Repo: ml
- [ ] T055 [IMPL] Registrar el **piso de recall por clase** (`per_class_floor`) en el `summary.json` de la corrida de referencia, derivado de la evaluación del baseline de T044
      ↳ Traza: plan §Estrategia de pruebas — un promedio estable puede esconder que una seña pasó de 0,9 a 0,2 de recall · Dep: T051, T044 · Repo: ml
- [ ] T056 [IMPL] Implementar el test de regresión de métricas en `ml/tests/regression/test_metrics_regression.py`, comparando contra el `summary.json` de la corrida de referencia (**no** contra números escritos en el test): accuracy global y **ninguna clase** por debajo de `per_class_floor`
      ↳ Traza: Principio V, Principio XIII · Dep: T055 · Repo: ml
- [ ] T057 [IMPL] Definir el esquema de `eval/segmentation_error.json` con las cuatro categorías de NFR-022 (no detectada / falso positivo / mal delimitada / mal clasificada) y el campo `attribution`, en `ml/src/lsa_ml/eval/segmentation.py` — se **llena** en T159
      ↳ Traza: NFR-022, SC-020, [data-model.md §8.4](./data-model.md) · Dep: T054 · Repo: ml
- [ ] T058 [EXP] Clasificar la causa de cada par sistemáticamente confundido de T052 según las tres hipótesis de R-013
      - **Hipótesis**: los pares confundidos se explican por trayectoria compartida con configuración de mano distinta, por insuficiencia de los keypoints, o por desbalance de datos — y la causa determina el trabajo.
      - **Métrica**: para cada par, distancia entre centroides de clase en el subespacio de landmarks de mano vs el de pose, comparada con la varianza intra-clase, sobre el conjunto de test **por sujeto**.
      - **Si el resultado es negativo** (los keypoints no capturan la diferencia): se documenta como límite de la representación, se declara el par como confundible, y el comportamiento correcto pasa a ser el ya especificado — **ambas quedan bajo umbral** y el sistema dice "no entendí", nunca se resuelve arbitrariamente hacia una.
      - **Se documenta en**: [research.md](./research.md) R-013, y en el campo `hypothesis` de `confused_pairs.json`.
      ↳ Traza: R-013, edge case "señas visualmente similares dentro de LSA64" · Dep: T052 · Repo: ml

> **Checkpoint 4 — no se pasa a la Fase 5 hasta que**:
> `--full-report` genera las cinco salidas obligatorias sobre el modelo de referencia; el piso por
> clase está registrado; T056 pasa y **falla** si se degrada deliberadamente una clase.

---

## Fase 5 — Política de confianza

**Propósito**: umbral, comportamiento bajo umbral, reintentos y compensación del optional stopping
como **módulo propio**, no lógica dispersa en la UI ni en el modelo.

### Medición y calibración (ml)

- [ ] T059 [IMPL] Implementar `ml/src/lsa_ml/eval/stopping.py`: simular la regla de parada con `k ∈ {1,2,3}` sobre las mismas 3 segmentaciones candidatas que produce el sistema real, en una grilla de umbrales 0,50→0,95 paso 0,01, calculando FPR, cobertura y accuracy condicional a aceptar → `eval/stopping.json`
      ↳ Traza: NFR-019, R-005, SC-016 · Dep: T053 · Repo: ml
- [ ] T060 [IMPL] Implementar la separación **calibrar en sujeto 9 / reportar en sujeto 10** en `ml/src/lsa_ml/eval/stopping.py` (`--calibrate-on-subject 9 --report-on-subject 10`); calibrar y reportar sobre el mismo sujeto convertiría SC-016 en una tautología
      ↳ Traza: R-005, Principio V · Dep: T059 · Repo: ml
- [ ] T061 [IMPL] Publicar la **curva completa** de FPR y cobertura por umbral y por `k` en el informe de evaluación, no solo el número elegido — sin la curva no se puede auditar si el umbral se eligió antes o después de ver el resultado
      ↳ Traza: R-005 · Dep: T060, T054 · Repo: ml
- [ ] T062 [IMPL] **[BLOQUEADA: D5 — valores finales de umbral (NFR-019)]** Fijar los umbrales compensados de estricto / normal / permisivo tales que `FPR(θ*,3) − FPR(θ_ref,1) <= 0,02`, y escribirlos en `ml/artifacts/<exp>/model/thresholds.json`
      - **Para desbloquear**: ejecutar T060 y leer la curva. Los nominales de FR-016 (0,85 / 0,70 / 0,55) son punto de partida documentado, **no** valores finales, y ninguna tarea posterior los usa como si lo fueran.
      ↳ Traza: FR-016, NFR-019, SC-016 · Dep: T061 · Repo: ml
- [ ] T063 [IMPL] **[BLOQUEADA: D5]** Decidir y documentar **umbral global vs calibrado por clase** según el criterio de R-007, fijado antes de ver los datos, y registrar la comparación completa (no solo la opción elegida) en `research.md` R-007
      - **Para desbloquear**: `per_class.json` (T051) y `confidence.json` (T053) del modelo de referencia. Preferencia declarada: **global**; se abandona solo con evidencia.
      ↳ Traza: R-007, Principio VIII · Dep: T051, T053, T062 · Repo: ml
- [ ] T064 [IMPL] Implementar el test que verifica SC-016 en `ml/tests/regression/test_stopping_compensation.py`: la FPR con 3 intentos no supera en más de 2 puntos porcentuales a la de 1 intento, sobre el conjunto de test por sujeto
      ↳ Traza: SC-016, NFR-019 · Dep: T062 · Repo: ml

### Módulo en el cliente (frontend)

- [ ] T065 [IMPL] [US1] Implementar el módulo de política de confianza en `frontend/src/confidence/policy.ts`: recibe hasta 3 predicciones de un evento, aplica el umbral vigente y decide `RECONOCIDA | NO_ENTENDIDA`; **no conoce la UI y no conoce el modelo**
      ↳ Traza: FR-016, FR-017, AD-04, NFR-015, Principio XI · Dep: T062 · Repo: frontend
- [ ] T066 [IMPL] [US1] Implementar la **invariante de privacidad de resultado** en `frontend/src/confidence/policy.ts`: ninguna etiqueta de un evento `NO_ENTENDIDA` sale del módulo — ni a la vista, ni al historial, ni a la voz, ni al registro local
      ↳ Traza: FR-017, SC-005, [data-model.md §1.3](./data-model.md) · Dep: T065 · Repo: frontend
- [ ] T067 [IMPL] [US1] Implementar la regla de parada en `frontend/src/confidence/attempts.ts`: se detiene en el primer intento sobre umbral; agotados los 3 comunica "no entendí"; **jamás ejecuta un cuarto**
      ↳ Traza: FR-009, SC-012 · Dep: T065 · Repo: frontend
- [ ] T068 [IMPL] [US7] Implementar los tres niveles nombrados (estricto / normal / permisivo) leyendo `thresholds.json` del artefacto de modelo, en `frontend/src/confidence/levels.ts` — con umbral por clase, cada nivel es un **desplazamiento global** sobre el vector, nunca 192 números expuestos
      ↳ Traza: FR-016, FR-025, R-007 · Dep: T063, T065 · Repo: frontend
- [ ] T069 [IMPL] Escribir los tests de la puerta **G2** en `frontend/tests/confidence/`: por encima y por debajo del umbral en los tres niveles; los 3 intentos; que ninguna etiqueta candidata se filtre a vista, historial ni voz
      ↳ Traza: Principio VIII, Principio XIII, SC-005, SC-012 · Dep: T066, T067, T068 · Repo: frontend
- [ ] T070 [IMPL] Cablear la puerta **G2** como job bloqueante de CI en `.github/workflows/ci.yml`
      ↳ Traza: constitution §Puertas de CI · Dep: T069 · Repo: raíz

> **Checkpoint 5 — no se pasa a la Fase 6 hasta que**:
> la curva de optional stopping está publicada; los umbrales compensados están fijados en
> `thresholds.json` con la decisión global/por-clase documentada; G2 está en CI y pasa.

---

## Fase 6 — Inferencia en dispositivo

**Propósito**: el modelo que corre en el navegador debe ser el modelo medido. **NFR-001a aplica al
que realmente corre.**

- [ ] T071 [IMPL] Implementar la exportación PyTorch → ONNX en `ml/src/lsa_ml/export/to_onnx.py`, **sin** destilación, poda ni cuantización
      ↳ Traza: R-003, NFR-001a · Dep: T044 · Repo: ml
- [ ] T072 [IMPL] Implementar la verificación de **paridad numérica** en `ml/src/lsa_ml/export/verify.py`: logits ONNX vs PyTorch sobre los fixtures, error absoluto máximo <= 1e-4
      ↳ Traza: R-003 · Dep: T071 · Repo: ml
- [ ] T073 [IMPL] Implementar la verificación de **paridad de métrica**: accuracy del modelo exportado sobre el sujeto 10, con diferencia <= 0.005 respecto de PyTorch; si la excede, **el export está roto y el trabajo se detiene ahí**
      ↳ Traza: R-003, NFR-001a, plan §Riesgo 6 · Dep: T072 · Repo: ml
- [ ] T074 [IMPL] **Re-medir NFR-001a sobre el artefacto exportado** y registrar ese número —no el del checkpoint— como la métrica reportada en `model-card.json`
      ↳ Traza: NFR-001a, regla D, plan §Riesgo 6 · Dep: T073 · Repo: ml
- [ ] T075 [IMPL] Generar el artefacto de modelo completo en `ml/artifacts/<exp>/model/`: `model.onnx`, `model.pt`, `labels.json`, `thresholds.json` y `model-card.json` con todos los metadatos obligatorios de [data-model.md §7](./data-model.md)
      ↳ Traza: Principio VI, [data-model.md §7](./data-model.md) · Dep: T074, T062 · Repo: ml
- [ ] T076 [IMPL] Implementar el rechazo de artefactos con `metrics.per_subject.protocol != "subject-holdout"` y con `delta < 0` sin `justification`, en `ml/src/lsa_ml/export/validate_card.py`
      ↳ Traza: NFR-002, Principio V · Dep: T075 · Repo: ml
- [ ] T077 [IMPL] [US1] Implementar el **puerto** `Classifier` en `frontend/src/classify/port.ts` según [contracts/inference-ws.md](./contracts/inference-ws.md) §1: acepta un **lote**, devuelve top-k con confianza, **no conoce el umbral y no decide nada**
      ↳ Traza: AD-05, NFR-015, Principio XI · Dep: T003 · Repo: frontend
- [ ] T078 [IMPL] [US1] Implementar el adaptador `OnnxRuntimeWebClassifier` en `frontend/src/classify/onnx-adapter.ts` (WASM + SIMD + hilos), clasificando las 3 candidatas en **un solo lote**
      ↳ Traza: R-003, R-008 · Dep: T077, T075 · Repo: frontend
- [ ] T079 [IMPL] Implementar la validación de `kp_contract_version` al cargar el modelo en `frontend/src/classify/load.ts`: si no coincide con la que implementa el cliente, **falla de forma ruidosa** en vez de clasificar
      ↳ Traza: [data-model.md §7](./data-model.md), Principio IV · Dep: T078 · Repo: frontend
- [ ] T080 [IMPL] Implementar la carga conjunta de `model.onnx`, `labels.json` y `thresholds.json` **del mismo artefacto**, con rechazo si provienen de corridas distintas, en `frontend/src/classify/load.ts`
      ↳ Traza: [data-model.md §1.5](./data-model.md) — un modelo nuevo con etiquetas viejas traduce todo mal sin error visible · Dep: T079 · Repo: frontend
- [ ] T081 [IMPL] Implementar el rechazo de toda entrada cuya forma no sea `(SEQ_LEN, 201)` en el puerto, con test en `frontend/tests/classify/shape-guard.test.ts`
      ↳ Traza: [contracts/inference-ws.md](./contracts/inference-ws.md) §1 · Dep: T077 · Repo: frontend

> **Checkpoint 6 — no se pasa a la Fase 7 hasta que**:
> el modelo exportado pasa paridad numérica y de métrica; NFR-001a está re-medido **sobre el ONNX**;
> el artefacto completo existe con `model-card.json` validado; el puerto `Classifier` carga el
> artefacto y rechaza formas inválidas y versiones de contrato distintas.

---

## Fase 7 — Arnés de dev/eval (`backend/devinfer`)

**Propósito**: servicio de inferencia por WebSocket **exclusivamente para desarrollo y evaluación**.
Transporta keypoints por red, lo que en producción está prohibido (FR-002, NFR-006, SC-004).

> **Regla F en vigor**: la barrera anti-producción es parte de esta fase, no una tarea posterior.

- [ ] T082 [IMPL] Implementar el servidor WebSocket en `backend/devinfer/src/server.py` con el endpoint `/ws/infer` y los mensajes `hello` / `ready` / `classify` / `prediction` / `error` / `bye` de [contracts/inference-ws.md](./contracts/inference-ws.md) §2
      ↳ Traza: [contracts/inference-ws.md](./contracts/inference-ws.md), NFR-014 Nivel 2 · Dep: T075 · Repo: backend
- [ ] T083 [IMPL] Implementar la validación de mensajes en `backend/devinfer/src/schema.py`: forma exacta `(SEQ_LEN, 201)` (`bad_shape`), máximo 3 candidatas (`too_many_candidates`), y `contract_mismatch` que **cierra la conexión** — clasificar con contratos distintos no admite degradación
      ↳ Traza: [contracts/inference-ws.md](./contracts/inference-ws.md) §2.2, Principio IV · Dep: T082 · Repo: backend
- [ ] T084 [IMPL] Implementar la regla de que el servidor devuelve **las tres** predicciones sin aplicar umbral ni decidir cuál gana, en `backend/devinfer/src/infer.py` — un servidor que aplicara el umbral rompería la separación que hace verificable SC-005 y SC-016
      ↳ Traza: AD-04, SC-005 · Dep: T082 · Repo: backend
- [ ] T085 [IMPL] **Barrera anti-producción (1/3)**: mantener `backend/devinfer` como paquete independiente, **sin** que `frontend/package.json` lo declare como dependencia en ninguna forma
      ↳ Traza: regla F, SC-004, SC-013 · Dep: T005 · Repo: raíz
- [ ] T086 [IMPL] **Barrera anti-producción (2/3)**: alojar el adaptador `WebSocketClassifier` en `frontend/src/dev/ws-classifier.ts`, dentro de un directorio `dev/` excluido del bundle de producción por configuración de Vite en `frontend/vite.config.ts`
      ↳ Traza: regla F, AD-05, SC-004 · Dep: T077, T085 · Repo: frontend
- [ ] T087 [IMPL] **Barrera anti-producción (3/3)**: implementar el guard de build en `frontend/scripts/check-prod-bundle.ts` que falla si el artefacto de producción contiene cualquier referencia a `ws-classifier`, a `/ws/infer` o al directorio `dev/`
      ↳ Traza: regla F, SC-013 — la propiedad no puede depender de una bandera de configuración · Dep: T086 · Repo: frontend
- [ ] T088 [IMPL] Escribir la **verificación de que el build de producción no incluye ni alcanza por red** el arnés, en `frontend/tests/e2e/no-devinfer.spec.ts`: inspección del bundle distribuido **y** captura del tráfico saliente durante una sesión completa (cero conexiones WebSocket)
      ↳ Traza: regla F, SC-004, SC-013, NFR-006 · Dep: T087 · Repo: frontend
- [ ] T089 [P] [IMPL] Escribir los tests del canal en `backend/devinfer/tests/test_protocol.py`: formato de mensajes contra el schema, rechazo de formas inválidas, códigos de error y sus severidades
      ↳ Traza: [contracts/inference-ws.md](./contracts/inference-ws.md) §2.2 · Dep: T083 · Repo: backend
- [ ] T090 [P] [IMPL] Escribir los tests de resiliencia en `frontend/tests/dev/ws-resilience.test.ts`: reconexión con backoff (250 ms→4 s, máx 5), **pérdida de conexión durante un reconocimiento → se resuelve como no-reconocido** (nunca queda esperando, nunca inventa un resultado), timeout por petición, y que el buffer no se retransmite
      ↳ Traza: [contracts/inference-ws.md](./contracts/inference-ws.md) §2.3, Principio VIII · Dep: T086 · Repo: frontend
- [ ] T091 [IMPL] Medir la **cota empírica del Nivel 2** del contrato procesando el mismo video por el camino Python y por el del navegador a través del arnés, y registrarla en `research.md` R-014 — se **mide**, nunca se asume
      ↳ Traza: NFR-014 Nivel 2, D3 · Dep: T082, T031 · Repo: raíz
- [ ] T092 [IMPL] Verificar que el canal **no transporta** frames, identificadores de persona ni preferencias, con test en `backend/devinfer/tests/test_no_video.py`
      ↳ Traza: Principio VII, NFR-007, [contracts/inference-ws.md](./contracts/inference-ws.md) §2.4 · Dep: T089 · Repo: backend

> **Checkpoint 7 — no se pasa a la Fase 8 hasta que**:
> el arnés clasifica correctamente por WebSocket; T088 confirma que el build de producción **no lo
> incluye ni lo alcanza por red**; la cota de Nivel 2 está medida y documentada.

---

## Fase 8 — Cliente web: captura, keypoints y segmentación

**Propósito**: US1 y US3 en su parte de entrada. **Es el mayor riesgo del proyecto** (DD-002): si la
segmentación no funciona, no funciona nada.

### Captura (frontend)

- [ ] T093 [IMPL] [US1] Implementar el acceso a cámara y el manejo de permisos en `frontend/src/capture/camera.ts`, con mensajes explícitos para permiso denegado, cámara ocupada y cámara no disponible — **nunca una pantalla en negro sin explicación**
      ↳ Traza: FR-021, FR-023, edge cases §Entorno y dispositivo · Dep: T003 · Repo: frontend
- [ ] T094 [IMPL] [US1] Implementar el control de grabación en `frontend/src/capture/recording.ts`: accesible en todo momento, **una sola acción por conversación**, y detener **apaga la cámara** (no solo suspende el reconocimiento); accionar dos veces nunca deja el estado ambiguo
      ↳ Traza: FR-001, FR-007, DD-002, edge case "grabación iniciada dos veces" · Dep: T093 · Repo: frontend
- [ ] T095 [IMPL] [US7] Implementar el modo espejo como transformación **CSS sobre el `<video>`** en `frontend/src/capture/mirror.ts`, activado por defecto, sin que llegue al detector
      ↳ Traza: FR-026, R-001, T032 · Dep: T093 · Repo: frontend
- [ ] T096 [IMPL] [US1] Implementar la selección de persona (mayor área de torso al iniciar, mantenida durante toda la sesión, sin alternar; si dos áreas son equivalentes **no inicia** y lo comunica) en `frontend/src/capture/tracking.ts`
      ↳ Traza: edge case "más de una persona en cuadro" · Dep: T093 · Repo: frontend
- [ ] T097 [IMPL] [US1] Implementar el monitor de fps efectivos y el aviso de rendimiento insuficiente en `frontend/src/capture/perf.ts` — el sistema **advierte la degradación** en lugar de producir reconocimientos poco fiables en silencio
      ↳ Traza: FR-023, NFR-004 (>= 15 fps), edge case "dispositivo que no sostiene la tasa de cuadros" · Dep: T093 · Repo: frontend

### Keypoints (frontend)

- [ ] T098 [IMPL] [US1] Implementar la extracción de keypoints con MediaPipe Tasks for Web (`HandLandmarker` + `PoseLandmarker`) en `frontend/src/keypoints/extract.ts`, con la **misma** asignación de handedness y política de relleno que S1
      ↳ Traza: FR-002, R-001, [contracts/pipeline-stages.md](./contracts/pipeline-stages.md) §Correspondencia · Dep: T032, T095 · Repo: frontend
- [ ] T099 [IMPL] [US1] Garantizar timestamps estrictamente crecientes en `detectForVideo` en `frontend/src/keypoints/extract.ts` — nunca llamarlo dos veces con el mismo `ts` (bug ya sufrido, `Claude.md`)
      ↳ Traza: [contracts/pipeline-stages.md](./contracts/pipeline-stages.md) §S1 · Dep: T098 · Repo: frontend
- [ ] T100 [IMPL] [US1] Implementar el **buffer circular** de keypoints en `frontend/src/keypoints/buffer.ts`: duración acotada, sobrescritura continua, vaciado al detener la grabación, **nunca serializado a disco**
      ↳ Traza: NFR-006, NFR-007, [data-model.md §5](./data-model.md) · Dep: T098 · Repo: frontend

### Segmentación (frontend) — DD-002

- [ ] T101 [IMPL] [US1] Implementar el detector de actividad de señado en `frontend/src/segment/activity.ts`: señal `a(t)` a partir de velocidad de muñecas y codos **normalizada por la distancia entre hombros**, con histéresis `θ_on`/`θ_off`
      ↳ Traza: FR-008, R-004, DD-002, deuda XII.2 · Dep: T100 · Repo: frontend
- [ ] T102 [IMPL] [US1] Implementar el silencio de confirmación de fin `T_off`, la duración mínima y máxima de seña, y el período refractario tras un reconocimiento, en `frontend/src/segment/boundaries.ts` — **todos como parámetros configurables**, ninguno escrito en el código
      ↳ Traza: FR-008, FR-036, R-004, R-008 · Dep: T101 · Repo: frontend
- [ ] T103 [IMPL] [US1] Implementar el **criterio de forma** del rechazo de no-seña en `frontend/src/segment/reject.ts`: manos detectadas durante una fracción mínima del tramo y torso presente; si no, el evento se descarta con aviso de encuadre y **no se clasifica**
      ↳ Traza: FR-034, SC-011, edge cases "manos fuera de cuadro a mitad de la seña" y "seña bimanual con una mano ocluida" · Dep: T102 · Repo: frontend
- [ ] T104 [IMPL] [US1] Implementar la generación de las **3 segmentaciones candidatas** (`[i,f]`, `[i+δ,f]`, `[i,f−δ]` con δ proporcional a la duración) en `frontend/src/segment/candidates.ts`, entregadas al clasificador **en un solo lote**
      ↳ Traza: FR-009, R-004, R-008 · Dep: T102 · Repo: frontend
- [ ] T105 [IMPL] [US1] Cablear la cadena completa captura → keypoints → S2 → S3 → clasificador → política de confianza en `frontend/src/pipeline.ts`, usando **exactamente** los módulos de `frontend/src/contract/`
      ↳ Traza: Principio IV, Principio XI, NFR-014 · Dep: T104, T078, T065, T030, T031 · Repo: frontend
- [ ] T106 [IMPL] **[BLOQUEADA: D4 — rango de duración de seña]** Fijar los valores de duración mínima y máxima del segmentador en `frontend/src/segment/params.json`
      - **Para desbloquear**: resultado de T048. Si T048 concluye que no hay rango estable, el segmentador **no filtra por duración** y solo aplica los criterios de forma de T103.
      ↳ Traza: R-004, D4 · Dep: T048, T102 · Repo: frontend
- [ ] T107 [IMPL] Calibrar `θ_on`, `θ_off` y `T_off` offline contra la anotación humana de referencia, minimizando las categorías (a) y (c) de NFR-022 con la restricción `T_off <= 800 ms`, y registrar los valores en `frontend/src/segment/params.json` y en `research.md` R-004
      ↳ Traza: R-004, R-008, NFR-022 · Dep: T057, T102 · Repo: frontend
- [ ] T108 [IMPL] [US1] Implementar los cuatro estados mutuamente excluyentes de FR-010 (detenida / grabando y detectando / grabando sin detectar / procesando) en `frontend/src/capture/state.ts`, cada uno con indicador visual **distinto en color y en forma**
      ↳ Traza: FR-010, [data-model.md §1.1](./data-model.md) · Dep: T094, T101 · Repo: frontend
- [ ] T109 [IMPL] [US1] Implementar el **modo de respaldo manual** (FR-037) en `frontend/src/capture/manual-mode.ts`: una acción humana delimita el inicio de una captura individual, el sistema detecta el fin, **mismo pipeline**, activable desde preferencias sin recargar, dejando ambas manos libres
      ↳ Traza: FR-037, FR-007, DD-002 — construido desde el inicio, no agregado al final · Dep: T105 · Repo: frontend
- [ ] T110 [P] [IMPL] Escribir el test de SC-018 en `frontend/tests/e2e/continuous-session.spec.ts`: 20 señas separadas por pausas naturales producen **exactamente 20 eventos**, cero fusiones, cero duplicados
      ↳ Traza: SC-018, FR-036, US1 Independent Test · Dep: T105 · Repo: frontend
- [ ] T111 [P] [IMPL] Escribir el test de SC-019 en `frontend/tests/e2e/no-sign-session.spec.ts`: sesión de 3 minutos sin señar (conversar, acomodarse el pelo, gesticular, desplazarse) produce **cero** traducciones
      ↳ Traza: SC-019, FR-034 · Dep: T105 · Repo: frontend

> **Checkpoint 8 — no se pasa a la Fase 9 hasta que**:
> US1 es operativa extremo a extremo sobre stream continuo; T110 y T111 pasan; los parámetros del
> segmentador están calibrados y registrados. **Aquí se dispara la puerta NFR-022 (T159), que se
> ejecuta ahora y no al final del proyecto.**

---

## Fase 9 — Cliente web: presentación, texto y voz

**Propósito**: US1 (salida), US2, US3, US4, US5, US6, US7, US8. Texto y voz son **dos canales de la
misma salida**: ambos consumen el mismo resultado de la política de confianza.

### Texto y confianza (frontend)

- [ ] T112 [IMPL] [US1] Implementar la presentación del texto en `frontend/src/presentation/text/Result.tsx`, dimensionado por **ángulo visual** >= 0,4° (2 m para la persona señante, 40 cm para el interlocutor) con contraste >= 4,5:1
      ↳ Traza: FR-011, NFR-011, SC-007 · Dep: T065 · Repo: frontend
- [ ] T113 [IMPL] [US1] Implementar las 3 categorías nombradas de confianza (alta / media / no entendí) con indicador visual en `frontend/src/presentation/text/Confidence.tsx`; **MUST NOT** presentarse únicamente como número, y en "no entendí" **sin valor numérico y sin etiqueta candidata**
      ↳ Traza: FR-013, FR-017, Principio VIII · Dep: T066, T112 · Repo: frontend
- [ ] T114 [IMPL] [US5] Implementar el historial de sesión en memoria en `frontend/src/session/history.ts`: orden cronológico, solo entradas presentadas, **no se restaura al reabrir**
      ↳ Traza: FR-014, US5 esc. 3, [data-model.md §6.1](./data-model.md) · Dep: T113 · Repo: frontend
- [ ] T115 [IMPL] [US5] Implementar la limpieza del historial con acción explícita **y confirmación** en `frontend/src/session/history.ts`
      ↳ Traza: FR-015 · Dep: T114 · Repo: frontend
- [ ] T116 [IMPL] [US4] Implementar el descarte de un reconocimiento en `frontend/src/session/discard.ts`: lo retira de la vista y del historial y **detiene la reproducción por voz si está en curso**
      ↳ Traza: FR-019, US4 esc. 1 · Dep: T114 · Repo: frontend
- [ ] T117 [IMPL] [US4] Implementar el registro local de descartes en `localStorage["helpi.discards.v1"]` (etiqueta presentada, confianza, momento) en `frontend/src/session/discard-log.ts` — **solo señas que fueron presentadas**, para que el registro no sea la puerta trasera de FR-017
      ↳ Traza: FR-020, SC-008, [data-model.md §6.2](./data-model.md) · Dep: T116 · Repo: frontend
- [ ] T118 [IMPL] [US4] Implementar el borrado conjunto de historial, registro de descartes y buffer de keypoints con **una sola acción** en `frontend/src/session/clear.ts`; las preferencias no se borran con ella
      ↳ Traza: FR-020, NFR-008, US4 esc. 4 · Dep: T117 · Repo: frontend

### Avisos de encuadre y condiciones (frontend)

- [ ] T119 [IMPL] [US3] Implementar la detección de condiciones de entorno en `frontend/src/presentation/text/Conditions.tsx` con los 12 tipos de [data-model.md §1.7](./data-model.md), cada uno con **mensaje que nombra la causa y una acción concreta ejecutable**
      ↳ Traza: FR-006, FR-023, FR-035, SC-009 · Dep: T097, T103 · Repo: frontend
- [ ] T120 [IMPL] [US3] Implementar el indicador persistente de "te estoy viendo" y el aviso de detección perdida en `frontend/src/presentation/text/Detection.tsx`, en menos de 1 segundo
      ↳ Traza: FR-005, SC-009, US3 esc. 1–2 · Dep: T119 · Repo: frontend
- [ ] T121 [IMPL] [US3] Implementar la guía de distancia **sin prescribir un número**: `DEMASIADO_CERCA` / `DEMASIADO_LEJOS` indican dirección de corrección, en `frontend/src/presentation/text/Distance.tsx`
      ↳ Traza: FR-035, NFR-004 ("la distancia se registra, no se impone") · Dep: T119 · Repo: frontend
- [ ] T122 [IMPL] Revisar el **catálogo completo de mensajes** en `frontend/src/presentation/messages.ts` y verificar que cada uno nombra causa y acción, con test en `frontend/tests/presentation/message-catalog.test.ts`
      ↳ Traza: FR-023 (verificable por revisión del catálogo), SC-009 · Dep: T119 · Repo: frontend
- [ ] T123 [IMPL] [US8] Implementar la señal audible dirigida al interlocutor en `frontend/src/presentation/audio-cue.ts`, que le permite saber sin ver la pantalla si el sistema detecta a la persona señante — **ningún estado queda cubierto solo por sonido**
      ↳ Traza: FR-032, NFR-010, US8 esc. 1–2 · Dep: T120 · Repo: frontend

### Voz (frontend)

- [ ] T124 [IMPL] [US2] Implementar el **puerto** `SpeechPort` en `frontend/src/presentation/voice/port.ts` (`speak` / `cancel` / `listVoices`), con la política de cola, la degradación y el comportamiento bajo umbral **en el puerto**, no en el adaptador
      ↳ Traza: AD-06, R-010, FR-012 · Dep: T113 · Repo: frontend
- [ ] T125 [IMPL] [US2] Implementar `WebSpeechAdapter` en `frontend/src/presentation/voice/web-speech.ts` con la cadena de FR-012 (`es-AR` → `es-UY` → cualquier `es-*` → solo texto), escuchando `voiceschanged` porque `getVoices()` puede devolver lista vacía en la primera llamada
      ↳ Traza: FR-012, R-010, US2 esc. 5 · Dep: T124 · Repo: frontend
- [ ] T126 [IMPL] [US2] Implementar el camino único para "sin voz en español" y "sin `speechSynthesis`" en `frontend/src/presentation/voice/degrade.ts`: mismo mensaje, mismo estado, sin excepción no capturada, **continuando solo con texto sin bloquear el flujo**
      ↳ Traza: US2 esc. 5, edge case "voz TTS no disponible" · Dep: T125 · Repo: frontend
- [ ] T127 [IMPL] [US2] Implementar la **agrupación por pausa** en `frontend/src/presentation/voice/blocks.ts`: el texto se muestra por seña al reconocerse; la voz espera la pausa y entrega el bloque, con máximo de 5 señas
      ↳ Traza: FR-038, DD-003 · Dep: T124 · Repo: frontend
- [ ] T128 [IMPL] [US2] Implementar la política de **no solapamiento** en `frontend/src/presentation/voice/queue.ts`: un bloque nuevo durante una locución se encola (no se solapa ni se descarta); dos pendientes se fusionan hasta 5 glosas; el descarte cancela la locución en curso; detener la grabación vacía la cola
      ↳ Traza: FR-038, FR-019, R-010 · Dep: T127, T116 · Repo: frontend
- [ ] T129 [IMPL] [US2] Implementar el comportamiento bajo umbral en la voz: **no se pronuncia nada** —ni la etiqueta, ni "no entendí"—, en `frontend/src/presentation/voice/port.ts`
      ↳ Traza: FR-017, R-010 §Comportamiento bajo umbral, Principio VIII · Dep: T124, T066 · Repo: frontend
- [ ] T130 [P] [IMPL] Escribir los tests de degradación de TTS en `frontend/tests/voice/degradation.test.ts`: sin `es-AR`, sin ninguna voz en español, sin `speechSynthesis`, bloques consecutivos sin solaparse, descarte durante la locución, bajo umbral sin pronunciar
      ↳ Traza: FR-012, FR-038, US2 esc. 5, R-010 · Dep: T126, T128, T129 · Repo: frontend

### Preferencias y encuadre de expectativas (frontend)

- [ ] T131 [IMPL] [US7] Implementar las preferencias persistentes en `frontend/src/prefs/store.ts` según [data-model.md §5](./data-model.md), con `pulido_activado` en `false` y `aviso_pulido_visto` en `false` por defecto, y revalidación de la voz guardada al arrancar
      ↳ Traza: FR-024, FR-025, FR-026, FR-027, FR-041, SC-023 · Dep: T125, T068 · Repo: frontend
- [ ] T132 [IMPL] [US7] Implementar la pantalla de preferencias en `frontend/src/prefs/Preferences.tsx`: voz on/off y selección, umbral en 3 opciones nombradas **con una explicación de una frase cada una**, modo espejo, modo de captura
      ↳ Traza: FR-024, FR-025, FR-026, FR-037 · Dep: T131 · Repo: frontend
- [ ] T133 [P] [IMPL] [US6] Implementar la lista completa de las 64 señas con su significado en español en `frontend/src/vocabulary/VocabularyList.tsx`, leyendo `labels.json` del artefacto de modelo
      ↳ Traza: FR-028, US6 esc. 1, SC-010 · Dep: T080 · Repo: frontend
- [ ] T134 [P] [IMPL] [US6] Implementar el aviso **visible y persistente** de vocabulario limitado y de "asistencia, no reemplazo de intérpretes humanos" en `frontend/src/layout/ScopeNotice.tsx`, presente en cualquier pantalla
      ↳ Traza: FR-029, Principio VIII, constitution §preámbulo, SC-010 · Dep: T003 · Repo: frontend
- [ ] T135 [P] [IMPL] [US6] Implementar la atribución a LSA64 (autores LIDI-UNLP, paper/sitio) y su condición de uso **no comercial** en `frontend/src/about/About.tsx`
      ↳ Traza: FR-030, NFR-012, Principio X, US6 esc. 3 · Dep: T003 · Repo: frontend
- [ ] T136 [IMPL] Agregar el aviso de herramienta de **asistencia** y la atribución a LSA64 en el `Readme.md` de la raíz del repositorio — pendiente marcado en el Sync Impact Report de la constitution
      ↳ Traza: Principios VIII y X, constitution §Sync Impact Report · Dep: — · Repo: raíz

> **Checkpoint 9 — no se pasa a la Fase 10 hasta que**:
> US1 entrega texto con confianza categórica; US2 pronuncia bloques agrupados por pausa con toda la
> cadena de degradación; US3–US8 tienen su superficie implementada; T130 y T122 pasan.

---

## Fase 10 — Servicio de pulido (`backend/polish`)

**Propósito**: convertir la secuencia de glosas en frase natural. Es el **único componente remoto** y
solo recibe glosas. **No reconoce, no clasifica y no infiere señas.**

- [ ] T137 [IMPL] Implementar el endpoint `POST /v1/polish` en `backend/polish/src/api/routes.py` con el schema **estricto** de [contracts/polish-service.md](./contracts/polish-service.md) §2.1: solo `v` y `glosses`, **ningún** campo de sesión, persona o dispositivo
      ↳ Traza: DD-005, FR-039(a), NFR-024 — un campo que no existe no puede filtrarse por descuido · Dep: T004 · Repo: backend
- [ ] T138 [IMPL] Implementar la validación contra el **vocabulario cerrado** en `backend/polish/src/validate/vocabulary.py`, rechazando toda petición cuyo contenido no sea una secuencia de glosas de LSA64
      ↳ Traza: NFR-026, SC-025 — vuelve el endpoint inútil como LLM de propósito general · Dep: T137 · Repo: backend
- [ ] T139 [IMPL] Crear la **tabla curada de lemas y flexiones** de las 64 glosas y la lista blanca de palabras funcionales en `backend/polish/src/validate/lexicon.json`, versionada con el vocabulario
      ↳ Traza: FR-040, DD-004, AD-07, [data-model.md §1.5](./data-model.md) · Dep: T138 · Repo: backend
- [ ] T140 [IMPL] Implementar el validador de trazabilidad por lema en `backend/polish/src/validate/traceability.py`: todo token debe estar en la lista blanca o ser flexión de una glosa de entrada; **una sola** palabra de contenido no trazable descarta la frase completa
      ↳ Traza: FR-040, DD-004, SC-021 · Dep: T139 · Repo: backend
- [ ] T141 [IMPL] Implementar el límite de uso por origen (30/min, provisional), el tamaño máximo de 5 glosas con rechazo **sin procesar**, y los códigos de error de [contracts/polish-service.md](./contracts/polish-service.md) §2.3 en `backend/polish/src/api/limits.py`
      ↳ Traza: NFR-026, FR-038 · Dep: T137 · Repo: backend
- [ ] T142 [IMPL] Implementar la **ausencia total de persistencia** en `backend/polish/src/`: ni glosas, ni respuestas, ni IP asociada a contenido; cuerpos de error **sin eco del contenido recibido**
      ↳ Traza: NFR-024 — verificable por inspección del código del servicio · Dep: T137 · Repo: backend
- [ ] T143 [IMPL] Implementar las métricas **agregadas sin contenido** en `backend/polish/src/metrics/`: peticiones, distribución de latencia, tasa de error, tasa de rechazo por límite y por vocabulario, disponibilidad; **sin registros por petición** que puedan reconstruir contenido cruzándolos
      ↳ Traza: NFR-027 · Dep: T142 · Repo: backend
- [ ] T144 [IMPL] **[BLOQUEADA: D2 — runtime, modelo y despliegue del servicio de pulido (R-011)]** Implementar el adaptador del modelo en `backend/polish/src/llm/adapter.py`
      - **Para desbloquear**: decidir dónde corre y con qué modelo. La spec **descarta** delegarlo a una API comercial de terceros. Evaluar antes la alternativa registrada en R-011: la tarea (insertar palabras funcionales y conjugar, sobre <= 5 glosas de un vocabulario de 64) es mucho más chica que lo que un modelo de 7–8B está dimensionado para hacer.
      ↳ Traza: DD-003, R-011, spec §Out of Scope · Dep: T140 · Repo: backend
- [ ] T145 [IMPL] **[BLOQUEADA: D2]** Desplegar el servicio y medir NFR-023 (< 3 s entre pausa e inicio de la frase hablada) sobre **4G urbano** con RTT 50–150 ms registrado
      - **Para desbloquear**: T144. **Criterio de revisión**: si no se alcanzan los 3 s, decidir explícitamente entre un modelo más chico, pronunciar siempre la glosa cruda, o subir el presupuesto con justificación en `research.md` bajo el Principio IX.
      ↳ Traza: NFR-023, R-011, R-012 · Dep: T144 · Repo: backend
- [ ] T146 [P] [IMPL] Escribir los tests del servicio en `backend/polish/tests/`: SC-021 (50 secuencias, cero palabras de contenido no trazables), SC-025 (100% de rechazo de peticiones adversarias con texto libre), límite de uso, tamaño máximo, ausencia de persistencia
      ↳ Traza: SC-021, SC-025, NFR-024, NFR-026 · Dep: T140, T141, T142 · Repo: backend

### Cliente del pulido (frontend)

- [ ] T147 [IMPL] [US2] Implementar el cliente del servicio en `frontend/src/polish/client.ts`, enviando **únicamente** la secuencia de glosas que superaron el umbral, sin ningún otro dato de la sesión
      ↳ Traza: FR-039(a)(b)(d), DD-005 · Dep: T127, T137 · Repo: frontend
- [ ] T148 [IMPL] [US2] Implementar la **doble validación** de FR-040 en `frontend/src/polish/verify.ts`: el cliente aplica la misma tabla de lemas antes de pronunciar y descarta la frase si contiene una palabra de contenido no trazable
      ↳ Traza: FR-040, AD-07 — el cliente no puede confiar en que el servicio esté sano · Dep: T147, T139 · Repo: frontend
- [ ] T149 [IMPL] [US2] Implementar la **degradación unificada** en `frontend/src/polish/degrade.ts`: sin red, servicio caído, límite excedido, presupuesto agotado, frase rechazada o pulido desactivado → se pronuncia la **glosa cruda**, se avisa que la frase no pudo componerse, **sin reintentos en bucle** y sin exponer el detalle técnico
      ↳ Traza: FR-031, SC-026, [contracts/polish-service.md](./contracts/polish-service.md) §5.2 · Dep: T147 · Repo: frontend
- [ ] T150 [IMPL] [US2] Implementar la presentación de la **glosa cruda junto a la frase pulida** en `frontend/src/presentation/text/PolishedPhrase.tsx`, en el 100% de las presentaciones
      ↳ Traza: FR-039(c), SC-022, US2 esc. 3 · Dep: T148 · Repo: frontend
- [ ] T151 [IMPL] Implementar el **aviso previo a la primera salida de glosas** en `frontend/src/polish/FirstUseNotice.tsx`, en lenguaje directo y en la interfaz —no en una política que nadie lee—, con `pulido_activado` bloqueado hasta que `aviso_pulido_visto` sea `true`
      ↳ Traza: FR-041, SC-023, DD-005 · Dep: T131, T147 · Repo: frontend
- [ ] T152 [IMPL] [US7] Implementar el interruptor de pulido en preferencias, con el sistema **completo** funcionando desactivado y sin penalización distinta de la calidad de la frase hablada
      ↳ Traza: NFR-025, SC-024 · Dep: T151, T132 · Repo: frontend

> **Checkpoint 10 — no se pasa a la Fase 11 hasta que**:
> el servicio rechaza el 100% de peticiones adversarias y no persiste nada; la doble validación de
> FR-040 funciona en ambos lados; la degradación a glosa cruda cubre las seis situaciones sin
> bloqueos ni reintentos en bucle; T151 impide que salga una glosa antes del aviso.

---

## Fase 11 — Robustez y validación con personas usuarias

**Propósito**: medir el sistema en condiciones reales y validar la accesibilidad **con usuarias
reales de LSA**, no por inspección interna.

### Build de evaluación (frontend)

- [ ] T153 [IMPL] Implementar el **build de evaluación separado** en `frontend/src/eval/` y `frontend/vite.config.eval.ts`, con consentimiento explícito e informado antes de cada sesión, declaración visible y permanente de que está registrando, y exportación **solo de métricas**
      ↳ Traza: NFR-017 · Dep: T105 · Repo: frontend
- [ ] T154 [IMPL] Implementar la garantía de que el build de producción **no contiene ninguna ruta de código** de instrumentación, con verificación por inspección del artefacto en `frontend/scripts/check-prod-bundle.ts`
      ↳ Traza: SC-013, regla F · Dep: T153, T087 · Repo: frontend
- [ ] T155 [IMPL] Implementar la prohibición absoluta de registrar o exportar video en el build de evaluación, **incluida la medición de L2**, en `frontend/src/eval/recorder.ts` — L2 usa un dispositivo externo a la aplicación
      ↳ Traza: NFR-017(c), NFR-003, NFR-007 · Dep: T153 · Repo: frontend

### Protocolo de campo

- [ ] T156 [IMPL] Sortear el subconjunto congelado de **10 señas** con semilla registrada **antes** de la primera medición, y congelarlo en `specs/001-lsa-sign-translator/field-sample.json`; reutilizarlo idéntico en los tres entornos y en toda medición posterior
      ↳ Traza: NFR-018 — impide que la puerta de NFR-005 se acomode eligiendo señas fáciles · Dep: — · Repo: raíz
- [ ] T157 [IMPL] **[BLOQUEADA: D1 — dispositivo de referencia (NFR-003)]** Declarar el dispositivo de referencia (teléfono de gama media de los últimos 4 años y notebook) con resolución y fps efectivos **medidos**, e incorporarlo a la lista de >= 3 dispositivos de NFR-020, en `specs/001-lsa-sign-translator/devices.md`
      - **Para desbloquear**: saber qué hardware tiene disponible el equipo. **Sin dispositivo de referencia declarado, NFR-003 no es verificable**: un mismo sistema cumple o incumple según el hardware en que se lo mida.
      ↳ Traza: NFR-003, NFR-020, D1 · Dep: — · Repo: raíz
- [ ] T158 [IMPL] Documentar el protocolo de los 3 entornos en `specs/001-lsa-sign-translator/field-protocol.md` con sus parámetros observables y los mínimos comunes (>= 640×480 px, >= 15 fps efectivos), y la regla de que toda sesión fuera de rango **se descarta y se repite**
      ↳ Traza: NFR-004, NFR-016, SC-003 · Dep: T156 · Repo: raíz
- [ ] T159 [IMPL] **Ejecutar la primera medición completa de NFR-022** contra anotación humana de referencia sobre grabación externa, produciendo el desglose de las cuatro categorías y la atribución de la diferencia entre NFR-001a y NFR-001b
      ↳ Traza: NFR-022, NFR-001b, SC-020 · Dep: T057, T105 · Repo: ml
      ⚠ **Puerta de decisión**: si con segmentación continua la accuracy no alcanza **0.70 en E1** —el entorno más favorable—, el modo manual de FR-037 pasa a **predeterminado** y la segmentación continua queda opcional, documentando decisión y evidencia. **Se ejecuta en cuanto US1 está operativa, no al cierre.**
- [ ] T160 [IMPL] **[BLOQUEADA: D1]** Medir **L1** (fin detectado → texto, < 1 s) como métrica de regresión de CI, en `frontend/tests/perf/l1.spec.ts`
      - **Para desbloquear**: T157.
      ↳ Traza: NFR-003 L1, R-008 · Dep: T157, T105 · Repo: frontend
- [ ] T161 [IMPL] **[BLOQUEADA: D1]** Medir **L2** (último frame anotado a ciegas por una persona competente en LSA sobre grabación con dispositivo externo → presentación) sobre >= 50 capturas, con criterio p95 < 2 s
      - **Para desbloquear**: T157. **Criterio de revisión**: si no se alcanza, reportar el percentil real y decidir explícitamente entre optimizar `T_off`, subir el presupuesto con justificación bajo el Principio IX, o declarar otro dispositivo de referencia. **Nunca dejar el número incumplido y sin decisión.**
      ↳ Traza: NFR-003 L2, SC-002, R-008 · Dep: T157, T155 · Repo: raíz
- [ ] T162 [IMPL] Ejecutar la **primera corrida completa del protocolo** en E1, E2 y E3 sobre el subconjunto congelado, con >= 10 intentos por seña, reportando por separado por entorno
      ↳ Traza: NFR-004, NFR-005, NFR-016, SC-003 · Dep: T158, T153, T157 · Repo: raíz
      ⚠ **Criterio de revisión de NFR-005**: E3 entre 0,55 y 0,70 → se reajusta el umbral **por entorno**, documentando valor y evidencia. E3 < 0,55 → fallo de robustez: se revisa el enfoque, **no** el umbral.
- [ ] T163 [IMPL] Reportar la accuracy **desagregada por lateralidad** en el informe de campo, y declarar la diferencia en la interfaz y la documentación si es significativa
      ↳ Traza: edge case "persona zurda", spec §Limitaciones conocidas · Dep: T162 · Repo: raíz

### Validación con personas sordas (NFR-021)

- [ ] T164 [IMPL] **Iniciar la gestión de reclutamiento** con asociaciones de personas sordas, cátedras de LSA o el grupo LIDI (UNLP), **6 semanas antes** de cada ronda — se dispara **por fecha**, no por el avance técnico
      ↳ Traza: NFR-021 §Dependencia externa, D6 — es la dependencia con mayor riesgo de calendario del proyecto · Dep: — · Repo: raíz
- [ ] T165 [IMPL] Documentar el protocolo de observación en `specs/001-lsa-sign-translator/user-validation-protocol.md`: guion fijo, tareas T1–T5, criterio de aprobado (2 de 3 sin ayuda, T1 < 2 min), conducción por una persona **que no participó del diseño**, y registro por participante
      ↳ Traza: NFR-021, SC-006 · Dep: T164 · Repo: raíz
- [ ] T166 [IMPL] Ejecutar la **ronda formativa** sobre prototipo navegable al completarse US1 y US3, y registrar los resultados en `specs/001-lsa-sign-translator/user-validation-formative.md` — sirve para corregir, **no para aprobar**
      ↳ Traza: NFR-021 §Momento · Dep: T165, T120 · Repo: raíz
- [ ] T167 [IMPL] Rediseñar y volver a evaluar toda tarea que no haya alcanzado el criterio en la ronda formativa, antes de la sumativa
      ↳ Traza: NFR-021 §Criterio de aprobado · Dep: T166 · Repo: frontend
- [ ] T168 [IMPL] Ejecutar la **ronda sumativa** sobre el sistema completo antes del cierre y registrar los resultados en `specs/001-lsa-sign-translator/user-validation-summative.md` — es la que cuenta para SC-006
      ↳ Traza: NFR-021, SC-006, NFR-009 · Dep: T167, T152 · Repo: raíz
- [ ] T169 [IMPL] Si el reclutamiento no se concreta, declarar NFR-021 **no validado** y documentarlo como limitación — **no se aprueba por sustitución interna** ni por juicio del equipo de desarrollo
      ↳ Traza: NFR-021, NFR-009 · Dep: T164 · Repo: raíz
- [ ] T170 [IMPL] Calibrar la pausa de 1,5 s y el máximo de 5 señas por bloque con los participantes de las rondas, observando con qué frecuencia se cortan enunciados al medio, y actualizar `frontend/src/presentation/voice/params.json`
      ↳ Traza: FR-038 (valores provisionales), R-012 · Dep: T166, T127 · Repo: frontend
- [ ] T171 [P] [IMPL] Verificar SC-007 (legibilidad a 2 m y a 40 cm bajo iluminación E1) y SC-015 (80% de 5 personas que no conocen LSA logran encuadrar y completar una captura en < 1 min) durante las corridas de campo
      ↳ Traza: SC-007, SC-015, NFR-011, US8 · Dep: T162 · Repo: raíz
- [ ] T172 [P] [IMPL] Verificar SC-010 (una persona encuentra el vocabulario y el aviso de alcance en < 30 s sin ayuda) con participantes que nunca vieron el sistema
      ↳ Traza: SC-010, US6 · Dep: T133, T134 · Repo: raíz

> **Checkpoint 11 — no se pasa a la Fase 12 hasta que**:
> la puerta de NFR-022 se ejecutó y su decisión está documentada; la primera corrida del protocolo
> está reportada por entorno; la ronda sumativa se ejecutó **o** NFR-021 está declarado no validado
> con su limitación documentada.

---

## Fase 12 — Validación end-to-end según `quickstart.md`

**Propósito**: cerrar el ciclo con las verificaciones transversales y ejecutar la guía de validación
completa.

- [ ] T173 [IMPL] Escribir la verificación de privacidad **G3** en `frontend/tests/e2e/privacy.spec.ts`: con pulido desactivado, **cero** peticiones de red en una sesión completa; con pulido activado, solo secuencias de glosas — cero frames, cero keypoints, cero identificadores
      ↳ Traza: SC-004, NFR-006, Principio VII, Principio XIII · Dep: T149, T152 · Repo: frontend
- [ ] T174 [IMPL] Verificar NFR-007 tras una sesión de >= 10 capturas: `localStorage`, `IndexedDB`, `Cache Storage` y sistema de archivos **sin ningún** artefacto de video, frame ni miniatura, en `frontend/tests/e2e/no-video-artifacts.spec.ts`
      ↳ Traza: NFR-007, Principio VII · Dep: T173 · Repo: frontend
- [ ] T175 [IMPL] Cablear la puerta **G3** como job bloqueante de CI en `.github/workflows/ci.yml`
      ↳ Traza: constitution §Puertas de CI · Dep: T173, T174, T088 · Repo: raíz
- [ ] T176 [IMPL] Verificar SC-024 (con el pulido desactivado, sesión de 10 señas con voz agrupada pronunciando glosa cruda, **sin ninguna petición de red** y sin degradación del reconocimiento) y SC-026 (servicio caído, límite excedido o fuera de presupuesto → glosa cruda sin bloqueos ni reintentos en bucle)
      ↳ Traza: SC-024, SC-026 · Dep: T149, T152 · Repo: frontend
- [ ] T177 [IMPL] Verificar SC-014 (con la conectividad deshabilitada tras la carga, sesión de 10 capturas sin errores de red visibles y sin degradación medible del reconocimiento)
      ↳ Traza: SC-014, FR-031 · Dep: T176 · Repo: frontend
- [ ] T178 [IMPL] Verificar SC-005 con la auditoría reproducible de 100 capturas sin reconocimiento sobre umbral (subconjunto congelado en modo estricto más gestos deliberadamente fuera de vocabulario, en la proporción del protocolo): **cero** casos con etiqueta candidata mostrada o pronunciada
      ↳ Traza: SC-005, FR-017, Principio VIII · Dep: T069, T156 · Repo: frontend
- [ ] T179 [IMPL] Verificar SC-011 (eventos inválidos provocados: el sistema descarta y explica el motivo, en **cero** casos clasifica el tramo) y SC-012 (ningún evento ejecuta más de 3 intentos, verificable por instrumentación local)
      ↳ Traza: SC-011, SC-012 · Dep: T103, T067 · Repo: frontend
- [ ] T180 [IMPL] Revisar **todos** los valores provisionales de la tabla de [research.md R-012](./research.md) y, para cada uno, registrar su revisión o una nota escrita de por qué se mantiene — un número provisional que sobrevive sin revisión deja de ser provisional por omisión
      ↳ Traza: R-012, spec §Assumptions "Valores provisionales declarados" · Dep: T162, T161, T145, T170 · Repo: raíz
- [ ] T181 [IMPL] Ejecutar la guía completa de [quickstart.md](./quickstart.md) V1→V15 y registrar el resultado de cada escenario en `specs/001-lsa-sign-translator/validation-report.md`
      ↳ Traza: quickstart.md, todos los SC · Dep: T175, T180 · Repo: raíz
- [ ] T182 [IMPL] Actualizar `Claude.md` con el estado real del proyecto tras la implementación: baseline vigente con su `experiment-id`, decisiones cerradas, deuda técnica saldada (XII.1–XII.4) y limitaciones declaradas
      ↳ Traza: Principio I, Principio XII, regla D · Dep: T181 · Repo: raíz

> **Checkpoint 12 — el proyecto está listo para cierre cuando**:
> G1, G2 y G3 están en verde y son bloqueantes; los criterios SC-001 a SC-026 están verificados o
> declarados no alcanzados con su justificación escrita; la tabla de valores provisionales está
> revisada punto por punto; `quickstart.md` corrió completa.

---

## Dependencias y orden de ejecución

### Entre fases

```text
F1 Contrato ─► F2 Pipeline ─► F3 Dataset+Baseline ─► F4 Evaluación ─► F5 Confianza
                                       │                                    │
                                       └──────────────► F6 Dispositivo ◄────┘
                                                              │
                                                              ├─► F7 devinfer
                                                              └─► F8 Captura+Segmentación
                                                                        │
                                                                        ├─► F9 Presentación
                                                                        │        │
                                                                        │        └─► F10 Pulido
                                                                        │
                                                                        └─► F11 Robustez ─► F12 E2E
```

- **F1 bloquea todo.** Nada se construye antes de que el contrato esté definido, implementado en
  ambos entornos y verificado.
- **F3 bloquea todo lo que dependa de un modelo entrenado** (F5, F6, F7, F8 en su cableado final).
- **F5 depende de los datos de F4**: los umbrales salen de la curva de confianza, no de la intuición.
- **La puerta NFR-022 (T159) se ejecuta al terminar F8**, no en F11 ni al cierre.

### Cadena crítica

```text
T007 → T009 → T012 → T025 → T035 → T041 → T044 → T071 → T075 → T078 → T105 → T159
contrato  tabla  test   S2   dataset entren. BASELINE export artefacto cliente pipeline PUERTA
```

T044 (fijar el baseline nuevo) es el nudo del proyecto: **nada aguas abajo tiene número de
referencia hasta que se complete**.

### Paralelismo por repositorio

| Momento | ml | frontend | backend | raíz |
|---|---|---|---|---|
| Tras Checkpoint 1 | T020–T029 | T030–T032 | — | — |
| Tras Checkpoint 3 | T045–T049 `[EXP]`, T050–T058 | — | — | T036, T037 |
| Tras Checkpoint 6 | — | T093–T111 | T085–T092 | — |
| Tras Checkpoint 8 | T159 | T112–T136 | T137–T146 | T156–T158 |

**Grupos `[P]` grandes**:

- T002–T006 (setup por repositorio)
- T012–T015 (tests de contrato en ambos entornos)
- T050–T053 (las cuatro salidas del módulo de evaluación)
- T045–T049 (los cinco experimentos de la Fase 3, todos independientes entre sí)
- T133–T135 (contenido de encuadre de expectativas)

### Las tareas `[EXP]` no bloquean

T033, T045, T046, T047, T048, T049, T058 pueden quedar abiertas sin detener el avance. **Única
excepción declarada**: T042 → T044, porque el Principio V exige justificación escrita antes de fijar
un baseline por debajo del heredado.

T048 y T063 alimentan valores de tareas `[IMPL]` bloqueadas (T106, T062), pero el bloqueo está en la
tarea `[IMPL]`, no en el experimento: si T048 da resultado negativo, T106 se resuelve igual —
el segmentador no filtra por duración.

---

## Estrategia de entrega

### MVP — US1 con confianza explícita

**Fases 1 → 8, más la parte de US1 de la Fase 9** (T112, T113, T119, T120).

Entrega: una persona seña sobre stream continuo tras una sola acción de inicio, el sistema detecta
inicio y fin de cada seña, la clasifica y muestra la palabra con su categoría de confianza; bajo
umbral dice "no entendí" sin revelar la etiqueta.

**Validación independiente**: T110 (20 señas → 20 eventos), T111 (3 min sin señar → 0 traducciones),
T178 (100 capturas bajo umbral → 0 etiquetas candidatas).

**No es entregable un MVP que adivine**: la confianza explícita va incluida en US1 y no en una
historia posterior (Principio VIII).

### Incrementos posteriores

1. **US2 — voz** (Fase 9 voz + Fase 10). Con el dispositivo apoyado, la voz es el único canal que
   alcanza al interlocutor sin manipular el aparato.
2. **US3 — feedback de encuadre** (T119–T123). Sin esto la persona no distingue "no me entendió" de
   "no me está viendo" y repite señas a ciegas.
3. **US4, US5 — descarte e historial** (T114–T118).
4. **US6, US7, US8** (T131–T136, T123).

### Momentos que no se postergan

| Qué | Cuándo | Por qué |
|---|---|---|
| Puerta NFR-022 (T159) | En cuanto US1 es operativa | Postergarla elimina la posibilidad de reaccionar y convierte el respaldo de FR-037 en letra muerta |
| Primera corrida del protocolo (T162) | En cuanto US1 y US3 están completas | Para que quede tiempo de reaccionar al número real |
| Reclutamiento NFR-021 (T164) | 6 semanas antes de cada ronda, por fecha | Es la dependencia con mayor riesgo de calendario |
| Modo de respaldo manual (T109) | Con US1, no al final | Decidirlo ahora cuesta poco; agregarlo en el último mes, mucho |

---

## Notas

- `[P]` = archivos distintos, sin dependencias pendientes.
- Verificar que los tests de contrato **fallan** antes de implementar lo que los hace pasar.
- Commits en español con conventional commits, sin coautoría (`Claude.md`).
- Cada PR declara: el requisito que implementa (Principio I); si toca modelo, datos o
  preprocesamiento, la métrica por sujeto medida frente al baseline vigente (Principio V); y toda
  violación constitucional con su justificación escrita.
- Cada corrida de ML registra configuración, seed, dataset y dependencias, en carpeta propia que
  **nunca se sobrescribe** (Principio VI).
- **Cero tareas sin referencia** en esta lista: si se agrega una tarea nueva sin trazar a un
  requisito o a una decisión, marcarla para revisión antes de ejecutarla (regla G).
