# Contrato de keypoints

**Versión**: `2.0.0` | **Estado**: NORMATIVO — NO NEGOCIABLE (Principio IV)

**Fuente de verdad declarativa**: `contracts/keypoints/kp-contract.json` +
`contracts/keypoints/landmark-map.json` (raíz del repositorio). Este documento explica y justifica;
ante discrepancia gana el archivo declarativo.

**Productores obligados** (NFR-014): preprocesamiento Python de entrenamiento (`ml/`) y cliente de
la aplicación (`frontend/`). Todo productor futuro queda obligado por el mismo contrato.

---

## 1. El vector

```text
FrameKeypoints = float32[201]

[  0 ..  62]   mano izquierda   21 landmarks × (x, y, z)   = 63
[ 63 .. 125]   mano derecha     21 landmarks × (x, y, z)   = 63
[126 .. 200]   pose 0..24       25 landmarks × (x, y, z)   = 75
                                                    Total  = 201
```

Dentro de cada bloque, los landmarks van en orden ascendente de índice y cada uno aporta sus tres
componentes contiguas en el orden `x, y, z`.

```text
SecuenciaKeypoints = float32[SEQ_LEN, 201]
```

`SEQ_LEN` es un **parámetro del contrato** (`kp-contract.json`), valor inicial **40**. Ningún módulo
puede tener ese número escrito: ambas implementaciones lo leen del archivo. Cambiarlo es un cambio
MAJOR de contrato y obliga a regenerar el dataset y re-entrenar.

---

## 2. Origen de los landmarks (MediaPipe Tasks API)

### 2.1 Manos — `HandLandmarker`

- `numHands = 2`, landmarks normalizados, 21 por mano, topología estándar de MediaPipe.
- La asignación a las ranuras `mano_izquierda` / `mano_derecha` se hace por la clasificación de
  **`handedness`** que devuelve el detector, según la correspondencia declarada en
  `landmark-map.json`.
- **Colisión de handedness**: si se detectan dos manos con la misma etiqueta, se conserva la de
  mayor score en su ranura y la otra se descarta; la ranura restante se trata como ausente. Es
  preferible perder una mano a colocarla en la ranura equivocada.
- **La imagen que recibe el detector NUNCA va espejada.** El modo espejo de FR-026 es una
  transformación de presentación aplicada al elemento de video. Espejar la entrada del detector
  invierte `handedness` y por lo tanto intercambia las dos mitades de 63 valores del vector: el
  resultado tiene forma correcta y contenido absurdo. **Test de contrato obligatorio.**

### 2.2 Pose — `PoseLandmarker`

- `numPoses = 1`, topología BlazePose GHUM (33 landmarks).
- Se toman los índices **0..24** y se descartan 25..32 (piernas).
- Los índices 0..24 son equivalentes uno a uno con los de MediaPipe Holistic, incluidos los hombros
  **11** (izquierdo) y **12** (derecho), que definen el centro de la normalización espacial.

### 2.3 Mano ausente

Cuando una mano no se detecta, su ranura de 63 valores se rellena con **ceros**, aplicados
**después** de la normalización espacial (§4): la etapa espacial no toca las manos ausentes.

Ambigüedad conocida y declarada: un relleno de ceros es formalmente indistinguible de una mano cuyos
21 landmarks estuvieran todos exactamente en el punto medio de los hombros. En la práctica no
ocurre. Resolverla requeriría un canal de máscara de presencia, lo que **rompe el contrato de 201**
y exige enmienda al Principio IV. Ver [research.md R-001](../research.md).

La presencia por frame se conserva **fuera** del vector, como metadato de S1
([data-model.md §3](../data-model.md)), y no llega al modelo.

---

## 3. Normalización temporal (S2)

Convierte una secuencia de `T` frames en una de `SEQ_LEN` frames por **selección de índices**, sin
interpolación.

```text
Entrada: T frames, T >= 1
Salida:  N frames, N = SEQ_LEN

si N == 1:  idx[0] = 0
si T == 1:  idx[i] = 0                              para i = 0..N-1
si no:      idx[i] = (i * (T - 1)) DIV (N - 1)      para i = 0..N-1
            # DIV = división entera truncada; aritmética entera exacta, sin punto flotante

salida[i] = entrada[idx[i]]
```

### Reglas

| Caso | Comportamiento |
|---|---|
| `T > N` | Submuestreo. Se descartan frames intermedios; ninguno se interpola |
| `T < N` | Sobremuestreo por **repetición** de frames. Sin relleno de ceros, sin padding |
| `T == N` | Identidad: `idx[i] = i` |
| `T == 1` | Todos los índices son 0 |
| `T == 0` | **Entrada inválida.** Se rechaza con error explícito. NUNCA se produce un vector de ceros |

