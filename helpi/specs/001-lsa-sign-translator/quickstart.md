# Quickstart — Guía de validación

**Feature**: `001-lsa-sign-translator` | **Date**: 2026-07-29 | **Plan**: [plan.md](./plan.md)

Escenarios de validación que prueban que el sistema funciona extremo a extremo, en el orden en que
se vuelven ejecutables.

> **Estado**: en el momento de escribir esta guía **no existe código**. Las fases de especificación,
> aclaración, checklist, planificación y generación de tareas son documentales (Principio XIV). Los
> comandos de abajo son el **contrato de invocación** que la implementación debe cumplir: definen
> qué se ejecuta y qué debe observarse, no describen algo que ya corre.

Cada escenario declara: **prerequisitos → comando → resultado esperado → requisito que verifica**.

---

## Convenciones

```text
ml/         proyecto Python de datos, entrenamiento y evaluación
frontend/   cliente web
backend/    polish (producción) · devinfer (solo dev/eval)
contracts/keypoints/   contrato declarativo + fixtures (raíz del repo)
```

Los artefactos de cada corrida viven en `ml/artifacts/<experiment-id>/` y **nunca se sobrescriben**
(Principio VI).

---

## V0 — Prerequisitos comunes

| Requisito | Detalle |
|---|---|
| Python | 3.12 |
| Node | 20+ |
| LSA64 | Versión **cut**, descargada desde el sitio oficial. Licencia **no comercial** (Principio X) |
| GPU (opcional) | Solo para entrenar. Blackwell sm_120 → PyTorch build cu128+ (`Claude.md`) |

```bash
python -m venv .venv && . .venv/bin/activate && pip install -e "ml[dev]"
```

```bash
npm --prefix frontend ci
```

---

## V1 — El contrato de keypoints se sostiene (puerta G1, bloqueante)

**Es la primera validación y la más importante.** Si esta falla, todo lo demás mide otra cosa.

**Prerequisitos**: `contracts/keypoints/fixtures/` poblado y congelado (Fase A).

```bash
python -m tools.contract_equivalence --contract contracts/keypoints/kp-contract.json --report artifacts/contract-report.json
```

**Resultado esperado**

- Los productores de Python y TypeScript producen el mismo vector de 201 coordenadas para cada
  fixture, con **error absoluto máximo por coordenada <= 1e-6**.
- La cobertura del reporte incluye las 64 clases, los casos `T > SEQ_LEN`, `T < SEQ_LEN`,
  `T == SEQ_LEN`, y los casos de mano ausente y de colisión de handedness.

**Prueba negativa (obligatoria)** — un test verde que nunca se vio fallar no es evidencia de nada:

```bash
python -m tools.contract_equivalence --inject-fault center-z
```

Debe **fallar**. Igual con `--inject-fault round-indices` y `--inject-fault swap-hands`.

**Verifica**: NFR-014 Nivel 1 · SC-017 · Principio IV
**Contrato**: [contracts/keypoints.md](./contracts/keypoints.md)

---

## V2 — Cada etapa del pipeline se verifica por separado

**Prerequisitos**: V0.

```bash
pytest ml/tests/stages -v
```

**Resultado esperado** — sin video y sin modelo, con datos sintéticos:

| Etapa | Qué se verifica |
|---|---|
| S1 | Forma `(T, 201)`, `T` = frames procesados, relleno de mano ausente, asignación izquierda/derecha por handedness, timestamps estrictamente crecientes |
| **S2** | `T > N`, `T < N`, `T == N`, `T == 1`, `T == 0` (rechazo), e `indices` idénticos a la **tabla congelada** de fixtures |
| S3 | Punto medio de hombros en `(0,0)`; la componente `z` **idéntica** a la entrada; rechazo si faltan los landmarks 11/12 |
| S4 | Correspondencia secuencia ↔ etiqueta ↔ sujeto ↔ repetición; ningún sujeto en más de un split; manifiesto completo |

**Verifica**: NFR-014 · NFR-002
**Contrato**: [contracts/pipeline-stages.md](./contracts/pipeline-stages.md)

---

## V3 — Regenerar el dataset con Tasks API

