# Contrato del servicio de pulido glosa→frase

**Versión**: `1.0.0` | **Estado**: Normativo (producción)

Único componente remoto del sistema. Convierte una secuencia de glosas ya reconocidas en una frase
en español. **No reconoce, no clasifica y no infiere señas** (Principio II, FR-039).

**Traza a**: DD-003, DD-004, DD-005, FR-039, FR-040, FR-041, NFR-023, NFR-024, NFR-025, NFR-026,
NFR-027, SC-021, SC-022, SC-025, SC-026.

---

## 1. Frontera de datos

| Dato | ¿Cruza? | Fuente |
|---|---|---|
| Video, frames, miniaturas | **Nunca** | Principio VII, NFR-007 |
| Keypoints | **Nunca** | NFR-006, DD-005 |
| Secuencia de glosas reconocidas | **Sí**, solo hacia este servicio y solo con el pulido activado | DD-005 |
| Identificadores de persona o de sesión | **Nunca** | DD-005, NFR-024 |
| Historial, descartes, preferencias | **Nunca** | NFR-006, NFR-008 |

Lo que sale es **el contenido de lo que la persona sorda está diciendo**, expresado como palabras de
un vocabulario cerrado y públicamente conocido de 64 glosas. Es menos revelador que texto libre
—el espacio de mensajes está acotado— pero es el mensaje. La spec lo dice así y este contrato no lo
suaviza.

**Precondición de la primera petición (FR-041, SC-023)**: ninguna glosa sale del dispositivo antes
de que la persona haya visto el aviso en la interfaz y decidido. La preferencia `pulido_activado`
arranca en `false` y solo puede pasar a `true` después de `aviso_pulido_visto`
([data-model.md §5](../data-model.md)).

**El pulido es desactivable sin penalización funcional** (NFR-025): con el pulido apagado, ninguna
glosa sale, la voz pronuncia la glosa cruda y el resto del sistema funciona igual.

---

## 2. Endpoint

```text
POST /v1/polish
Content-Type: application/json
Transporte cifrado obligatorio (NFR-024)
Sin autenticación de usuario, sin cuentas (NFR-026)
```

### 2.1 Petición

```json
{
  "v": 1,
  "glosses": ["agua", "beber", "gracias"]
}
```

| Campo | Regla |
|---|---|
| `glosses` | 1 a **5** elementos (FR-038, NFR-026). Más de 5 → rechazo **sin procesar** |
| Cada elemento | MUST ser una `clave` del vocabulario LSA64. Cualquier otra cosa → rechazo |

**El schema no admite ningún otro campo.** No hay `session_id`, no hay `user_id`, no hay
`device_id`, no hay timestamps, no hay confianzas. Un campo que no existe no puede filtrarse por
descuido, y su ausencia es verificable por inspección del código del servicio, que forma parte del
proyecto y de su licencia (NFR-024, NFR-026).

Solo se envían glosas que **superaron el umbral** (FR-039(d)). El filtrado ocurre en el cliente,
antes de la petición.

### 2.2 Respuesta correcta

```json
{
  "v": 1,
  "sentence": "necesito agua para beber, gracias"
}
```

### 2.3 Respuestas de rechazo

```json
{ "v": 1, "error": { "code": "out_of_vocabulary" } }
```

| HTTP | `code` | Cuándo |
|---|---|---|
| 400 | `out_of_vocabulary` | Algún elemento no es una glosa de LSA64 (SC-025) |
| 413 | `too_many_glosses` | Más de 5 elementos |
| 422 | `unpolishable` | El modelo no produjo una frase que pase la validación de §3 |
| 429 | `rate_limited` | Límite por origen excedido (30/min inicial, provisional) |
| 503 | `unavailable` | Servicio no disponible |

Los cuerpos de error **no** incluyen la entrada recibida ni ningún eco del contenido. Un mensaje de
error que devuelve las glosas recibidas es una forma de registro.

En **todos** los casos de rechazo, el cliente degrada a glosa cruda (§5). Ninguno es un estado de
espera.

---

## 3. Restricción de clase cerrada (DD-004, FR-040)

La frase generada MUST restringirse a **palabras de clase cerrada** añadidas —preposiciones,
artículos, conjunciones, pronombres, auxiliares— y a la flexión de las glosas reconocidas. **Toda
palabra de contenido** de la salida (sustantivo, verbo principal, adjetivo, adverbio) MUST trazar
por lema a una glosa de la entrada.

### Mecanismo de validación

**Tabla curada, no lematizador estadístico.** Con 64 glosas conocidas de antemano:

```text
Para cada token t de la frase generada:
    t ∈ lista_blanca_funcional            → OK
    t ∈ flexiones(g) para alguna g ∈ entrada  → OK
    en otro caso                          → palabra de contenido NO TRAZABLE
```

Los conjuntos `lema` y `flexiones` de cada glosa son parte del vocabulario
([data-model.md §1.5](../data-model.md)) y se versionan con él.

**Si aparece una sola palabra de contenido no trazable, se descarta la frase completa** y se
pronuncia la glosa cruda (FR-040). No se corrige la frase, no se reintenta con otro prompt, no se
pronuncia una versión parcial.

