# Contrato de envío: `helpi.evaluacion.sesion` v1.0.0

**Estado**: propuesto (iteración de evaluación, 2026-10-05) · **Dueño del contrato**:
`helpi-android` · **Implementa el receptor**: `helpi-firebase`

Este documento define el intercambio entre el build de **evaluación** de la app y el receptor
que opera el equipo. El mismo documento JSON es también el formato del **archivo exportado**
(FR-009, FR-039): el análisis lo lee igual llegue por red o por archivo.

El límite de 1 MiB aplica al cuerpo HTTP. El esquema no impone topes arbitrarios a la cantidad
de intentos o interrupciones; el archivo local conserva la sesión completa aunque supere ese
límite de transporte.

Archivos normativos (fijados en [`CONTRATO_SHA256`](CONTRATO_SHA256)):

| Archivo | Rol |
|---|---|
| [`envio-sesion-evaluacion.schema.json`](envio-sesion-evaluacion.schema.json) | Estructura y tipos (JSON Schema 2020-12) |
| [`ejemplos/sesion-evaluacion-v1.ejemplo.json`](ejemplos/sesion-evaluacion-v1.ejemplo.json) | Ejemplo de referencia válido: sesión interrumpida con los tres resultados, un descarte, un "lo hice mal", un intento repetido y un video visto |
| [`ejemplos/negativos/*.json`](ejemplos/negativos/) | Cada uno **debe** ser rechazado por el receptor |

Donde este documento y el esquema digan cosas distintas, manda el esquema para la estructura
y este documento para la semántica (§Reglas semánticas). Una discrepancia entre ambos es un
defecto del contrato.

Fuera de alcance: cómo implementa el receptor la autenticación, el almacenamiento, las reglas
de seguridad o los paneles. Este documento solo fija lo que el receptor **acepta**, **responde**
y **garantiza**.

---

## Transporte

| Aspecto | Valor |
|---|---|
| Método y ruta | `POST {urlReceptor}/v1/sesiones-evaluacion` |
| Protocolo | HTTPS, TLS ≥ 1.2. Sin redirecciones: un 3xx es un error transitorio para el cliente |
| Cuerpo HTTP | Un documento JSON UTF-8 sin BOM, ≤ 1 MiB. Sin compresión |
| Tiempo de espera del cliente | 15 s conexión, 30 s lectura |

### Encabezados de la solicitud

| Encabezado | Obligatorio | Valor |
|---|---|---|
| `Content-Type` | Sí | `application/json; charset=utf-8` |
| `X-Helpi-Contrato` | Sí | `helpi.evaluacion.sesion/1.0.0`; debe coincidir con `contrato` del cuerpo |
| `Idempotency-Key` | Sí | Igual a `envio.envioId` |
| `X-Helpi-Clave-Evaluacion` | Sí | Clave del build de evaluación (research R-06). Opaca para este contrato |
| `User-Agent` | Sí | `HelpiEvaluacion/{appVersionName} ({appVersionCode})` |

El cliente **no** envía identificadores de dispositivo, de publicidad ni de cuenta en ningún
encabezado.

---

## Cuerpo

La estructura completa está en el esquema. Resumen de bloques:

| Bloque | Contenido | Trazabilidad |
|---|---|---|
| `contrato` | `nombre`, `version` | FR-039 |
| `envio` | `envioId` (UUID v4, idempotencia), `revision` (≥ 1; sube en cada envío o exportación de la misma sesión), `generadoEn` | FR-030 |
| `sesion` | `sesionId` (UUID v4), `estado`, `creadaEn`, `ultimoIntentoEn`, `intentosPerdidos` | US1, Edge Cases |
| `participante` | `codigo` (`P-NNN`, asignado por el equipo) | FR-015 |
| `consentimiento` | `avisoVersion`, `otorgadoEn` | FR-011, FR-014 |
| `condiciones` | Entorno, iluminación, distancia, mano dominante, guantes, soporte, perfil | FR-008 |
| `versiones` | App, modelo y hashes de modelo y catálogo, contrato de keypoints, protocolo | FR-008, FR-037, CLAUDE.md §3 |
| `dispositivo` | Fabricante, modelo y SDK | FR-008 |
| `protocolo` | Versión, `semilla` (texto), constantes 64/3/4/48 y `secuencia` completa de 192 índices | FR-040, FR-040a, FR-043 |
| `intentos[]` | Uno por intento registrado, en orden de registro, incluidos los reemplazados | FR-006, FR-007, FR-025, FR-042a, FR-044 |
| `interrupciones[]` | Bloque, posición pendiente, causa e instante | Edge Cases |

### Tipos y convenciones

- **Todas las claves son obligatorias.** Lo que no aplica va como `null` explícito, nunca se
  omite. Así el receptor y el análisis no tienen que distinguir "ausente" de "nulo".
- **Claves desconocidas: rechazo** (`additionalProperties: false` en todos los niveles). Es la
  garantía de que nada nuevo viaja sin pasar por una versión del contrato.