**Prerequisitos**: V1, V2, LSA64 cut descargado.

```bash
python -m lsa_ml.stages.s1_extract --videos <lsa64_cut> --out artifacts/<ds>/s1_raw
```

```bash
python -m lsa_ml.stages.s2_temporal --in artifacts/<ds>/s1_raw --out artifacts/<ds>/s2_fixed
```

```bash
python -m lsa_ml.stages.s3_spatial --in artifacts/<ds>/s2_fixed --out artifacts/<ds>/s3_norm
```

```bash
python -m lsa_ml.stages.s4_assemble --in artifacts/<ds>/s3_norm --out artifacts/<ds>/dataset
```

**Resultado esperado**

- `X.npy` de forma `(3200, 40, 201)`, con `y`, `subject` y `repetition` alineados.
- `MANIFEST.json` con versión de contrato `2.0.0`, seed, versiones exactas de dependencias, splits
  por sujeto y `content_hash`.
- **Reproducibilidad**: re-ejecutar las cuatro etapas produce el **mismo `content_hash`**.

**Verifica**: NFR-001a (insumo) · Principio VI · deuda XII.1
**Detalle**: [research.md R-001](./research.md) · [data-model.md §3–§4](./data-model.md)

---

## V4 — Re-validar el baseline con split por sujeto

**Prerequisitos**: V3.

```bash
python -m lsa_ml.train --dataset artifacts/<ds>/dataset --seed 42 --out artifacts/<exp>
```

```bash
python -m lsa_ml.eval --experiment artifacts/<exp> --split subject-holdout --test-subject 10
```

**Resultado esperado**

- `eval/summary.json` con `protocol = "subject-holdout"` y `test_subject = 10`.
- **`accuracy >= 0.85`** (NFR-001a).
- Si `delta < 0`: el artefacto **no se mergea** sin `justification` escrita, con la métrica medida,
  el motivo atribuido y el plan de recuperación (Principio V). Se escribe al detectarse la caída, no
  al cierre del proyecto.

**El split aleatorio no produce números reportables.** No hay bandera para pedirlo (NFR-002).

**Verifica**: NFR-001a · NFR-002 · SC-001 (primera mitad) · Principio V

---

## V5 — Evaluación por clase y diagnóstico

**Prerequisitos**: V4. Se **regenera en cada evaluación**, no es un análisis puntual.

```bash
python -m lsa_ml.eval --experiment artifacts/<exp> --full-report
```

**Resultado esperado** — cinco salidas obligatorias en `artifacts/<exp>/eval/`:

| Archivo | Contenido |
|---|---|
| `summary.json` | Accuracy global con split por sujeto + LOSO |
| `confusion.npy` / `.csv` | Matriz 64 × 64 completa sobre el conjunto de test |
| `per_class.json` | Precisión, recall, F1 y soporte de las 64 señas |
| `confused_pairs.json` | Pares más confundidos, ordenados por `conf(i→j) + conf(j→i)` |
| `confidence.json` | Distribución de confianza de aciertos vs errores, con `overlap` |

`overlap` es el número que decide si algún umbral puede cumplir el Principio VIII. Si las dos
distribuciones se solapan fuertemente en todas las clases, el problema es de modelo y ninguna
política de confianza lo arregla; se reporta como tal.

**Verifica**: SC-001 · SC-020 (insumo) · Principio V
**Detalle**: [research.md R-007](./research.md) y [R-013](./research.md)

---

## V6 — Ablación de la componente `z` (201 vs 134)

**Prerequisitos**: V4.

```bash
python -m lsa_ml.experiments.z_ablation --dataset artifacts/<ds>/dataset --seeds 1,2,3,4,5
```

**Resultado esperado**: media ± desviación de accuracy en ambas configuraciones, con desglose por
clase, sobre el mismo split por sujeto.

- `acc(201) − acc(134) <= 0.01` → **`z` no aporta**. Se documenta y se **propone** simplificar el
  contrato.
- **La propuesta no cambia nada por sí sola.** El contrato de 201 está en el Principio IV, marcado NO
  NEGOCIABLE: bajarlo a 134 exige el procedimiento de enmienda, y este experimento es justamente la
  evidencia empírica que ese procedimiento pide. Hasta entonces el contrato es 201, aunque `z`
  resulte inútil.

