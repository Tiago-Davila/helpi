# Research: Traductor LSA de señas aisladas (LSA64)

**Feature**: `001-lsa-sign-translator` | **Date**: 2026-07-29 | **Plan**: [plan.md](./plan.md)

Fase 0 del flujo de planificación. Cada entrada resuelve una incógnita del *Technical Context* o un
riesgo abierto del checklist, con el formato **Decisión / Justificación / Alternativas descartadas**.

**Cómo leer los estados**:

| Estado | Significado |
|---|---|
| **DECIDIDO** | La decisión está tomada y es ejecutable tal como está escrita. |
| **PROCEDIMIENTO** | Lo decidido es *cómo y cuándo* se resuelve; el valor sale de una medición que aún no existe. |
| **ABIERTO** | Requiere un dato externo al repositorio (hardware, infraestructura, resultado de campo). |

Una entrada en **PROCEDIMIENTO** no es una decisión postergada: postergada sería no decir nada. El
compromiso es el método, el momento y el criterio de aceptación.

---

## Índice

| ID | Tema | Estado |
|---|---|---|
| R-001 | Migración MediaPipe Holistic → Tasks API | DECIDIDO |
| R-002 | Aporte real de la componente `z` (201 vs 134) | PROCEDIMIENTO |
| R-003 | Runtime de inferencia en el dispositivo | DECIDIDO |
| R-004 | Segmentación temporal: detectar inicio y fin de seña | DECIDIDO |
| R-005 | Optional stopping: inflación de confianza y compensación | PROCEDIMIENTO |
| R-006 | Tiempo muerto inicial y transiciones ausentes en LSA64 cut | DECIDIDO |
| R-007 | Umbral global vs calibrado por clase | PROCEDIMIENTO |
| R-008 | Presupuesto de latencia y dispositivo de referencia | DECIDIDO / ABIERTO |
| R-009 | Criterio exacto de normalización temporal | DECIDIDO |
| R-010 | TTS con Web Speech API: selección, cola y degradación | DECIDIDO |
| R-011 | Servicio de pulido: modelo, despliegue y validación | ABIERTO / DECIDIDO |
| R-012 | Valores provisionales: criterio y momento de revisión | PROCEDIMIENTO |
| R-013 | Pares de señas confundidos y configuración de mano | PROCEDIMIENTO |
| R-014 | Equivalencia numérica Python ↔ TypeScript | DECIDIDO |
| R-015 | Antecedentes de viabilidad de inferencia local | ABIERTO |

---

## R-001 — Migración de MediaPipe Holistic a Tasks API

**Estado**: DECIDIDO (mapeo y política) + PROCEDIMIENTO (escala de `z`)

### Contexto

El modelo vigente se entrenó con **MediaPipe Holistic** en Python, que fue removido de las versiones
nuevas del paquete; la POC quedó fijada en `mediapipe==0.10.21` (con `numpy<2`, `opencv<4.10`). El
cliente web ya usa **Tasks API**. Hoy, por lo tanto, entrenamiento e inferencia usan **detectores
distintos**: una violación latente del contrato del Principio IV que no produce ningún error visible,
solo peores predicciones.

### Decisión

1. **El preprocesamiento migra a Tasks API** (`HandLandmarker` + `PoseLandmarker`). Se elimina toda
   dependencia de Holistic y el pin de `mediapipe==0.10.21`.
2. **La migración obliga a regenerar el dataset completo** desde los 3200 videos de LSA64 y a
   **re-entrenar**. Los keypoints de Tasks API no son numéricamente idénticos a los de Holistic:
   son modelos y pipelines de ROI distintos, no dos envoltorios del mismo detector. Reutilizar los
   `.npy` existentes con un cliente Tasks API es precisamente el fallo silencioso contra el que
   advierte el Principio IV.
3. **El modelo re-entrenado se re-valida contra 0.85** con split por sujeto (train 1-8, val 9,
   test 10). Toda caída requiere justificación escrita en el PR con la métrica medida, el motivo
   atribuido y el plan de recuperación (Principio V). La justificación se escribe **al detectarse la
   caída**, no al cierre del proyecto.
4. **El contrato de 201 coordenadas se preserva**: la migración cambia el productor, no el vector.

### Cómo se preserva el contrato de 201 coordenadas

**Manos (63 + 63)**

`HandLandmarker` devuelve, por mano detectada, 21 landmarks normalizados y una clasificación de
`handedness` (`Left` / `Right`) con su score. El orden de los 21 landmarks es el mismo que en
Holistic (topología de la mano de MediaPipe), de modo que el aplanado `[x,y,z] × 21` no cambia.

Lo que **sí** cambia es cómo se decide qué mano es cuál:

- Holistic asignaba las manos por asociación con la pose.
- Tasks API las asigna con un clasificador de `handedness`.

Reglas del contrato:

- `numHands = 2`. Si se detectan dos manos con la **misma** etiqueta de handedness, se conserva la
  de mayor score en su ranura y la otra se descarta; la ranura restante se rellena como ausente. Es
  preferible perder una mano a colocarla en la ranura equivocada, que produce un vector coherente en
  forma y absurdo en contenido.
- **La imagen que recibe el detector nunca va espejada.** El modo espejo de FR-026 se aplica al
  elemento `<video>` por CSS, en la capa de presentación, y no llega al detector. Espejar la entrada
  invierte la etiqueta de handedness y por lo tanto intercambia las dos mitades de 63 valores del
  vector. Es un error de una línea con consecuencias indistinguibles de un problema de modelo. Hay
  un test de contrato para esto.
- `handedness` de MediaPipe se reporta desde la perspectiva de la imagen. La correspondencia entre
  esa etiqueta y las ranuras `mano_izq` / `mano_der` del contrato se fija en `landmark-map.json` y
  se verifica contra un fixture con una mano identificable, no se deduce de la documentación.

**Política de relleno cuando una mano no se detecta**

Vector de **63 ceros** en la ranura correspondiente, aplicado **después** de la normalización
espacial: la etapa espacial no toca las manos ausentes. Es la política de la POC y se conserva por
compatibilidad conceptual con el baseline.

Limitación conocida y declarada: como el vector queda centrado en el punto medio de los hombros, un
relleno de ceros es indistinguible de una mano ubicada exactamente en ese punto. En la práctica no
ocurre —una mano en el centro del pecho tiene sus 21 landmarks dispersos alrededor, no todos en el
origen— pero es una ambigüedad real del contrato y queda anotada. Añadir un canal de máscara de
presencia resolvería la ambigüedad **y rompería el contrato de 201**, que es NO NEGOCIABLE: si la
evaluación por clase de la Fase B mostrara que las señas de una sola mano se confunden
sistemáticamente entre sí, esa sería la evidencia empírica que el procedimiento de enmienda del
Principio IV exige, y recién ahí se propone.

**Pose (75)**

`PoseLandmarker` devuelve 33 landmarks con la misma topología BlazePose GHUM que Holistic; los
índices **0–24** son equivalentes uno a uno, incluidos los hombros 11 y 12 que definen el centro de
la normalización espacial. Se toman los 25 primeros y se descartan 25–32 (piernas), como en el
baseline.