**Invariantes**: `idx[0] == 0` y `idx[N-1] == T-1`. El primer y el último frame de la seña siempre
se conservan.

### Por qué aritmética entera

`np.linspace(0, T-1, N).astype(int)` depende de la representación en punto flotante. Un error de un
ULP en un valor que cae sobre un entero cambia el índice en 1 — y con él, un frame completo de la
secuencia. El vector sigue teniendo 201 coordenadas y forma correcta; simplemente corresponde a otro
instante. Es el fallo silencioso del Principio IV en su forma más pura, y no es reproducible entre
Python y JavaScript de manera confiable.

La forma entera es exacta en ambos lenguajes para todo `T` del rango de trabajo. Ver
[research.md R-009](../research.md).

### Tests obligatorios (aislados, sin video ni modelo)

`T > N`, `T < N`, `T == N`, `T == 1`, `T == 0` (rechazo), y comparación de los índices producidos
contra una **tabla congelada** `(T, N) → idx[]` versionada en los fixtures. Comparar una
implementación contra la otra, sin tabla, permitiría que ambas se equivoquen igual.

---

## 4. Normalización espacial (S3)

```text
c_x = (pose[11].x + pose[12].x) / 2        # punto medio de los hombros
c_y = (pose[11].y + pose[12].y) / 2

para cada landmark L presente en el frame:
    L.x ← L.x − c_x
    L.y ← L.y − c_y
    L.z ← L.z                              # SIN CENTRAR
```

- Se aplica a los tres bloques (mano izquierda, mano derecha, pose 0..24).
- **La componente `z` no se altera bajo ninguna circunstancia.** Test de contrato obligatorio.
- No hay escalado. El contrato no normaliza por distancia entre hombros ni por ninguna otra medida:
  agregar un escalado sería un cambio MAJOR.
- **Si los landmarks 11 o 12 no están presentes**, el frame no puede normalizarse: el evento se
  descarta con `TORSO_NO_VISIBLE` (FR-006). No se sustituye por un centro por defecto ni se usa el
  centro del frame anterior.

### Orden normativo

**S2 antes que S3.** Muestrear y después centrar. El resultado numérico coincidiría con el orden
inverso, pero el orden se fija porque el cliente en vivo debe ejecutar exactamente la misma cadena y
una divergencia de orden es una divergencia de contrato aunque hoy no se note.

---

## 5. Tolerancia y verificación

### Nivel 1 — transformación (BLOQUEANTE de CI)

Dados los **mismos landmarks crudos**, todo productor debe producir el mismo vector.

| Aspecto | Valor |
|---|---|
| Tolerancia | Error absoluto máximo por coordenada **<= 1e-6** |
| Fixtures | `contracts/keypoints/fixtures/`, congelados con hash en `MANIFEST.json` |
| Cobertura | Las 64 clases al menos una vez; largos `T` que ejerciten `T>N`, `T<N`, `T==N`; casos de mano ausente y de handedness en colisión |
| Productores obligados | `ml/` y `frontend/` |
| Prueba negativa | El runner **debe fallar** ante una alteración deliberada del contrato (centrar `z`, usar `round` en vez de división entera, intercambiar ranuras de manos) |

La tolerancia es **absoluta**, no relativa: las coordenadas están centradas y muchas quedan cerca de
cero, donde una tolerancia relativa sería vacía justo donde más importa.

### Nivel 2 — extremo a extremo (informativo, no bloqueante)

Mismo video procesado por ambos caminos completos, **incluida la detección**. Se verifica dimensión,
orden de landmarks y ausencia de desalineación estructural (manos intercambiadas, pose desplazada un
índice). La cota de diferencia por coordenada se **mide** empíricamente en la primera corrida y se
registra; nunca se asume (NFR-014).

MediaPipe en Python y en el navegador no producen los mismos landmarks a partir del mismo video:
son implementaciones distintas. Exigir igualdad extremo a extremo llevaría a relajar la tolerancia
hasta que el test dejara de detectar nada.

---

## 6. Lo que este contrato prohíbe

- Cambiar la dimensión 201, el orden de los bloques o el orden de landmarks dentro de un bloque.
- Centrar la componente `z`.
- Agregar canales al vector (máscaras de presencia, velocidades, ángulos), sin enmienda al
  Principio IV.
- Escribir `SEQ_LEN`, los índices de los hombros o las dimensiones de los bloques en el código en
  vez de leerlos del archivo declarativo.
- Aplicar filtros de imagen, normalizaciones de color, recortes o aumentos de contraste en la ruta
  que alimenta al clasificador (Principio III).
- Alimentar el detector con la imagen espejada.
- Producir un vector de ceros ante entrada inválida en vez de rechazarla.