**Verifica**: [research.md R-002](./research.md)

---

## V7 — Compensación de optional stopping

**Prerequisitos**: V4.

```bash
python -m lsa_ml.eval.stopping --experiment artifacts/<exp> --calibrate-on-subject 9 --report-on-subject 10
```

**Resultado esperado**

- `stopping.json` con la curva **completa** de FPR y cobertura por umbral y por `k ∈ {1,2,3}`.
- Umbrales compensados para estricto / normal / permisivo tales que
  `FPR(θ*, 3) − FPR(θ_ref, 1) <= 0,02`.
- La calibración usa el **sujeto 9**; el reporte usa el **sujeto 10**. Calibrar y reportar sobre el
  mismo sujeto convierte SC-016 en una tautología.

**Verifica**: NFR-019 · SC-016
**Detalle**: [research.md R-005](./research.md)

---

## V8 — El modelo que corre es el modelo medido

**Prerequisitos**: V4.

```bash
python -m lsa_ml.export --experiment artifacts/<exp> --format onnx
```

```bash
python -m lsa_ml.export.verify --experiment artifacts/<exp>
```

**Resultado esperado**

- Paridad numérica: error absoluto máximo de logits ONNX vs PyTorch <= 1e-4.
- Paridad de métrica: accuracy del modelo exportado sobre el sujeto 10, con diferencia <= 0.005
  respecto de PyTorch. Si la excede, **el export está roto y el trabajo se detiene ahí**.
- La métrica que se reporta como NFR-001a es la del **artefacto exportado**.
- `model/` contiene `model.onnx`, `model.pt`, `labels.json`, `thresholds.json` y `model-card.json`.

**Verifica**: NFR-001a sobre el artefacto real · [research.md R-003](./research.md)

---

## V9 — Reconocer sobre stream continuo (US1)

**Prerequisitos**: V1, V8. Modelo exportado disponible para el cliente.

```bash
npm --prefix frontend run dev
```

**Guion de validación** — una persona realiza **20 señas conocidas separadas por pausas naturales**,
tras una sola acción de inicio:

| Observación esperada | Requisito |
|---|---|
| Exactamente **20 eventos** de seña detectada: cero fusiones, cero duplicados | SC-018, FR-036 |
| Cada reconocimiento sobre umbral se muestra con su **categoría de confianza** (alta / media) | FR-013 |
| Los que no llegan al umbral producen "no entendí" **sin revelar la etiqueta candidata** | FR-017, SC-005 |
| Ningún evento ejecuta más de **3 intentos** | SC-012 |
| Nadie toca el dispositivo entre seña y seña | FR-007, DD-002 |
| El estado visible distingue los cuatro estados de FR-010 en color **y** forma | FR-010 |

**Contraprueba obligatoria** — sesión de **3 minutos sin señar** (conversar, acomodarse el pelo,
gesticular, desplazarse):

```text
Resultado esperado: CERO traducciones.
```

**Verifica**: US1 · FR-034 · SC-018 · SC-019 · SC-012

---

## V10 — Puerta de decisión de la segmentación (NFR-022)

**Se ejecuta en cuanto V9 pasa, NO al final del proyecto.** Postergarla elimina la posibilidad de
reaccionar y convierte el modo de respaldo de FR-037 en letra muerta.

**Prerequisitos**: V9, más anotación humana de referencia sobre grabación externa.

```bash
python -m lsa_ml.eval.segmentation --annotations <ref> --system-log <log> --out artifacts/<exp>/eval
```

**Resultado esperado** — `segmentation_error.json` con las cuatro categorías desglosadas:

(a) no detectada · (b) falso positivo · (c) mal delimitada · (d) mal clasificada

más `nfr_001a`, `nfr_001b` y la **atribución explícita** de la diferencia entre ambas.

**Puerta**: si con segmentación continua la accuracy no alcanza **0.70 en E1** —el entorno más
favorable—, el modo de respaldo manual de FR-037 pasa a **predeterminado** y la segmentación
continua queda como funcionalidad opcional, documentando la decisión y la evidencia.