Se usa `numPoses = 1`. La selección de persona cuando hay más de una en cuadro (edge case de la
spec: mayor área de torso al iniciar, mantenida durante la sesión) es responsabilidad del módulo de
captura, no del contrato.

**Componente `z`** — PROCEDIMIENTO

`HandLandmarker` reporta `z` relativa a la muñeca de esa mano, con una escala aproximadamente
comparable a la de `x`. `PoseLandmarker` reporta `z` relativa al punto medio de las caderas, en
unidades de ancho de imagen normalizado. Ambas son estimaciones de profundidad monocular.

**No se asume que Tasks API reporte `z` en la misma escala que Holistic.** Se mide antes de aceptar
el dataset regenerado:

- Procesar el mismo subconjunto de videos con Holistic (entorno de la POC, `mediapipe==0.10.21`) y
  con Tasks API.
- Comparar, por bloque (mano izquierda, mano derecha, pose), la distribución de `z`: media,
  desviación, rango intercuartílico y correlación entre ambos productores.
- **Criterio**: si las escalas difieren en más de un factor 1.5 en desviación estándar, se documenta
  como cambio de distribución de entrada y se declara explícitamente como causa candidata de
  cualquier caída respecto de 0.85, antes de buscar la causa en el modelo.
- El resultado se registra en el informe de la Fase A y se enlaza desde aquí.

Esta medición es además el insumo directo de **R-002**: si `z` cambia de escala entre productores y
al mismo tiempo no aporta desempeño, la conclusión práctica es fuerte.

### Alternativas descartadas

- **Congelar `mediapipe==0.10.21` para siempre.** Deja el proyecto sobre un paquete removido, sin
  parches de seguridad, e incompatible con NumPy 2. Y no resuelve el problema real: el cliente web
  ya usa Tasks API, de modo que la divergencia entrenamiento/inferencia sigue existiendo hoy.
- **Portar el cliente web a Holistic.** No existe Holistic para web en Tasks API; sería volver a la
  Solutions API legacy, también en fin de vida.
- **Reutilizar el dataset de Holistic y entrenar solo un "adaptador" de dominio.** Agrega una etapa
  al pipeline (Principio II exige justificarla como enmienda), introduce un modelo más que mantener,
  y sustituye un problema medible —regenerar y re-entrenar— por uno que no se puede caracterizar.

---

## R-002 — Aporte real de la componente `z` (201 vs 134 coordenadas)

**Estado**: PROCEDIMIENTO

### Contexto

La `z` de MediaPipe es una estimación de profundidad monocular de fiabilidad discutible y ocupa
**67 de las 201 coordenadas** por frame (21 + 21 + 25). Si no aporta, el contrato se simplifica, la
entrada del modelo baja un 33% y el modelo se achica.

### Decisión

Se ejecuta un **experimento de ablación** en la Fase B, con el dataset ya regenerado con Tasks API:

| Parámetro | Valor |
|---|---|
| Configuración A | 201 coordenadas (contrato vigente) |
| Configuración B | 134 coordenadas (se eliminan las 67 componentes `z`) |
| Split | Por sujeto: train 1-8, val 9, test 10 (Principio V) |
| Seeds | 5 seeds registradas, idénticas en A y B |
| Hiperparámetros | Idénticos salvo la dimensión de entrada |
| Métricas | Accuracy global, precisión/recall por clase, matriz de confusión, distribución de confianza |
| Reporte | Media ± desviación sobre las 5 seeds, en ambas configuraciones |

**Criterio de decisión**, fijado antes de medir para que el resultado no elija el criterio:

- Si `acc(A) − acc(B) <= 0.01` (dentro del ruido entre seeds): **`z` no aporta**. Se documenta y se
  **propone** la simplificación del contrato.
- Si `acc(A) − acc(B) > 0.01`: `z` aporta y el contrato de 201 se mantiene sin discusión.
- Se reporta además el desglose por clase: es posible que `z` aporte solo en las señas con
  componente de profundidad (movimientos hacia y desde el cuerpo). Ese caso —aporta en pocas clases,
  es irrelevante en el resto— se documenta explícitamente en lugar de resumirse en el promedio.

### Consecuencia constitucional, que es lo importante

El contrato de 201 coordenadas está en el **Principio IV, marcado NO NEGOCIABLE**. Bajarlo a 134
**no es una decisión de esta fase de plan**: requiere el procedimiento de enmienda del artículo
*Governance*, que para los principios NO NEGOCIABLES exige "evidencia empírica que contradiga la
razón original documentada".

Este experimento es exactamente esa evidencia. El plan lo produce; la enmienda, si corresponde, se
tramita aparte con propuesta escrita, aprobación explícita, plan de migración y actualización de
templates. Hasta entonces el contrato es 201, aunque `z` resulte inútil.

### Alternativas descartadas

- **Decidirlo por intuición** ("la profundidad monocular no sirve"). Es probablemente cierto y no es
  evidencia. Con 67 coordenadas en juego, el costo de medirlo es una tarde de cómputo.
- **Ablación con split aleatorio** porque es más rápida. Prohibido por NFR-002; además, con split
  aleatorio la diferencia entre A y B quedaría enterrada bajo los ~9 puntos de inflación conocidos.
- **Ablación por bloques** (quitar solo la `z` de pose, o solo la de manos). Multiplica las corridas
  y las decisiones. Se hace solo si el resultado global cae en la zona ambigua alrededor de 0.01.

---

## R-003 — Runtime de inferencia en el dispositivo

**Estado**: DECIDIDO

### Contexto

FR-002 exige que la clasificación corra íntegramente en el dispositivo, y la spec descarta la
inferencia en servidor. El modelo debe salir de PyTorch y correr en el navegador. Riesgo abierto 3
de la entrada de planificación: **si en el dispositivo corre una versión más liviana, NFR-001a
aplica a la que realmente corre**, no a la que se entrenó.

### Decisión

**`onnxruntime-web` con backend WASM + SIMD + hilos**, con export desde PyTorch a ONNX.

- El modelo es un LSTM de 2 capas, hidden 128, sobre una secuencia de 40 × 201: del orden de
  0,5 M parámetros. En float32 son unos 2 MB. No hay presión que justifique cuantizar ni podar.
- **La estrategia frente al riesgo 3 es no tener el riesgo**: se exporta el **mismo** modelo, sin
  destilación, sin poda y sin cuantización. Si más adelante hiciera falta reducirlo, NFR-001a se
  re-mide sobre la versión reducida y el número reportado es ese.
- **Verificación de paridad obligatoria (Fase C)**:
  1. Paridad numérica: mismos vectores de entrada del fixture → logits de ONNX vs PyTorch con error
     absoluto máximo <= 1e-4.
  2. Paridad de métrica: accuracy del modelo exportado sobre el sujeto 10 held-out, con una
     diferencia respecto de PyTorch <= 0.005. Si la excede, el export está roto y el trabajo se
     detiene ahí.
  3. La métrica que se reporta como NFR-001a es la del **artefacto exportado**.
- El adaptador WebGPU queda como optimización posterior, detrás del mismo puerto `Classifier`
  (AD-05), y solo si el presupuesto de latencia lo pidiera — que según R-008 no lo pide.