### Por qué tabla curada

Un lematizador estadístico introduciría su propia tasa de error justo en el mecanismo que protege
contra poner palabras en boca de una persona sorda. La tabla es determinista, auditable, testeable
con las 50 secuencias de SC-021, y no agrega una dependencia de NLP al servicio.

### Doble validación (AD-07)

La misma validación corre **en el servicio** y **en el cliente antes de pronunciar**. El cliente no
puede confiar en que el servicio esté sano: una respuesta corrupta o un servicio comprometido
pondría palabras en boca de una persona sorda, que es el daño concreto que DD-004 evita.

### Glosa cruda siempre visible (SC-022)

En el 100% de las presentaciones, la glosa cruda se muestra junto a la frase pulida, de modo que un
observador pueda distinguir qué señó la persona y qué agregó el modelo. Es requisito de
presentación, no del servicio, y se enuncia aquí porque es la contrapartida de permitir que el
servicio agregue palabras.

---

## 4. Operación (NFR-026, NFR-027)

| Propiedad | Valor | Nota |
|---|---|---|
| Disponibilidad | >= 95% durante las ventanas de evaluación declaradas | Fuera de ellas, mejor esfuerzo: la caída no rompe el producto |
| Límite de uso | 30 peticiones/min por origen | Provisional; holgado frente a una conversación real |
| Tamaño máximo | 5 glosas | Rechazo sin procesar por encima |
| Validación | Vocabulario cerrado | Vuelve el endpoint **inútil como LLM de propósito general** |
| Persistencia | **Ninguna** | Ni glosas, ni respuestas, ni IP asociada a contenido (NFR-024) |
| Estado | Sin estado | No correlaciona peticiones entre sí |

La validación contra vocabulario cerrado es la defensa central contra el abuso: no hay cuentas que
administrar y no hay clave que extraer del cliente.

### Métricas permitidas (NFR-027)

Cantidad de peticiones · distribución de latencia · tasa de error · tasa de rechazo por límite ·
tasa de rechazo por vocabulario · disponibilidad.

**Métricas agregadas, sin registros por petición.** Contar peticiones y medir latencia no revela qué
dijo nadie; guardar la glosa, sí, y esa es exactamente la línea. Tampoco se guardan registros por
petición que puedan reconstruir contenido cruzándolos entre sí.

**Prohibido registrar**: la glosa recibida, la frase generada, cualquier IP asociada a contenido, y
cualquier identificador que permita correlacionar peticiones con una persona o sesión.

---

## 5. Comportamiento del cliente

### 5.1 Presupuesto de espera (NFR-023)

El tiempo entre la pausa detectada y el comienzo de la frase hablada MUST ser < **3 s**, incluidos
ida y vuelta y generación, sobre **4G urbano** con RTT de referencia 50–150 ms registrado en cada
corrida.

**El presupuesto es un límite de espera, no una aspiración.** Vencido, el cliente pronuncia la glosa
cruda y deja de esperar.

### 5.2 Degradación (FR-031, SC-026)

Ante cualquiera de estas situaciones, el comportamiento es **el mismo**:

- Sin conexión.
- Servicio caído (`503`).
- Límite de uso excedido (`429`).
- Presupuesto de NFR-023 agotado.
- Frase rechazada por la validación de §3.
- Pulido desactivado por la persona usuaria (`NFR-025`).

```text
→ Se pronuncia la GLOSA CRUDA.
→ Se avisa que la frase no pudo componerse (no se calla ni se espera).
→ NO se reintenta en bucle.
→ NO se muestra el detalle técnico a la persona usuaria.
```

El reconocimiento **no se degrada** en ninguno de estos casos: es local (FR-031). Lo único que se
pierde es la fluidez de la frase.

### 5.3 Verificación

| Criterio | Qué comprueba |
|---|---|
| SC-021 | 50 secuencias, cero palabras de contenido no trazables por lema |
| SC-022 | Glosa cruda visible junto a la frase pulida, en el 100% de las presentaciones |
| SC-023 | En instalación nueva, ninguna glosa sale antes del aviso de FR-041 y la decisión |
| SC-024 | Con el pulido desactivado, sesión de 10 señas sin ninguna petición de red |
| SC-025 | 100% de rechazo de peticiones adversarias con texto libre |
| SC-026 | Con el servicio caído, con límite excedido o fuera de presupuesto: sesión de 10 señas con glosa cruda, sin bloqueos, sin esperas visibles y sin reintentos en bucle |
| SC-004 | Tráfico saliente: únicamente glosas. Cero frames, cero keypoints, cero identificadores |

---

## 6. Lo que este servicio NO puede hacer

- Recibir video, frames o keypoints (Principio II, FR-039(b)).
- Reconocer, clasificar o inferir señas (Principio II).
- Agregar palabras de contenido no trazables a la entrada (DD-004, FR-040).
- Retener, registrar o persistir glosas o respuestas (NFR-024).
- Correlacionar peticiones entre sí o con una persona (DD-005, NFR-027).
- Delegarse a una API comercial de terceros (spec §Out of Scope).
- Ser un requisito para reconocer: sin él el sistema sigue funcionando (FR-031, NFR-025).
