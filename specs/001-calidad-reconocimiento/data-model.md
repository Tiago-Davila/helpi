# Data Model: calidad del reconocimiento — build de evaluación

**Feature**: `001-calidad-reconocimiento` | **Fecha**: 2026-10-05 | **Plan**: [plan.md](plan.md)

Base Room `helpi_evaluacion.db`, que existe **solo** en el módulo `:evaluacion` y, por lo
tanto, solo en el sabor `evaluacion` (research R-01). Producción no tiene base de datos en
esta iteración.

Convenciones:

- `…En` es un instante de reloj de pared en milisegundos UTC (`Long`).
- `…Ms` relativo es una duración medida con `elapsedRealtime`.
- Ninguna tabla guarda nombres, contactos, identificadores del dispositivo, video, cuadros ni
  keypoints (FR-010, NFR-003).

```text
consentimiento (historial, por dispositivo)

participante 1 ──< sesion 1 ── 1 condiciones_prueba
                     │
                     ├──< intento 1 ──< prediccion_top (0..3)
                     ├──< interrupcion
                     └──< envio  (outbox)

estado_depuracion (fila única)
```

---

## consentimiento

Historial **por dispositivo**; no se vincula a ningún participante (FR-011). Solo se agregan
filas; la revocación completa la fila vigente.

| Campo | Tipo | Regla |
|---|---|---|
| `id` | Long PK autoinc | |
| `avisoVersion` | String | Versión común del texto y del video en LSA (FR-012, FR-014) |
| `avisoVideoSha256` | String(64) | Hash del video mostrado; debe coincidir con el asset de esa versión |
| `otorgadoEn` | Long | Momento de la acción explícita de aceptar |
| `revocadoEn` | Long? | Null mientras está vigente |

**Vigente** = la última fila tiene `revocadoEn == null` y `avisoVersion == AVISO_VERSION_ACTUAL`.
Sin consentimiento vigente no se puede iniciar, retomar, exportar ni enviar (research R-09).

---

## participante

| Campo | Tipo | Regla |
|---|---|---|
| `codigo` | String PK | Asignado por el equipo; patrón `^P-[0-9]{3,4}$` (research R-12). Sin datos personales (FR-015) |
| `creadoEn` | Long | |

Se borra solo con la acción de borrado (FR-013a), en cascada con sus sesiones.

---

## sesion

Una sesión es **un participante en un entorno**, con condiciones fijas (FR-040a, FR-040b).

| Campo | Tipo | Regla |
|---|---|---|
| `id` | String PK (UUID v4) | Generado al crear; es el `sesionId` del contrato |
| `participanteCodigo` | String FK → participante | `ON DELETE CASCADE` |
| `consentimientoId` | Long FK → consentimiento | Consentimiento vigente al crear |
| `protocoloVersion` | String | `"1.0.0"` en esta iteración (FR-043) |
| `semilla` | Long | Semilla del generador; se conserva al retomar (FR-040a) |
| `estado` | Enum `EstadoSesion` | Ver transiciones |
| `bloqueActual` | Int 1..4 | Bloque en curso o próximo a retomar |
| `appVersionName` / `appVersionCode` | String / Int | |
| `modeloVersion` | String | `lsa-manifest.json:modelVersion` |
| `modeloSha256` / `catalogoSha256` | String(64) | Modelo y catálogo son una unidad (CLAUDE.md §3) |
| `contratoKeypoints` | Int | `KeypointContract.CONTRACT_VERSION` |
| `dispositivoFabricante` / `dispositivoModelo` | String | `Build.MANUFACTURER` / `Build.MODEL` (FR-008) |
| `sdkAndroid` | Int | `Build.VERSION.SDK_INT` |
| `creadaEn` | Long | |
| `ultimoIntentoEn` | Long? | Último intento registrado. Base de retención si existe y nunca se exportó; si es null, se usa `creadaEn` (NFR-011) |
| `ultimaExportacionEn` | Long? | Envío confirmado **o** archivo compartido (research R-13) |
| `intentosPerdidos` | Int | Cantidad persistida de intentos cuya escritura falló (research R-03); el escritor acumula fallos pendientes en memoria y los suma en la próxima transacción exitosa. Si el proceso termina antes del vaciado, el contador puede subestimar esa ejecución; la posición sin intento persistido se vuelve a ofrecer al retomar. |
| `revision` | Int | Sube en 1 en cada envío o exportación; viaja en el contrato |