### Alternativas descartadas

- **TensorFlow.js**: obligaría a convertir PyTorch → ONNX → TF → TFJS, con una etapa más donde puede
  romperse la paridad de LSTM, o a reimplementar el modelo en Keras y re-entrenar. Más superficie de
  divergencia sin ganancia.
- **Reimplementar el LSTM a mano en TypeScript.** Elimina dependencias y agrega una segunda
  implementación del modelo que hay que mantener numéricamente igual a la de PyTorch, para siempre.
  El proyecto ya tiene una equivalencia de dos implementaciones que sostener (el contrato de
  keypoints); agregar otra sobre el clasificador multiplica el mismo riesgo sin motivo.
- **Cuantización a int8 desde el arranque.** Optimización previa a la medición, sobre un modelo de
  2 MB. Y pondría a NFR-001a a medir algo distinto del baseline sin necesidad.

---

## R-004 — Segmentación temporal: detectar inicio y fin de seña

**Estado**: DECIDIDO (enfoque) + PROCEDIMIENTO (parámetros)

### Contexto

DD-002 incorpora la segmentación temporal completa al MVP: es la deuda XII.2 y **el mayor riesgo del
proyecto**. La fase exploratoria ya encontró que la ventana deslizante de 40 frames crudos produce
confianza inestable, porque no coincide con el entrenamiento (seña completa muestreada a 40 con
linspace). Aparece además un problema nuevo: distinguir señado de movimiento que no es seña
(FR-034), donde un falso positivo produce una traducción inventada a partir de un gesto cualquiera.

### Decisión

Arquitectura en **dos niveles**, con la frontera de AD-04: el segmentador propone, la política de
confianza dispone.

**Nivel 1 — detector de actividad de señado (barato, sin datos nuevos)**

Sobre los keypoints ya extraídos, señal de actividad `a(t)` a partir de la velocidad de muñecas y
codos normalizada por la distancia entre hombros (invariante a escala y distancia a la cámara):

```text
a(t) = || v_muñeca_izq(t) ||  +  || v_muñeca_der(t) ||   (normalizado por ancho de hombros)
```

Con:

- **Histéresis**: umbral de entrada `θ_on` mayor que el de salida `θ_off`, para no oscilar en el
  borde.
- **Duración mínima de seña** y **duración máxima**: fuera del rango, el evento se descarta en lugar
  de clasificarse.
- **Silencio de confirmación de fin**: `a(t) < θ_off` sostenido durante `T_off` para declarar el fin.
  `T_off` es el parámetro que domina la latencia percibida (ver R-008) y por eso es un parámetro del
  contrato, no una constante escondida.
- **Refractario tras un reconocimiento**, que resuelve FR-036 (seña sostenida o repetida no produce
  duplicados en cadena).

**Nivel 2 — rechazo de no-seña (FR-034)**

El nivel 1 detecta *movimiento*, no *señado*: acomodarse el pelo también lo dispara. Sobre cada
evento candidato se aplica un rechazo con dos criterios acumulativos:

1. **Criterio de forma**: el evento debe cumplir el contrato (manos detectadas durante una fracción
   mínima del tramo, torso presente). Si no, se descarta con aviso de encuadre — que es también el
   comportamiento exigido para "manos fuera de cuadro a mitad de la seña" y para la seña bimanual con
   una mano ocluida.
2. **Criterio de confianza**: el propio umbral de la política de confianza. Un gesto que no es seña
   produce una distribución de salida plana, y bajo umbral el sistema calla (FR-017). Esto es lo que
   hace que FR-034 y el Principio VIII sean el mismo mecanismo y no dos.

**Las 3 segmentaciones candidatas (FR-009)** se generan variando los bordes del evento detectado
—`[inicio, fin]`, `[inicio+δ, fin]`, `[inicio, fin−δ]` con δ proporcional a la duración— y se
clasifican **en un solo lote**. No son tres capturas sucesivas ni tres esperas: son tres vistas del
mismo tramo ya grabado. Esto es lo que hace que los 3 intentos casi no consuman presupuesto de
latencia (R-008) y lo que obliga a compensar la confianza (R-005).

### Qué queda como PROCEDIMIENTO

`θ_on`, `θ_off`, `T_off`, duración mínima y máxima y el refractario **no se fijan en este documento**.
Se calibran offline, en la Fase D, sobre grabación anotada por una persona, midiendo directamente
las cuatro categorías de error de NFR-022: no detectada / falso positivo / mal delimitada / mal
clasificada. Fijarlos aquí por intuición sería inventar cinco números más sin evidencia, que es
justamente lo que la spec pasó tres sesiones de clarify corrigiendo.

El calibrado usa la **misma anotación humana de referencia** que sostiene L2 en NFR-003 y la
medición de NFR-022, para no multiplicar conjuntos de referencia.

### Alternativas descartadas

- **Clase 65 "ninguna" en el clasificador.** Elegante, y contamina la comparabilidad de NFR-001a con
  el baseline de 0.85: la accuracy sobre 65 clases no es la accuracy sobre 64. Además requiere
  material propio de no-seña para entrenar, que no existe. Queda como candidata para después de que
  NFR-022 muestre cuánto del error viene de falsos positivos.
- **Segmentador aprendido extremo a extremo (CTC / detección temporal de acciones).** Es el enfoque
  correcto a mediano plazo y necesita datos continuos anotados que el proyecto no tiene; LSA64 es
  versión cut. Adoptarlo ahora convertiría el mayor riesgo del proyecto en una dependencia de
  recolección de datos.
- **Mantener la ventana deslizante de la POC.** Es la causa documentada de la confianza inestable.
  El Principio XII prohíbe agravar la deuda, y sostenerla equivale a eso.

---

## R-005 — Optional stopping: cuantificar la inflación y compensar el umbral

**Estado**: PROCEDIMIENTO

### Contexto

NFR-019 y el supuesto de "riesgo de parada opcional" de la spec: evaluar hasta 3 ventanas distintas
del mismo evento y quedarse con la primera que supera el umbral es **buscar activamente** una ventana
que produzca confianza alta. La confianza aparente sube sin que el sistema haya mejorado, y los
falsos positivos aumentan. Presiona directamente el Principio VIII, que es el principio que sostiene
la promesa central del producto.

### Decisión

**Se mide la inflación y se compensa el umbral con la medición, no con una regla de corrección
teórica.** Módulo `ml/eval/stopping.py`, sobre el conjunto de test **por sujeto** (Principio V).

**Procedimiento**

1. Sobre el sujeto held-out, generar para cada seña las mismas 3 segmentaciones candidatas que
   produce el sistema real (R-004), aplicando el mismo δ.
2. Para cada umbral `θ` de una grilla (0,50 → 0,95 en pasos de 0,01), simular la regla de parada con
   `k = 1`, `k = 2` y `k = 3` intentos y calcular:
   - **Tasa de falsos positivos** `FPR(θ, k)`: eventos aceptados con etiqueta incorrecta sobre el
     total de eventos.
   - **Cobertura** `C(θ, k)`: eventos aceptados sobre el total. Es el costo de subir el umbral y hay
     que reportarlo junto a la FPR: un umbral que no acepta nada tiene FPR cero.
   - **Accuracy condicional a aceptar**: de lo que el sistema muestra, cuánto es correcto. Es el
     número que la persona usuaria experimenta.