**Verifica**: NFR-022 · NFR-001b · SC-020

---

## V11 — Latencia L1 y L2

**Prerequisitos**: V9, dispositivo de referencia **declarado** (sin él NFR-003 no es verificable).

**L1** (proxy automatizable, regresión de CI): tiempo entre el fin de seña detectado por el sistema
y la presentación del texto. Umbral: **< 1 s**.

**L2** (métrica constitucional): tiempo entre el **último frame de la seña** —anotado offline y a
ciegas por una persona competente en LSA, sobre grabación tomada con un **dispositivo externo a la
aplicación**— y la presentación. Umbral: **< 2 s en el p95 de >= 50 capturas**.

> La aplicación **no registra video**, ni siquiera para medir latencia (NFR-017(c)). Esa prohibición
> no se relaja.

**Resultado esperado**: desglose del presupuesto que confirme lo previsto en
[research.md R-008](./research.md) — el costo dominante es `T_off` (silencio de confirmación de fin,
<= 800 ms), no los 3 intentos (~50 ms de cómputo total).

Si no se alcanzan los 2 s: reportar el percentil real y **decidir explícitamente** entre optimizar
`T_off`, subir el presupuesto con justificación bajo el Principio IX, o declarar otro dispositivo de
referencia. Nunca dejar el número incumplido y sin decisión.

**Verifica**: NFR-003 · SC-002

---

## V12 — Voz, degradación de TTS y pulido

**Prerequisitos**: V9.

```bash
npm --prefix frontend test -- voice
```

| Caso | Resultado esperado | Requisito |
|---|---|---|
| Sin voz `es-AR` | Cae a `es-UY`, luego a cualquier `es-*` | FR-012 |
| Sin ninguna voz en español | Lo informa explícitamente y continúa **solo con texto**, sin bloquear | US2 esc. 5 |
| Sin `speechSynthesis` | Mismo camino, sin excepción no capturada | US2 esc. 5 |
| Bloques consecutivos | Se encolan; **nunca** se solapan | FR-038, R-010 |
| Descarte durante la locución | Cancela la reproducción en curso | FR-019 |
| Bajo umbral | No se pronuncia **nada**: ni la etiqueta, ni "no entendí" | FR-017, R-010 |

**Pulido** (con `backend/polish` corriendo):

```bash
pytest backend/polish/tests -v
```

| Caso | Resultado esperado | Criterio |
|---|---|---|
| 50 secuencias de glosas | Cero palabras de contenido no trazables por lema | SC-021 |
| Peticiones adversarias con texto libre | 100% rechazadas | SC-025 |
| Servicio caído / límite excedido / fuera de presupuesto | Sesión de 10 señas con **glosa cruda**, sin bloqueos, sin esperas visibles, sin reintentos en bucle | SC-026 |
| Pulido desactivado | Sesión de 10 señas con **cero peticiones de red** | SC-024 |
| Presentación | Glosa cruda visible junto a la frase pulida, siempre | SC-022 |

**Verifica**: US2 · FR-012 · FR-038–FR-041 · NFR-023–NFR-027
**Contrato**: [contracts/polish-service.md](./contracts/polish-service.md)

---

## V13 — Privacidad verificable (puerta G3, bloqueante)

**Prerequisitos**: V9.

```bash
npm --prefix frontend run test:e2e -- privacy
```

| Verificación | Resultado esperado | Criterio |
|---|---|---|
| Pulido desactivado | **Cero** peticiones de red en una sesión completa | SC-004 |
| Pulido activado | Solo secuencias de glosas: cero frames, cero keypoints, cero identificadores | SC-004 |
| Instalación nueva | Ninguna glosa sale antes de que la persona vea el aviso de FR-041 y decida | SC-023 |
| Tras >= 10 capturas | `localStorage`, `IndexedDB`, `Cache Storage` y sistema de archivos **sin ningún** artefacto de video, frame ni miniatura | NFR-007 |
| Borrado de sesión | Historial, registro de descartes y buffer de keypoints desaparecen juntos | US4 esc. 4, NFR-008 |
| Build de producción | **Ninguna** ruta de código de instrumentación de evaluación, por inspección del artefacto distribuido | SC-013 |