### EstadoSesion — transiciones

```text
                 iniciar bloque
   CREADA ─────────────────────────► EN_CURSO ──── 192 intentos ────► COMPLETA
                                     │  ▲   │
                    fin de bloque    │  │   │ background / revocación /
                    (pausa)          ▼  │   │ proceso terminado (al reabrir)
                                  PAUSADA   ▼
                                     │   INTERRUMPIDA
                                     │      │
                      retomar ───────┴──────┘──► EN_CURSO   (mismas condiciones,
                                                            consentimiento vigente)

   CREADA | EN_CURSO | PAUSADA | INTERRUMPIDA ── "Terminar sesión" ──► TERMINADA_ANTES
```

Reglas:

- Como máximo una sesión puede estar `EN_CURSO` en el dispositivo. La transacción que inicia
  un bloque comprueba que no haya otra sesión activa antes de cambiar su estado.
- Al arrancar la app: si hay una `EN_CURSO`, pasa a `INTERRUMPIDA` con causa
  `PROCESO_TERMINADO` (research R-03).
- `COMPLETA` y `TERMINADA_ANTES` son finales: no se retoman. Se exportan igual que las demás.
- Se pueden exportar y enviar sesiones en **cualquier** estado. Las interrumpidas también son
  datos.

---

## condiciones_prueba

1:1 con `sesion`; inmutable después de crear la sesión (FR-008). Todo campo es enumerado.

| Campo | Tipo | Valores |
|---|---|---|
| `sesionId` | String PK, FK → sesion | `ON DELETE CASCADE` |
| `entorno` | Enum | `INTERIOR`, `EXTERIOR` |
| `tipoEntorno` | Enum | `CASA`, `AULA`, `OFICINA`, `CALLE`, `TRANSPORTE`, `OTRO` |
| `iluminacion` | Enum | `BUENA`, `MEDIA`, `BAJA` |
| `contraluz` | Boolean | |
| `distancia` | Enum | `MENOS_DE_1M`, `ENTRE_1_Y_2M`, `MAS_DE_2M` |
| `manoDominante` | Enum | `DIESTRA`, `ZURDA`, `AMBIDIESTRA` |
| `guantes` | Boolean | |
| `soporteCamara` | Enum | `TRIPODE`, `APOYADO`, `EN_MANO` |
| `perfilParticipante` | Enum | `SORDA_SENANTE`, `INTERPRETE_LSA`, `EQUIPO` (US1; lo separa el análisis) |

---

## intento

Un intento del protocolo. Se escribe **completo, en una transacción, apenas se resuelve**
(research R-03). En esta iteración no hay reconocimientos fuera del protocolo (plan.md,
"Diferido").

| Campo | Tipo | Regla |
|---|---|---|
| `id` | Long PK autoinc | |
| `sesionId` | String FK → sesion | `ON DELETE CASCADE`; índice |
| `posicion` | Int 1..192 | Posición en la secuencia del protocolo |
| `bloque` | Int 1..4 | `ceil(posicion / 48)` |
| `numeroIntento` | Int ≥ 1 | Sube con "Repetir intento" en la misma posición |
| `reemplazado` | Boolean | `true` si el intento se repitió después; el análisis usa el último |
| `senaEsperadaIndice` | Int 0..63 | |
| `senaEsperadaGlosa` | String | Glosa del catálogo de la sesión |
| `vioVideo` | Boolean | Se vio el clip de referencia antes del intento (FR-042a) |
| `resultado` | Enum `ResultadoIntento` | `SOBRE_UMBRAL`, `BAJO_UMBRAL`, `SIN_RESULTADO` |
| `causaSinResultado` | Enum? | Solo si `SIN_RESULTADO`: `SIN_HOMBROS`, `SEGMENTO_CORTO`, `SEGMENTO_LARGO`, `SEGUIMIENTO_PERDIDO`, `ORIENTACION_CAMBIO`, `ERROR_MODELO`, `TIEMPO_AGOTADO` |
| `predichoIndice` | Int? 0..63 | Null si `SIN_RESULTADO` |
| `predichoGlosa` | String? | Null si `SIN_RESULTADO` |
| `confianza` | Float? [0,1] | Null si `SIN_RESULTADO` |
| `umbral` | Float [0,1] | Umbral vigente en ese intento |
| `superoUmbral` | Boolean | `confianza >= umbral` |
| `correcto` | Boolean | `resultado == SOBRE_UMBRAL && predichoIndice == senaEsperadaIndice`. "Bajo el umbral" y "sin resultado" cuentan como no correctos (Edge Cases: error de recall) |
| `descartado` | Boolean | "Descartar" durante la ventana de deshacer (FR-025); solo es posible si `SOBRE_UMBRAL` |
| `loHiceMal` | Boolean | Marca de la persona (FR-044) |
| `inicioRelMs` | Long | Desde el inicio de la sesión hasta que se mostró la seña |
| `duracionSegmentoMs` | Long? | Duración del segmento clasificado |