3. **Regla de compensación (NFR-019)**: elegir `θ*` tal que
   `FPR(θ*, 3) − FPR(θ_ref, 1) <= 0,02` (2 puntos porcentuales), donde `θ_ref` es el umbral nominal
   del nivel correspondiente. El margen de 2 puntos es provisional (ver R-012).
4. Repetir para los tres niveles de FR-016 (estricto / normal / permisivo). Los valores publicados
   son los **compensados**, y los nominales de FR-016 (0,85 / 0,70 / 0,55) quedan como punto de
   partida documentado, no como valores finales.
5. **La curva completa se publica en el informe de evaluación**, no solo el número elegido: sin la
   curva no se puede auditar si el umbral se eligió antes o después de ver el resultado.

**Dónde se calibra**: los umbrales se eligen sobre el **sujeto de validación (9)** y se **reportan**
sobre el sujeto de test (10). Calibrar sobre test y reportar sobre test convierte a SC-016 en una
tautología. Este punto no está explícito en la spec y es la clase de detalle que invalida una
medición entera.

**Verificación**: SC-016 se comprueba con este mismo módulo, regenerado en cada evaluación del
modelo.

### Alternativas descartadas

- **Corrección tipo Bonferroni sobre el umbral** (dividir α por 3). Supone independencia entre los
  3 intentos, y las 3 segmentaciones del mismo evento están fuertemente correlacionadas: la
  corrección sería demasiado conservadora y destruiría la cobertura sin necesidad. La medición
  directa no necesita ese supuesto.
- **Un solo intento.** Elimina el problema y tira la razón de ser de los 3 intentos: tolerar una
  delimitación imperfecta del segmento, que es exactamente el error que R-004 no puede evitar.
- **Promediar las 3 salidas en lugar de quedarse con la mejor.** Atractivo, y cambia el significado
  de la confianza reportada. Queda registrado como opción a evaluar con la curva del paso 2 en la
  mano: si el promedio da mejor relación FPR/cobertura que el máximo compensado, se adopta y se
  documenta.

---

## R-006 — Tiempo muerto inicial y transiciones ausentes en LSA64 cut

**Estado**: DECIDIDO

### Contexto

Entre el inicio de captura y el comienzo real de la seña hay un tramo sin movimiento que **no existe
en LSA64 versión cut**, que contiene señas ya recortadas. Lo mismo ocurre con las transiciones entre
señas. Es la desalineación de distribución central de DD-002 y el tipo de fallo silencioso contra el
que advierte el Principio IV.

### Decisión

Tres medidas, en este orden de prioridad:

**1. El segmentador recorta; no se recorta después.** El tramo que llega a la normalización temporal
es `[inicio, fin]` detectado por R-004, no la ventana completa desde que se apretó grabar. El tiempo
muerto no se "compensa": no entra. Esto es lo que hace que la segmentación esté en el camino crítico
y no sea un adorno.

**2. Jitter de bordes como augmentation (Fase B).** El segmentador va a delimitar mal a veces; el
modelo debe tolerarlo. Durante el entrenamiento, cada secuencia se toma con bordes desplazados
aleatoriamente en ±15% de su duración (recortando o extendiendo con frames del propio clip cuando
existen), **antes** de la normalización temporal.

Esto no rompe el contrato: el contrato del Principio IV gobierna la forma del vector, la
normalización espacial y el criterio de muestreo temporal — no gobierna qué frames del video entran.
La augmentation opera aguas arriba de S2, sobre la secuencia cruda de largo variable, y su salida
pasa por exactamente las mismas S2 y S3 que el resto. Queda documentado en los metadatos del
experimento (Principio VI) y desactivado en evaluación.

**3. Padding de reposo controlado, solo si el desglose de NFR-022 lo justifica.** Si la categoría
"mal delimitada" domina el error, se agrega como augmentation adicional un prefijo/sufijo de frames
de reposo sintéticos (repetición del primer/último frame con ruido pequeño). Se deja como reserva y
no se aplica de entrada: agrega un supuesto sobre cómo es el reposo real que hoy nadie midió.

**Lo que NO se hace**: recortar por heurística de "primeros N frames" ni descartar un prefijo fijo.
Es el mismo error que el sistema debe evitar —una constante escondida— y además falla justamente en
las señas que empiezan rápido.

### Verificación

El desglose de las cuatro categorías de NFR-022 es lo que dice si esto funcionó: si "mal delimitada"
baja al introducir el jitter, la medida sirvió; si no, el problema está en el segmentador y no en la
tolerancia del modelo. SC-020 exige ese desglose.

---

## R-007 — Umbral de confianza: global o calibrado por clase

**Estado**: PROCEDIMIENTO

### Contexto

Si el desempeño varía fuertemente entre clases, un umbral global único es inadecuado: deja pasar
errores en las clases débiles y calla innecesariamente en las fuertes. La decisión depende de datos
que la evaluación por clase de la Fase B producirá y que hoy no existen.

### Decisión

**Preferencia por el umbral global.** Se abandona solo con evidencia, según un criterio fijado
**antes** de ver los datos:

Sobre el sujeto de validación (9), con el umbral global compensado de R-005:

- **Se mantiene global** si el recall por clase tiene un rango intercuartílico <= 0,15 y ninguna
  clase cae por debajo de 0,50 de recall.
- **Se adopta calibrado por clase** si más de 8 clases (>= 12,5% del vocabulario) quedan por debajo
  de 0,50 de recall con el umbral global, o si el rango intercuartílico supera 0,25.
- **Zona intermedia**: se decide con la relación FPR/cobertura de ambas opciones sobre validación, y
  se documenta la comparación completa en el informe, no solo la opción elegida.

**Restricciones que aplican en cualquiera de los dos casos**:

- La calibración se hace **siempre sobre el sujeto 9 (validación)**. Calibrar 64 umbrales sobre el
  sujeto 10 y reportar sobre el sujeto 10 es sobreajustar al conjunto de test con 64 grados de
  libertad. Es el riesgo específico del umbral por clase y por eso se declara antes.
- Los tres niveles de FR-016 (estricto/normal/permisivo) siguen existiendo: con umbral por clase,
  cada nivel es un **desplazamiento global** sobre el vector de 64 umbrales, no una tabla distinta
  por nivel. La persona usuaria sigue eligiendo entre tres opciones nombradas, no entre 192 números.
- El umbral —global o vector— es un dato del artefacto de modelo (ver [data-model.md](./data-model.md)
  §7), versionado con él. Un modelo nuevo con umbrales viejos es una desalineación silenciosa más.

**El insumo es la distribución de confianza de aciertos vs errores**, no la accuracy por clase: lo
que decide si un umbral separa bien es cuánto se solapan esas dos distribuciones. Si se solapan
fuertemente en todas las clases, ningún umbral —global ni por clase— cumple el Principio VIII, y eso
es un hallazgo de modelo, no de política. Ese caso se reporta como tal.