- Instantes: RFC 3339 UTC con segundos y `Z` (`2026-10-05T15:20:07Z`). Duraciones: enteros en
  milisegundos.
- Confianzas y umbral: número en [0, 1] con a lo sumo 6 decimales.
- `protocolo.semilla`: entero de 64 bits **como texto decimal**, porque un número JSON pierde
  precisión en JavaScript.
- Índices de clase: 0..63, según el catálogo identificado por `versiones.catalogoSha256`. La
  glosa viaja junto al índice solo para legibilidad: **manda el índice**.

### Lo que el cuerpo nunca contiene

Video, cuadros, keypoints, landmarks, texto de conversación, nombres, contactos, identificadores
de dispositivo, de cuenta o de publicidad, ni ubicación (FR-010, NFR-003). El esquema lo
garantiza estructuralmente (ver `negativos/keypoints-en-intento.json` y
`negativos/campo-adicional.json`).

---

## Reglas semánticas

El receptor **debe** verificarlas además del esquema. Si alguna falla, responde `422`.

| Id | Regla |
|---|---|
| S-01 | `X-Helpi-Contrato` y `Idempotency-Key` coinciden con `contrato` y `envio.envioId` |
| S-02 | `protocolo.secuencia` contiene cada índice 0..63 exactamente 3 veces y ningún par consecutivo igual (FR-041) |
| S-03 | Para cada intento: `senaEsperada.indice == protocolo.secuencia[posicion − 1]` y `bloque == ceil(posicion / 48)` |
| S-04 | Si `resultado != SIN_RESULTADO`: `top3` tiene rangos 1, 2, 3 en orden, con confianzas no crecientes, y `prediccion` es igual a `top3[0]` (índice, glosa y confianza) |
| S-05 | `superoUmbral == (prediccion.confianza >= umbral)`; `resultado == SOBRE_UMBRAL ⇔ superoUmbral` |
| S-06 | `correcto == (resultado == SOBRE_UMBRAL && prediccion.indice == senaEsperada.indice)` |
| S-07 | Por cada `posicion` hay a lo sumo un intento con `reemplazado == false`, y es el de mayor `numeroIntento` |
| S-08 | `descartado ⇒ resultado == SOBRE_UMBRAL` (ya cubierto por el esquema; se repite por claridad) |

El esquema cubre la estructura y parte de estas reglas. Las que dependen de relaciones entre
campos (S-02, S-03, S-04 en el orden de confianzas, S-05, S-06, S-07) solo las puede verificar
el código del receptor. El test de `helpi-android` las verifica sobre lo que serializa.

---

## Respuestas

El receptor responde siempre con `Content-Type: application/json` y uno de estos cuerpos.

### Confirmación de recepción

**Definición**: un envío está **confirmado** si y solo si el receptor responde `200` con:

```json
{ "estado": "RECIBIDO", "envioId": "<igual al de la solicitud>", "sha256": "<hex en minúsculas del cuerpo recibido>" }
```

o `"estado": "DUPLICADO"` con los mismos campos, y además:

1. `envioId` coincide con el enviado, y
2. `sha256` coincide con el SHA-256 de los **bytes exactos** que el cliente guardó en su cola
   (data-model, `envio.sha256`).

El receptor **solo puede responder `200` después de que el documento esté guardado de forma
durable**, es decir, después de que su almacenamiento confirme la escritura. Responder antes
viola el contrato: el cliente borra su copia al recibir la confirmación (FR-030).

Cualquier `200` que no cumpla 1 y 2 se trata como **error transitorio**: el cliente no borra
nada y reintenta.

### Tabla completa

| Código | `estado` | Significado | Cliente |
|---|---|---|---|
| 200 | `RECIBIDO` | Guardado por primera vez | Confirmado: borra el envío local |
| 200 | `DUPLICADO` | Ya existía ese `envioId` con el **mismo** `sha256` | Confirmado: borra el envío local |
| 400 | `JSON_INVALIDO` | No es JSON UTF-8 válido | `RECHAZADO`, sin reintento |
| 401 / 403 | `NO_AUTORIZADO` | Clave ausente, inválida o revocada | `BLOQUEADO`, sin reintento |
| 409 | `CONFLICTO` | Ya existe ese `envioId` con **otro** `sha256` | `RECHAZADO`, sin reintento |
| 413 | `DEMASIADO_GRANDE` | Cuerpo > 1 MiB | `RECHAZADO` |
| 415 | `TIPO_NO_SOPORTADO` | `Content-Type` incorrecto | `RECHAZADO` |
| 422 | `ESQUEMA_INVALIDO` / `SEMANTICA_INVALIDA` / `VERSION_NO_SOPORTADA` | Falla el esquema, una regla S-xx o la versión del contrato | `RECHAZADO` |
| 429 | `LIMITE` | Exceso de solicitudes | Transitorio: reintento con backoff |
| 5xx | `ERROR_INTERNO` | Falla del receptor | Transitorio |
| — | — | Timeout, DNS, conexión, TLS, 3xx | Transitorio |