Invariantes (las verifican tests de `:evaluacion-dominio` y de persistencia):

- `SIN_RESULTADO` ⇔ `causaSinResultado != null` ⇔ `predichoIndice == null`; además,
  `predichoGlosa` y `confianza` son null, no hay filas `prediccion_top`, y
  `superoUmbral`, `correcto` y `descartado` son false.
- Para `SOBRE_UMBRAL` y `BAJO_UMBRAL`, `causaSinResultado` es null, hay exactamente tres
  filas `prediccion_top` con rangos 1, 2 y 3, y la de rango 1 coincide con `predichoIndice`,
  `predichoGlosa` y `confianza`. Las confianzas del top-3 no crecen con el rango.
- Para cada posición hay a lo sumo un intento con `reemplazado = false`, y ese intento tiene
  el mayor `numeroIntento` de la posición (contrato S-07).
- `superoUmbral == (confianza >= umbral)` y
  `resultado == SOBRE_UMBRAL ⇔ superoUmbral`.
- `correcto == (resultado == SOBRE_UMBRAL && predichoIndice == senaEsperadaIndice)`.
- `descartado ⇒ resultado == SOBRE_UMBRAL`.
- Índice único `(sesionId, posicion, numeroIntento)`. La regla de S-07 se garantiza en la
  misma transacción que inserta la repetición: marca el anterior `reemplazado = true` antes
  de insertar el nuevo número, porque Room no tiene índices únicos parciales.

`TIEMPO_AGOTADO` es una causa del protocolo, generada por `ProtocoloViewModel` cuando vence
la espera de 15 s; no es una causa emitida por el puerto `RecognitionObserver`.

## prediccion_top

Top-3 de cada intento con resultado (FR-006): se guardan exactamente tres filas, rangos 1 a
3, en la misma transacción que el intento. Para `SIN_RESULTADO` no se guardan filas. Son
predicciones no confirmadas.

| Campo | Tipo | Regla |
|---|---|---|
| `intentoId` | Long FK → intento | PK compuesta con `rango`; `ON DELETE CASCADE` |
| `rango` | Int 1..3 | |
| `indice` | Int 0..63 | |
| `glosa` | String | |
| `confianza` | Float [0,1] | No crece con el rango |

---

## interrupcion

| Campo | Tipo | Regla |
|---|---|---|
| `id` | Long PK autoinc | |
| `sesionId` | String FK → sesion | `ON DELETE CASCADE` |
| `bloque` | Int 1..4 | |
| `posicionPendiente` | Int 1..192 | Primer intento sin respuesta, donde se retoma |
| `causa` | Enum | `SEGUNDO_PLANO`, `PROCESO_TERMINADO`, `REVOCACION`, `PERSONA_PAUSO_DIA` |
| `ocurrioEn` | Long | |

---

## envio (outbox)

Research R-05. Guarda **los bytes exactos** que se enviarán.

| Campo | Tipo | Regla |
|---|---|---|
| `envioId` | String PK (UUID v4) | Clave de idempotencia del contrato |
| `sesionId` | String FK → sesion | `ON DELETE CASCADE` |
| `revision` | Int | `sesion.revision` al crear el envío |
| `payload` | BLOB | JSON UTF-8 del contrato v1 |
| `sha256` | String(64) | De `payload` |
| `estado` | Enum | `PENDIENTE`, `RECHAZADO`, `BLOQUEADO` |
| `motivo` | String? | Código del contrato (`ESQUEMA_INVALIDO`, `CONFLICTO`, …) o error de red |
| `intentos` | Int | Intentos de envío realizados |
| `creadoEn` | Long | Base del vencimiento de 30 días |
| `ultimoIntentoEn` | Long? | |