### Alternativas descartadas

- **Calibración de probabilidades (temperature scaling / Platt) en lugar de umbrales por clase.**
  Es complementaria, no alternativa, y probablemente valga la pena: mejora la interpretabilidad del
  número de confianza que FR-013 muestra. Queda registrada como mejora a evaluar tras la Fase B, con
  la misma restricción de calibrar sobre el sujeto 9.
- **Exponer el umbral por clase en la UI.** Contradice FR-025 (tres opciones nombradas) y convierte
  una decisión de ingeniería en una carga para la persona usuaria.

---

## R-008 — Presupuesto de latencia y dispositivo de referencia

**Estado**: DECIDIDO (desglose) + ABIERTO (dispositivo)

### Contexto

NFR-003 define dos métricas: **L1** (fin de seña detectado por el sistema → texto presentado, < 1 s,
proxy automatizable de CI) y **L2** (último frame real de la seña, anotado a ciegas sobre grabación
externa → texto presentado, < 2 s en el p95 de >= 50 capturas). Riesgo abierto 4 de la entrada de
planificación: verificar que los 3 intentos entran en los 2 s.

### Hallazgo principal

**Los 3 intentos no son el problema.** El desglose muestra que el costo dominante está en otro lado.

La clave es que la extracción de keypoints ocurre **en línea**, frame a frame, mientras la persona
seña. Cuando el segmentador declara el fin, los keypoints del tramo ya están extraídos. Lo que queda
por hacer después del fin detectado es solo:

| Etapa (posterior al fin detectado) | Costo estimado | Nota |
|---|---|---|
| Generar las 3 segmentaciones candidatas | despreciable | Son 3 pares de índices sobre un buffer en memoria |
| S2 normalización temporal × 3 | < 1 ms | 3 × 40 gathers sobre 201 floats |
| S3 normalización espacial × 3 | < 1 ms | 3 × 40 × 201 restas |
| Clasificación de las 3 candidatas | ~10–30 ms | Un solo lote de 3 en `onnxruntime-web`; LSTM de 0,5 M parámetros |
| Política de confianza | despreciable | Comparaciones sobre 3 × 64 valores |
| Render del texto | 1 frame de UI (~16 ms) | |
| **Total ≈ L1** | **~50 ms, holgado bajo 1 s** | |

**L2 = retardo del detector de fin + L1.** El retardo del detector es `T_off`, el silencio de
confirmación de R-004: el sistema no puede declarar el fin hasta observar quietud sostenida.

### Presupuesto declarado

| Componente | Presupuesto | Quién lo controla |
|---|---|---|
| `T_off` — silencio de confirmación de fin | <= 800 ms | Parámetro del segmentador (R-004) |
| L1 — cómputo posterior al fin detectado | <= 1000 ms (NFR-003) | Medido; estimado en ~50 ms |
| Margen de render y jitter del dispositivo | ~200 ms | |
| **L2 total** | **< 2000 ms** (NFR-003, p95) | |

Consecuencias que ordenan el trabajo:

- **La palanca de latencia es `T_off`, no el modelo.** Bajar `T_off` mejora L2 y aumenta las señas
  mal delimitadas (categoría (c) de NFR-022). Ese intercambio es el que hay que calibrar, y se
  calibra con la misma anotación de referencia que sostiene L2 y NFR-022.
- **L1 con presupuesto de 1 s y consumo estimado de ~50 ms tiene margen enorme.** Ese margen es el
  seguro contra un dispositivo más lento de lo previsto y contra un runtime de inferencia peor de lo
  estimado. No se gasta por adelantado.
- **La extracción de keypoints sí tiene un presupuesto propio y distinto**: debe sostener >= 15 fps
  efectivos (NFR-004). Es una restricción de *throughput* continuo, no de latencia posterior al fin,
  y es la más probable de incumplirse en un teléfono de gama media, porque corren dos detectores
  (`HandLandmarker` + `PoseLandmarker`) sobre cada frame. Si no se sostiene, el camino es bajar la
  resolución de entrada al detector, no recortar el presupuesto de L1.

### Dispositivo de referencia — ABIERTO

NFR-003 exige declararlo **al iniciar la fase de plan**, y no puede decidirse desde el repositorio:
depende de qué hardware tiene disponible el equipo. Lo que este documento fija es el procedimiento:

1. Declarar modelo concreto de teléfono de gama media (últimos 4 años) y de notebook, con su
   resolución y fps efectivos medidos, no nominales.
2. Incorporarlos a la lista de >= 3 dispositivos de NFR-020.
3. Primera medición de L2 en cuanto US1 esté operativa, sobre >= 50 capturas, con el último frame
   anotado a ciegas por una persona competente en LSA sobre **grabación con un dispositivo externo a
   la aplicación** (NFR-017(c) prohíbe que la aplicación registre video, y esa prohibición no se
   relaja para medir).
4. **Criterio de revisión (NFR-003)**: si no alcanza los 2 s, reportar el percentil real y decidir
   explícitamente entre optimizar `T_off`, subir el presupuesto con justificación bajo el
   Principio IX, o declarar otro dispositivo de referencia. Nunca dejar el número incumplido y sin
   decisión.

Hasta que se declare, rige el valor provisional de la spec (gama media de los últimos 4 años).

### Nota sobre NFR-023 (frase hablada)

Presupuesto separado de 3 s, dominado por la ida y vuelta al servicio de pulido sobre 4G urbano
(RTT 50–150 ms) más la generación del LLM. No comparte presupuesto con L2: el texto ya se mostró.
Ver R-011.

---

## R-009 — Criterio exacto de normalización temporal

**Estado**: DECIDIDO

### Contexto

La normalización temporal es la fuente conocida de desalineación entre entrenamiento e inferencia
(`Claude.md`: "la ventana deslizante de 40 frames crudos no coincide con el entrenamiento"). El
cliente en vivo debe usar **exactamente** el mismo criterio que el entrenamiento, y eso debe estar
cubierto por un test.

### Decisión

**Índices por aritmética entera exacta**, no por `linspace` de punto flotante:

```text
Entrada:  secuencia de T frames, T >= 1
Salida:   secuencia de N frames, N = SEQ_LEN (parámetro del contrato, valor inicial 40)

si N == 1:  idx[0] = 0
si T == 1:  idx[i] = 0            para todo i
si no:      idx[i] = (i * (T - 1)) // (N - 1)     para i = 0 .. N-1
                     ^ división entera, truncando

salida[i] = entrada[idx[i]]
```

Propiedades, todas verificables:

- `idx[0] = 0` y `idx[N-1] = T-1` siempre: la secuencia normalizada conserva el primer y el último
  frame de la seña.
- **`T > N`**: submuestreo. Se descartan frames intermedios; ninguno se interpola.
- **`T < N`**: sobremuestreo por repetición de frames. No hay relleno con ceros ni con el último
  frame: la duración se estira repitiendo, que es lo que la POC hacía de hecho y lo que preserva la
  forma de la trayectoria.
- **`T == N`**: identidad. `idx[i] = i`. Es un test obligatorio porque es el caso que "obviamente
  funciona" y el que una implementación con `round()` puede romper.