**Verifica**: NFR-006 · NFR-007 · NFR-008 · NFR-017 · Principio VII

---

## V14 — Protocolo de campo (3 entornos)

**Se ejecuta en cuanto US1 y US3 están completas, no al final** (NFR-005).

**Prerequisitos**: build de evaluación (NFR-017), lista de >= 3 dispositivos (NFR-020), subconjunto
congelado de **10 señas** sorteado con semilla registrada **antes** de la primera medición
(NFR-018).

| Entorno | Condiciones | Mínimos comunes |
|---|---|---|
| E1 | Interior bien iluminado, 300–750 lux, fondo estático, dispositivo apoyado | >= 640×480 px |
| E2 | Interior con luz pobre, 50–150 lux, fondo estático, dispositivo apoyado | y >= 15 fps efectivos |
| E3 | Exterior >= 1000 lux o contraluz, fondo dinámico con personas, cámara **a pulso en desplazamiento** | Ambas condiciones |

Toda sesión fuera de los rangos declarados de lux, resolución o fps **se descarta y se repite**.
La distancia persona–cámara **se registra, no se impone**.

**Resultado esperado**: >= 0.70 de accuracy en **cada** entorno, con >= 10 intentos por seña.

Si E3 queda entre 0.55 y 0.70 → se reajusta el umbral **por entorno**, documentando valor y
evidencia. Si queda por debajo de 0.55 → fallo de robustez: se revisa el enfoque, no el umbral.

**Verifica**: NFR-004 · NFR-005 · NFR-016 · NFR-018 · NFR-020 · SC-003

---

## V15 — Validación con personas sordas (NFR-021)

**No es un test automatizado y no se sustituye por uno.** Ninguna afirmación de accesibilidad puede
darse por cumplida sobre la base del juicio del equipo de desarrollo.

**La gestión de reclutamiento arranca 6 semanas antes de cada ronda**, disparada por fecha y no por
el avance técnico. Es la dependencia con mayor riesgo de calendario del proyecto.

| Aspecto | Definición |
|---|---|
| Participantes | Mínimo **3** personas sordas o hipoacúsicas usuarias de LSA. Se admite sustituir **como máximo 1** por un intérprete titulado, asentándolo en el informe. Nunca las 3 |
| Rondas | **Formativa** sobre prototipo navegable, al completarse US1 y US3 (corrige, no aprueba). **Sumativa** sobre el sistema completo, antes del cierre — es la que cuenta |
| Tareas | T1 iniciar sesión y lograr un reconocimiento · T2 descartar uno incorrecto · T3 encontrar la lista de señas · T4 cambiar el umbral · T5 identificar el motivo de un fallo provocado |
| Aprobado | Cada tarea completada **sin ayuda** por al menos 2 de cada 3 participantes. T1 en < 2 minutos |
| Conducción | Persona que **no participó del diseño** de la interfaz, guion fijo, sin asistir ni sugerir |
| Registro | Por participante y tarea: completada sí/no, tiempo de T1, fluidez declarada, usuaria nativa o tardía, comunicación por escrito o con intérprete |
| Consentimiento | Rige NFR-017(a). **No se registra video de los participantes** |

Si una tarea no alcanza el criterio, **se rediseña y se vuelve a evaluar** antes de cerrar el
proyecto. Si el reclutamiento no se concreta, el requisito se declara **no validado** y se documenta
como limitación; no se aprueba por sustitución interna.

**Verifica**: NFR-009 · NFR-021 · SC-006

---

## Orden de ejecución

```text
V0 ─► V1 ─► V2 ─► V3 ─► V4 ─┬─► V5 ─► V7 ──┐
                            ├─► V6         │
                            └─► V8 ────────┴─► V9 ─┬─► V10  (puerta NFR-022)
                                                   ├─► V11
                                                   ├─► V12
                                                   ├─► V13
                                                   └─► V14 ─► V15
```

**Puertas que detienen el trabajo si fallan**: V1 (contrato), V4 (baseline, con justificación
escrita), V8 (paridad del export), V10 (decisión de repliegue a modo manual), V13 (privacidad).