Ciclo de vida:

```text
"Enviar al equipo" ──► PENDIENTE ── confirmación ──► (fila borrada; sesion.ultimaExportacionEn = ahora)
                          │
                          ├── 400/409/413/415/422 ──► RECHAZADO ──(30 días)──► borrado
                          ├── 401/403 ──────────────► BLOQUEADO ──(30 días)──► borrado
                          ├── transitorio ──► sigue PENDIENTE (retry con backoff)
                          ├── revocación ───► borrado (no salió del dispositivo)
                          └── 30 días sin confirmar ──► borrado; la sesión muestra "envío vencido"
```

Límites: como máximo 20 filas `PENDIENTE`; al llegar al límite, "Enviar" se rechaza con un
mensaje.

---

## estado_depuracion

Fila única para la protección contra saltos de reloj (research R-13).

| Campo | Tipo |
|---|---|
| `id` | Int PK = 1 |
| `ultimaHoraVistaEn` | Long |
| `ultimaDepuracionEn` | Long? |

## Retención de sesiones

- Si `ultimaExportacionEn` no es null, la sesión vence 90 días después de ese instante.
- Si nunca se exportó, vence 180 días después de `ultimoIntentoEn`; si no llegó a registrar
  ningún intento, vence 180 días después de `creadaEn`.
- En una sesión retomada, cada intento registrado actualiza `ultimoIntentoEn`, por lo que la
  retención se cuenta desde el último bloque que dejó intentos persistidos.

---

## Qué se escribe y cuándo

| Momento | Escritura | Hilo |
|---|---|---|
| Aceptar el consentimiento | `consentimiento` | Escritor |
| Revocar | Completa `revocadoEn`, sesión `EN_CURSO` → `INTERRUMPIDA`, más una `interrupcion` y el borrado de los `envio PENDIENTE` (una transacción) | Escritor |
| Crear sesión | `participante` (si es nuevo), `sesion` `CREADA` y `condiciones_prueba` (una transacción) | Escritor |
| Empezar o retomar un bloque | `sesion.estado = EN_CURSO`, `bloqueActual` | Escritor |
| **Intento resuelto** (ventana de deshacer vencida, descartado o "sin resultado") | `intento` + `prediccion_top` + `sesion.ultimoIntentoEn` (una transacción) | Escritor, encolado con `trySend` desde el hilo de reconocimiento |
| "Lo hice mal" sobre el último intento | `intento.loHiceMal = true` | Escritor |
| Fin de bloque o intento 192 | `sesion.estado` = `PAUSADA` / `COMPLETA` | Escritor |
| Segundo plano | `sesion` → `INTERRUMPIDA` + `interrupcion` | Escritor (desde `ON_STOP`) |
| Arranque de la app | `EN_CURSO` → `INTERRUMPIDA` + `interrupcion(PROCESO_TERMINADO)` | Escritor |
| "Enviar al equipo" | `envio` + `sesion.revision++` (una transacción) | Escritor |
| Confirmación de recepción | Borra el `envio` y pone `sesion.ultimaExportacionEn` (una transacción) | `EnvioWorker` |
| Archivo compartido | `sesion.ultimaExportacionEn`, `revision++` | Escritor |
| Depuración diaria | Borrado por retención y `estado_depuracion` | `DepuracionWorker` |

Nada se escribe desde el hilo de reconocimiento ni desde el de cámara (NFR-006).

---

## Correspondencia con la spec (Key Entities)

| Entidad de la spec | Implementación |
|---|---|
| Variante de compilación | Sabores `evaluacion` / `produccion` (research R-01); no es un dato |
| Consentimiento de evaluación | `consentimiento` |
| Participante (seudónimo) | `participante` |
| Protocolo de validación | Generado en `:evaluacion-dominio` a partir de `protocoloVersion` y `semilla`; no se persiste la lista, porque se reconstruye y viaja en la exportación |
| Sesión de evaluación | `sesion` + `condiciones_prueba` |
| Intento de evaluación | `intento` + `prediccion_top` |
| Fila de sesión de producción | **Diferida** (plan.md) |
| Cola de envío | `envio` (en esta iteración transporta sesiones de evaluación, no filas de producción) |
| Reporte de calidad | Fuera de la app (FR-039; research R-10) |