- **`T == 0`**: entrada inválida. Se rechaza con error explícito; no se produce un vector de ceros.
  Un vector de ceros es una secuencia perfectamente válida en forma y sin significado, y el
  clasificador le asignará alguna clase.

### Por qué aritmética entera y no `np.linspace`

`np.linspace(0, T-1, N).astype(int)` **trunca** hacia cero, y su resultado depende de la
representación en punto flotante de `i * (T-1) / (N-1)`. Reproducir esa semántica en JavaScript
—otro motor, otro orden de operaciones— es frágil: un error de un ULP en un valor que cae justo
sobre un entero cambia el índice en 1, y con él un frame completo de la secuencia. El vector sigue
teniendo 201 coordenadas y forma correcta; simplemente es de otro instante. Es el fallo silencioso
del Principio IV en su forma más pura.

La forma entera `(i * (T-1)) // (N-1)` es exacta en ambos lenguajes para todo `T` y `N` del rango de
trabajo (`T` de decenas a cientos, `N = 40`), sin representación intermedia en punto flotante.

**Costo asumido**: difiere de la versión de la POC para algunos valores de `T`, lo que obliga a
regenerar el dataset. Es un costo nulo en la práctica, porque la migración a Tasks API (R-001) ya lo
obliga.

### Parámetros del contrato

`SEQ_LEN` **no está escrito en ningún módulo**: vive en `contracts/keypoints/kp-contract.json` y lo
leen ambas implementaciones. El valor inicial es 40 (heredado del baseline, `FRAMES_FIJOS=40`).
Cambiarlo es un cambio de versión de contrato y obliga a regenerar el dataset y re-entrenar.

### Tests obligatorios (aislados, sin video ni modelo)

`T > N`, `T < N`, `T == N`, `T == 1`, `T == 0` (rechazo), y comparación de los índices producidos
contra una **tabla congelada** de `(T, N) → idx[]` versionada en los fixtures. Comparar contra otra
implementación en vez de contra la tabla permitiría que las dos se equivoquen igual.

---

## R-010 — TTS con Web Speech API: selección de voz, cola y degradación

**Estado**: DECIDIDO

### Contexto

FR-012 fija el orden de preferencia de voz; US2 escenario 5 exige informar y seguir con texto si no
hay ninguna. FR-038 agrupa la voz por pausa. El módulo de voz debe ser reemplazable: Web Speech API
es la implementación del MVP, no un acoplamiento permanente (AD-06).

### Decisión

**Selección de voz** — cadena de FR-012 sobre `speechSynthesis.getVoices()`, comparando la etiqueta
`lang` que declara la plataforma:

1. `es-AR` · 2. `es-UY` (otra variante rioplatense declarada) · 3. cualquier `es-*` · 4. ninguna →
   FR-012 no aplica, se informa explícitamente y el sistema continúa **solo con texto**, sin
   bloquear el flujo.

Detalles operativos que la implementación debe respetar y que son fuente conocida de errores:

- `getVoices()` puede devolver una lista **vacía** en la primera llamada; hay que escuchar
  `voiceschanged` y reintentar. Una implementación que consulta una sola vez al arrancar reporta
  "sin voz en español" en dispositivos que sí la tienen.
- La ausencia total de `window.speechSynthesis` (navegador sin soporte) recorre **el mismo camino**
  que "sin voz en español": mismo mensaje, mismo estado, sin excepción no capturada. Son dos causas
  y una sola experiencia.
- La voz seleccionada es una **preferencia persistente** (FR-024, FR-027) y se revalida al arrancar:
  una voz guardada puede no existir en otro navegador del mismo dispositivo.

**Cola y no solapamiento (FR-038)**

La voz se emite **por bloque acumulado en la pausa**, nunca por seña. El puerto de voz mantiene una
cola de un solo elemento con esta política:

- Si llega un bloque nuevo mientras se está pronunciando el anterior, **se encola**; no se solapa ni
  se descarta. Dos locuciones superpuestas son ininteligibles, y descartar la nueva pierde lo que la
  persona acaba de señar.
- Si la cola ya tiene un bloque pendiente y llega otro, se **fusionan** en un solo enunciado, hasta
  el máximo de 5 glosas de FR-038; el excedente abre un bloque nuevo.
- El descarte de un reconocimiento (FR-019) **cancela** la reproducción en curso si ese
  reconocimiento forma parte del bloque que se está pronunciando (`speechSynthesis.cancel()`).
- Detener la grabación vacía la cola.

**Comportamiento bajo umbral** — esto es lo que FR-017 y el Principio VIII exigen, y conviene que
esté escrito sin ambigüedad:

| Situación | Texto | Voz |
|---|---|---|
| Reconocimiento sobre umbral | Palabra en grande + categoría de confianza (alta / media) | Entra al bloque; se pronuncia en la pausa |
| Ningún intento supera el umbral | Estado **"no entendí"**, sin etiqueta candidata y sin valor numérico | **No se pronuncia nada.** Ni la etiqueta, ni "no entendí" |
| Evento descartado por encuadre | Aviso de encuadre con la acción correctiva | No se pronuncia |
| Pulido no disponible (sin red, servicio caído, presupuesto excedido) | Glosa cruda + aviso de que la frase no pudo componerse | Se pronuncia la **glosa cruda** (FR-031) |

La decisión de **no pronunciar el "no entendí"** merece explicitarse: la voz está dirigida al
interlocutor oyente, que puede no estar mirando la pantalla. Pronunciar "no entendí" por cada evento
fallido llenaría la conversación de interrupciones sin contenido y, en una sesión con mala detección,
sería un ruido continuo. La incertidumbre se comunica **visualmente**, que es el canal de la persona
señante, y el interlocutor la percibe por la ausencia de palabra más la señal audible de estado de
FR-032. NFR-010 se cumple porque ningún estado depende únicamente de sonido — aquí el estado depende
únicamente de imagen, que es lo que ese requisito protege.

**Reemplazabilidad (AD-06)**

`SpeechPort { speak(text, opts): Promise<void>; cancel(): void; listVoices(): VoiceInfo[]; }`.
`WebSpeechAdapter` es el adaptador del MVP. La política de cola, la degradación y el comportamiento
bajo umbral viven **en el puerto**, no en el adaptador: cambiar de proveedor de TTS no debe poder
cambiar qué se pronuncia.

---

## R-011 — Servicio de pulido: modelo, despliegue y validación

**Estado**: ABIERTO (modelo y despliegue) + DECIDIDO (validación y degradación)

### Contexto

DD-003 mueve el pulido glosa→frase a un servidor remoto propio. DD-004 restringe la salida a
palabras de clase cerrada. NFR-023 le da 3 s de presupuesto sobre 4G urbano. NFR-024/026/027 fijan
sus propiedades de privacidad y operación. La spec declara "dónde se despliega el servicio de
pulido" como decisión diferida a esta fase, y **sigue abierta**: depende de infraestructura que el
repositorio no conoce.

### Decidido: validación de trazabilidad por lema (FR-040)

**Tabla curada, no lematizador estadístico.** Con 64 glosas conocidas de antemano:

- `contracts/` (o un recurso del servicio, versionado con el vocabulario) contiene, por cada glosa,
  su lema y su conjunto de flexiones admitidas.
- Una **lista blanca de palabras funcionales**: preposiciones, artículos, conjunciones, pronombres,
  auxiliares.
- **Regla**: todo token de la frase generada debe estar en la lista blanca o pertenecer al conjunto
  de flexiones de una glosa presente en la entrada. Cualquier otro token es una palabra de contenido
  no trazable → **se descarta la frase completa y se pronuncia la glosa cruda**.

Es determinista, auditable, testeable con las 50 secuencias de SC-021 y no agrega una dependencia de
NLP. Un lematizador estadístico introduciría su propia tasa de error justo en el mecanismo que
protege contra poner palabras en boca de una persona sorda.

**Se valida dos veces** (AD-07): en el servicio, y en el cliente antes de pronunciar. El cliente no
puede confiar en que el servicio esté sano.

### Decidido: degradación

El cliente **nunca espera** al servicio más allá del presupuesto de NFR-023. Vencido el plazo, o
ante error, o ante rechazo por límite de uso, o sin red: pronuncia la glosa cruda y avisa que la
frase no pudo componerse. **Sin reintentos en bucle** y sin exponer el detalle técnico a la persona
usuaria (SC-026). El presupuesto es un límite de espera, no una aspiración.

### Decidido: superficie del servicio

Endpoint único, sin cuentas ni autenticación de usuario, con las cuatro defensas de NFR-026: límite
de peticiones por origen (30/min inicial), tamaño máximo de 5 glosas, validación contra el
vocabulario cerrado, y rechazo sin procesar de todo lo demás. La validación contra vocabulario
cerrado es la defensa central: **vuelve al endpoint inútil como LLM de propósito general**, que es
más barato y más efectivo que administrar claves que de todos modos habría que embarcar en el
cliente. Contrato completo en [contracts/polish-service.md](./contracts/polish-service.md).

Sin persistencia de ningún tipo (NFR-024). Métricas agregadas sin contenido (NFR-027): cantidad de
peticiones, distribución de latencia, tasa de error, tasa de rechazo por límite y por vocabulario,
disponibilidad. Nunca la glosa, nunca la respuesta, nunca IP asociada a contenido, y sin registros
por petición que puedan reconstruir contenido cruzándolos.

### Abierto: modelo y despliegue

Requiere infraestructura y presupuesto que el repositorio no declara. El procedimiento:

1. Declarar dónde se despliega (VPS propio, servicio administrado, GPU del laboratorio) y con qué
   modelo. La spec descarta explícitamente delegarlo a una API comercial de terceros.
2. **Primera medición de NFR-023** con el servicio desplegado, sobre 4G urbano con RTT registrado.
3. **Criterio de revisión (NFR-023)**: si no se alcanzan los 3 s, decidir explícitamente entre un
   modelo más chico, pronunciar siempre la glosa cruda, o subir el presupuesto con justificación
   bajo el Principio IX.

Observación que conviene registrar ahora: la tarea real —insertar palabras funcionales y conjugar,
sobre entradas de a lo sumo 5 glosas de un vocabulario de 64— es **mucho más chica** que lo que un
modelo de 7–8B está dimensionado para hacer. La restricción de DD-004 acota tanto el espacio de
salida que un modelo pequeño, o incluso un generador basado en plantillas con concordancia, podría
cumplir SC-021 y SC-022 con latencia muy inferior. Se deja anotado como alternativa a evaluar en la
Fase E antes de comprometer infraestructura de GPU; no se decide aquí porque la calidad de la frase
resultante no se puede juzgar sin probarla con personas.

---

## R-012 — Valores provisionales: criterio y momento de revisión

**Estado**: PROCEDIMIENTO

La spec declara que todos sus números fijados sin evidencia llevan criterio y momento de revisión.
Esta tabla los consolida para que ninguno llegue al cierre sin revisar (riesgo abierto 5 de la
entrada de planificación).

| Valor provisional | Requisito | Momento de revisión | Criterio de revisión | Fase |
|---|---|---|---|---|
| Umbrales 0,85 / 0,70 / 0,55 | FR-016 | Tras la medición de NFR-019 | Se reemplazan por los umbrales compensados de R-005; se documenta la curva completa | D |
| Margen de 2 puntos de FPR | NFR-019 | Primera medición de la curva | Si con 2 puntos la cobertura cae por debajo de un nivel usable, se ajusta el margen documentando el intercambio | D |
| 0,70 de NFR-005 (robustez por entorno) | NFR-005 | Tras la **primera corrida completa** del protocolo en E1/E2/E3, en cuanto US1 y US3 estén completas | E3 entre 0,55 y 0,70 → se reajusta el umbral **por entorno**, documentando valor y evidencia. E3 < 0,55 → fallo de robustez, se revisa el enfoque, no el umbral | F |
| 0,70 de NFR-001b (sistema desplegado) | NFR-001b | Junto con NFR-005 | Mismo número y mismo significado que NFR-005 y que la puerta de NFR-022; incumplirlo **es** el evento que dispara el repliegue a FR-037 | D/F |
| Dispositivo de referencia | NFR-003 | Al declararse, antes de la primera medición de L2 | Ver R-008 | D |
| Umbral de L1 (< 1 s) | NFR-003 | Con la primera medición de L2 | Se deriva del margen real dentro de los 2 s de L2 | D |
| Rangos de lux de E1/E2/E3 | NFR-004 | Junto con NFR-005, tras la primera corrida | Si los rangos no se pueden sostener en campo, se redeclaran con los valores efectivos registrados | F |
| 3 s y RTT 50–150 ms | NFR-023 | Primera medición con el servicio desplegado | Modelo más chico, glosa cruda siempre, o subir presupuesto con justificación | E |
| Pausa de 1,5 s y máximo 5 señas | FR-038 | Rondas de NFR-021 | Se calibra observando con qué frecuencia corta enunciados al medio | E/F |
| 30 peticiones/min por origen | NFR-026 | Primera ventana de evaluación | Holgado frente a una conversación real; se ajusta con el tráfico observado | E |
| `θ_on`, `θ_off`, `T_off`, duraciones | R-004 | Calibración offline de la Fase D | Contra la anotación humana de referencia, minimizando las categorías (a) y (c) de NFR-022 sin exceder `T_off <= 800 ms` | D |
| Cota del Nivel 2 del contrato | NFR-014 | Primera corrida de la Fase C | Se **mide**, nunca se asume | C |

**Regla de cierre**: ningún valor de esta tabla puede llegar a la ronda sumativa de NFR-021 sin
haber pasado por su revisión o sin una nota escrita de por qué se mantiene. Un número provisional
que sobrevive sin revisión deja de ser provisional por omisión, que es el modo en que estas cosas se
vuelven definitivas.

---

## R-013 — Pares de señas confundidos y suficiencia de la configuración de mano

**Estado**: PROCEDIMIENTO — se completa con los resultados de la Fase B

### Contexto

La evaluación por clase no es solo diagnóstico: alimenta la política de confianza (R-007) y la
priorización del trabajo de modelo. Los pares sistemáticamente confundidos son las hipótesis de
mejora.