Cuerpo de error (4xx):

```json
{ "estado": "ESQUEMA_INVALIDO", "envioId": "<si se pudo leer, o null>", "errores": [ { "ruta": "/intentos/3/prediccion", "regla": "S-04", "mensaje": "texto libre" } ] }
```

`errores` es una lista con a lo sumo 20 elementos. `ruta` es un JSON Pointer. El cliente guarda
`estado` en `envio.motivo` y no muestra `mensaje` a la persona participante.

`Retry-After` en 429 o 503 es **informativo**: el cliente usa el backoff de WorkManager
(research R-05) y no lo respeta en v1.

---

## Idempotencia y revisiones

- `envioId` identifica **un envío**: un documento fijo. Reintentar el mismo envío manda los
  mismos bytes con el mismo `envioId`.
- Una sesión puede enviarse varias veces (por ejemplo, al terminar cada bloque). Cada envío
  tiene un `envioId` nuevo y una `revision` mayor.
- El receptor **guarda cada envío** (no sobrescribe) y debe poder entregar, por `sesionId`, el
  de mayor `revision`. Dos envíos con el mismo `sesionId` y la misma `revision` y distinto
  `envioId` se aceptan los dos; el análisis toma el de mayor `envio.generadoEn`.

---

## Obligaciones del receptor

1. Validar el esquema y las reglas S-01 a S-08 antes de guardar. No guardar nada que falle.
2. Confirmar solo después de una escritura durable (§Confirmación).
3. Deduplicar por `envioId` (§Tabla).
4. **No guardar datos de conexión** asociados al documento: ni IP, ni `User-Agent`, ni
   encabezados (FR-032). Los registros de la plataforma que no se puedan desactivar no deben
   vincularse con `sesionId` ni con `participante.codigo`.
5. **Supresión**: poder borrar todos los envíos de un `sesionId` y todos los de un
   `participante.codigo`, a pedido del equipo, sin depender de la app (US7-4, research R-09). El
   plazo se fija en la revisión legal (Ley N.º 25.326) que pide la spec.
6. Entregar al análisis una exportación en bloque con los documentos **tal como se
   recibieron**, sin transformar.
7. La función receptora no calcula métricas sobre el contenido (research R-10). Se permite
   que un componente de análisis separado del receptor y de solo lectura consuma los documentos
   recibidos y calcule únicamente las métricas agregadas definidas en §Reglas para el análisis.
   Este componente no modifica ni elimina los documentos fuente.

---

## Reglas para el análisis

Son normativas para quien consuma estos documentos (FR-033 a FR-038, FR-042a, FR-044).

- **Sujeto** = `participante.codigo`. Todo intervalo de confianza se calcula con bootstrap
  **por participante**. Toda calibración de umbral usa leave-one-subject-out. Nunca se reparten
  intentos de una misma persona entre conjuntos (FR-035, NFR-008).
- Por cada `sesionId` se usa la revisión mayor. Por cada `(sesionId, posicion)`, el intento
  con `reemplazado == false`.
- No se combinan en una cifra documentos con distinto `versiones.modelo`,
  `versiones.catalogoSha256`, `versiones.contratoKeypoints` o `protocolo.version` (FR-037), ni
  intentos con distinto `vioVideo` (FR-042a).
- La exactitud se informa con y sin `SIN_RESULTADO`, y por separado para `loHiceMal == true`
  (Edge Cases, FR-044).
- `descartado` es **error percibido**, no verdad de referencia, y se informa aparte (FR-038).

---

## Versionado

- SemVer sobre `contrato.version`.
  - **PATCH**: aclaraciones de este documento sin cambio de esquema ni de semántica.
  - **MINOR**: no existe en la práctica. Como se rechazan las claves desconocidas, agregar un
    campo rompe a receptores viejos y es **MAJOR**.
  - **MAJOR**: cualquier cambio de esquema o de semántica.
- El receptor declara qué versiones acepta; una no soportada recibe `422 VERSION_NO_SOPORTADA`.
- Durante una transición, el receptor acepta la versión anterior y la nueva. La app envía una
  sola.
- Un cambio de contrato se hace en `helpi-android` (dueño) y se copia a `helpi-firebase` en un
  cambio coordinado, con la misma rama `NNN-` en los dos repositorios (CLAUDE.md §8).

## Sincronización con helpi-firebase

- `helpi-firebase` copia estos archivos **sin modificarlos** y guarda una copia de
  `CONTRATO_SHA256`. Su CI recalcula los hashes y falla si alguno difiere.
- Su CI también debe probar que el receptor:
  - acepta el ejemplo de referencia y responde `200 RECIBIDO` con el `sha256` correcto;
  - responde `200 DUPLICADO` al mismo cuerpo repetido;
  - responde `409` al mismo `envioId` con un cuerpo distinto;
  - rechaza **cada** ejemplo negativo con 4xx.
- En `helpi-android`, el test de contrato (research R-11) prueba que lo que serializa la app
  coincide con el ejemplo de referencia y que el esquema rechaza cada negativo.