### Qué produce el módulo de evaluación, en cada evaluación

Definido en el plan (Fase B) y en [data-model.md](./data-model.md) §8:

1. Accuracy global con split por sujeto.
2. Matriz de confusión completa 64 × 64 sobre el conjunto de test.
3. Precisión y recall por clase, para las 64 señas.
4. **Listado de los pares más confundidos**, ordenado por `conf(i→j) + conf(j→i)`.
5. Distribución de confianza para predicciones correctas e incorrectas, global y por clase.

No es un análisis puntual: se **regenera en cada evaluación** y se versiona con el artefacto del
modelo. Un análisis de confusión de hace tres modelos describe otro sistema.

### Hipótesis a documentar cuando existan los datos

Para cada par sistemáticamente confundido, se clasifica la causa en una de tres, y esa clasificación
determina el trabajo:

| Causa | Evidencia que la sostiene | Trabajo que dispara |
|---|---|---|
| **Trayectoria compartida, configuración de mano distinta** | Ambas señas tienen trayectorias de muñeca similares y difieren en la posición relativa de los dedos | Verificar si los 21 landmarks capturan la diferencia: comparar la distancia entre clases en el subespacio de landmarks de mano vs el de pose. Si la diferencia existe en los datos y el modelo no la usa, es un problema de modelo, no de keypoints |
| **Los keypoints no capturan la diferencia** | Las señas son casi indistinguibles en el espacio de 201 coordenadas (distancia entre centroides por clase comparable a la varianza intra-clase) | Es un límite de la representación. Alternativas a documentar: detector de mano de mayor resolución, recorte y re-detección de la ROI de la mano, o declarar el par como confundible y exigir que quede bajo umbral (comportamiento ya especificado en el edge case "señas visualmente similares") |
| **Datos insuficientes o desbalance** | El par confunde en pocos sujetos, o una de las dos clases tiene recall bajo en general | Augmentation dirigido a esas clases; más repeticiones si hubiera material |

**Restricción**: si el par no se puede separar, el comportamiento correcto está ya especificado —
ambas quedan bajo umbral y el sistema dice "no entendí"— y **no** se resuelve arbitrariamente hacia
una de las dos. Eso lo garantiza la política de confianza, no el modelo.

**Interacción con la muestra de campo**: la spec advierte que el sorteo con semilla de NFR-018 puede
dejar afuera los pares confundibles más interesantes, y que ese análisis se hace **por separado**
sobre la matriz de confusión del sujeto held-out, sin tocar la muestra de campo. Se respeta: los
hallazgos de R-013 no modifican el subconjunto congelado de 10 señas.

---

## R-014 — Equivalencia numérica Python ↔ TypeScript

**Estado**: DECIDIDO

### Contexto

NFR-014 exige verificación en dos niveles y explica por qué: MediaPipe en Python y en el navegador
**no producen los mismos landmarks** a partir del mismo video, porque son implementaciones
distintas. Exigir igualdad extremo a extremo llevaría a relajar la tolerancia hasta que el test
dejara de detectar nada.

### Decisión

**Nivel 1 — transformación (BLOQUEANTE de CI)**

Se testea la parte **determinista** del pipeline: dados los mismos landmarks crudos de entrada,
ambos productores deben producir el mismo vector de 201 coordenadas.

| Aspecto | Definición |
|---|---|
| Entrada | Fixtures de landmarks crudos, `contracts/keypoints/fixtures/raw/` |
| Salida esperada | Vectores congelados, `contracts/keypoints/fixtures/expected/` |
| Cobertura | Las 64 clases al menos una vez; además largos `T` que ejerciten `T>N`, `T<N`, `T==N`, y casos de mano ausente |
| Tolerancia | Error absoluto máximo por coordenada **<= 1e-6** |
| Productores obligados | Preprocesamiento Python de entrenamiento y cliente de la aplicación |
| Generación de los fixtures | Por el preprocesamiento de referencia, y **congelados** con hash en `MANIFEST.json` |

**Prueba negativa obligatoria**: el runner debe **fallar** ante una alteración deliberada del
contrato (por ejemplo, centrar también la `z`, o usar `round` en vez de división entera). Sin esa
prueba, un test verde no distingue "las dos implementaciones coinciden" de "el runner no está
comparando nada". SC-017 la exige explícitamente.

**Nivel 2 — extremo a extremo (informativo, no bloqueante)**

Mismo video por ambos caminos completos, incluida la detección. Se verifica dimensión, orden de
landmarks y **ausencia de desalineación estructural** (por ejemplo, manos intercambiadas por
handedness, o pose desplazada un índice). La cota de diferencia por coordenada se **mide**
empíricamente en la primera corrida y se registra; nunca se asume. Superarla en corridas posteriores
no rompe el build pero abre una investigación.

`backend/devinfer` existe en parte para sostener esta medición: permite procesar el mismo video por
el camino Python y por el camino del navegador y comparar los vectores resultantes en un mismo lugar.

### Alternativas descartadas

- **Tolerancia relativa en vez de absoluta.** Las coordenadas están normalizadas y centradas, de modo
  que muchas quedan cerca de cero; una tolerancia relativa sería vacía justo donde más importa.
- **Comparar las dos implementaciones entre sí sin fixture congelado.** Permite que ambas deriven
  juntas y el test siga verde. El fixture congelado es lo que ancla el contrato en el tiempo.
- **Un solo productor compilado a WASM.** Ver AD-01 en [plan.md](./plan.md).

---

## R-015 — Antecedentes de viabilidad de inferencia local

**Estado**: ABIERTO — pendiente de referencia verificable

La entrada de planificación afirma que existe un antecedente público de un traductor de lengua de
señas con arquitectura equivalente (MediaPipe + LSTM) desplegado en hardware embebido de bajo
consumo, y pide documentarlo como evidencia de viabilidad de la inferencia local.

**No se registra la cita aquí porque no se verificó ninguna referencia concreta en esta fase.**
Anotar un antecedente sin fuente sería exactamente lo que el Principio VI prohíbe para los
experimentos propios: un número o una afirmación sin procedencia no es citable.

Queda como tarea: aportar la referencia (paper, repositorio o publicación técnica) con autores,
año, hardware y métricas reportadas, y registrarla en esta entrada.

**Lo que sí sostiene la viabilidad, con evidencia propia**, y no depende de esa referencia:

- El modelo tiene ~0,5 M parámetros y ~2 MB en float32 (R-003).
- El desglose de latencia de R-008 estima ~50 ms de cómputo posterior al fin de seña detectado,
  contra un presupuesto de 1000 ms.
- El **Exp 5** de la fase exploratoria ya confirmó la transferencia laboratorio→webcam con una demo
  en vivo (`Claude.md`), aunque con inferencia en servidor local.

La restricción real no es la clasificación sino la **extracción de keypoints a >= 15 fps con dos
detectores de MediaPipe corriendo sobre cada frame** en un teléfono de gama media. Esa es la
medición que decide la viabilidad, y está en el plan (Fase D, junto con la primera medición de L2).
Un antecedente externo, por bueno que sea, no la sustituye.
