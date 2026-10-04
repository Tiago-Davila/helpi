# Feature Specification: Medición de calidad del reconocimiento de Eva

**Feature Branch**: `001-calidad-reconocimiento`

**Created**: 2026-10-03

**Status**: Draft

**Input**: User description: "Desarrollar el módulo de medición de calidad del reconocimiento
de señas de Eva: un sistema que permita saber si el modelo entiende bien, tanto durante las
pruebas como en uso real, sin comprometer la privacidad de las conversaciones."

## Contexto

El único número que existe sobre Eva es **≈0,85 de exactitud con partición por sujeto sobre
LSA64**, medido en laboratorio con señantes oyentes y diestros, guantes de colores, fondo
controlado y trípode. No existe ninguna medición con personas reales, sin guantes, en una
casa o en la calle.

Este módulo distingue dos situaciones con garantías distintas:

| Situación | Verdad de referencia | Qué se puede medir | Dónde |
|---|---|---|---|
| Sesión de validación | Sí: se pide una seña concreta | Exactitud real fuera del laboratorio | Build de evaluación |
| Uso real | No: nadie sabe qué seña se quiso hacer | Solo indicadores indirectos (error percibido) | Build de producción |

**Exactitud en producción no es medible por diseño.** Ningún indicador de uso real se
presenta como exactitud.

## Clarifications

### Session 2026-10-04

- Q: ¿Los indicadores agregados de producción se recolectan por defecto o con opt-in? → A:
  Activos por defecto (opt-out), con aviso en el primer uso y un ajuste para apagarlos
  (FR-022).
- Q: ¿Adónde se envían las filas de producción? → A: A un receptor de métricas nuevo,
  operado por el equipo (FR-032, FR-032a).
- Q: ¿Dónde vive el análisis? → A: Todavía no hay repositorio para el análisis. Queda fuera
  de la app; esta app define y produce el formato de exportación como contrato versionado
  (FR-039).
- Q: ¿Qué cuenta como descarte? → A: Solo descartar la frase propuesta; editar el texto
  antes de confirmar o corregir un mensaje ya publicado no cuenta (FR-023).
- Q: ¿Qué contiene el protocolo? → A: 64 señas × 3 repeticiones en orden aleatorio, en 4
  bloques de 48 intentos con pausas; la sesión puede retomarse en otro día (FR-040a).
- Q: ¿El consentimiento es por participante o por dispositivo? → A: Por dispositivo. Revocar
  solo detiene el registro futuro; lo ya registrado se conserva hasta su retención y se puede
  borrar con una acción separada en el dispositivo (FR-011, FR-013, FR-013a).
- Q: ¿Cuánto se conservan las sesiones de evaluación? → A: 90 días desde su última
  exportación; 180 días desde su registro si nunca se exportaron (NFR-011).
- Q: ¿Cómo se indica la seña pedida? → A: Glosa escrita por defecto; video de referencia de
  LSA64 a pedido; cada intento registra si se vio el video y el análisis separa ambos grupos
  (FR-042, FR-042a).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Sesión de validación con verdad de referencia (Priority: P1)

Una persona participante (señante sorda, intérprete de LSA o integrante del equipo) abre el
build de evaluación, consiente el registro, carga las condiciones de la prueba (entorno,
iluminación, distancia, mano dominante, guantes) y sigue un protocolo que le indica, de forma
visual y sin audio, qué seña hacer. Por cada intento queda registrado qué se pidió, qué
predijo Eva, con qué confianza, las tres predicciones más probables y si superó el umbral.
Al terminar, la sesión completa se puede exportar para análisis.

**Why this priority**: es la única forma de obtener exactitud real fuera del laboratorio.
Sin estas sesiones no hay ningún dato con verdad de referencia y ninguna otra historia
puede responder si el modelo "entiende bien".

**Independent Test**: ejecutar una sesión completa del protocolo en el build de evaluación,
exportarla y verificar que cada intento contiene seña esperada, seña predicha, confianza,
top-3, superación de umbral, acierto/error, y que la sesión contiene sus condiciones.

**Acceptance Scenarios**:

1. **Given** un dispositivo sin consentimiento vigente, **When** alguien abre el modo de
   validación, **Then** ve qué se registra antes de cualquier captura y no puede empezar sin
   consentir explícitamente.
2. **Given** consentimiento otorgado, **When** inicia una sesión, **Then** el sistema exige
   completar las condiciones de la prueba antes de pedir la primera seña.
3. **Given** una sesión en curso, **When** el protocolo pide una seña, **Then** la indicación
   es comprensible sin audio (la glosa escrita, con el video de referencia disponible a
   pedido) y muestra el avance (intento N de M).
4. **Given** que la persona realiza el intento, **When** Eva produce un resultado, **Then** se
   registran seña esperada, índice y glosa predichos, confianza, top-3 con confianzas,
   superación del umbral y si fue correcto.
5. **Given** que Eva no produce resultado (sin hombros, sin manos o secuencia inválida),
   **When** se agota el intento, **Then** se registra como "sin resultado" con su causa, y no
   como error de clasificación ni se omite.
6. **Given** una sesión terminada, **When** el equipo la exporta, **Then** obtiene un archivo
   autocontenido con condiciones, intentos y versiones de app, modelo y contrato.

---

### User Story 2 - Garantía de que producción no puede registrar glosas (Priority: P1)

El equipo necesita demostrar, no solo afirmar, que el build de producción no contiene la
capacidad de registrar qué se señó. La separación la produce la configuración de
compilación, y existe una verificación automática sobre el artefacto de producción.

**Why this priority**: la secuencia de glosas es la transcripción de una conversación
privada y dato sensible bajo la Ley N.º 25.326. Un error acá no se puede deshacer. Es
condición para que las historias 1 y 3 puedan existir.

**Independent Test**: inspeccionar el artefacto de producción con la verificación automática
y comprobar que no contiene la recolección detallada; introducir deliberadamente una
referencia a ella en código de producción y comprobar que la verificación falla.

**Acceptance Scenarios**:

1. **Given** el artefacto de producción, **When** se ejecuta la verificación, **Then**
   confirma la ausencia de todo componente de recolección detallada, consentimiento de
   evaluación y protocolo.
2. **Given** un cambio que hace alcanzable la recolección detallada desde producción,
   **When** corre la integración continua, **Then** la verificación falla y el cambio no se
   integra.
3. **Given** el build de producción, **When** se usa la app, **Then** no existe ningún
   control, pantalla ni ajuste que active el registro detallado.

---

### User Story 3 - Indicadores agregados de uso real (Priority: P2)

Durante una conversación real, el sistema acumula en memoria solo contadores y estadísticas
de confianza. Al terminar la sesión escribe una única fila agregada, sin ninguna información
sobre qué se señó. Más tarde, cuando hay conexión, esa fila se envía en segundo plano.

**Why this priority**: es la única ventana al comportamiento en uso real, pero sus datos son
indirectos y requieren primero la garantía de la historia 2.

**Independent Test**: realizar una conversación con varios reconocimientos sobre y bajo el
umbral, cerrarla y verificar que existe exactamente una fila con los campos permitidos y
ningún campo prohibido.

**Acceptance Scenarios**:

1. **Given** una conversación con N reconocimientos, **When** finaliza, **Then** se persiste
   exactamente una fila con: cantidad de reconocimientos, cuántos superaron y cuántos no
   superaron el umbral, confianza media y mediana, cantidad de descartes, duración, versión
   de app, versión de modelo, modelo de dispositivo y versión de sistema operativo.
2. **Given** la misma conversación, **When** se inspecciona todo lo persistido o enviado,
   **Then** no aparece ninguna glosa, índice de clase, frecuencia por clase, keypoint ni
   marca temporal por reconocimiento.
3. **Given** una conversación en curso, **When** ocurre un reconocimiento, **Then** no se
   produce ninguna escritura a disco.
4. **Given** una fila persistida, **When** se inspecciona su identificador, **Then** es
   aleatorio, distinto en cada sesión y no permite vincular sesiones entre sí ni con una
   persona.
5. **Given** el primer uso de la app, **When** la persona llega a la primera conversación,
   **Then** antes ve un aviso de qué indicadores se registran, qué no se registra y adónde se
   envían, con acceso directo al ajuste para apagarlos.
6. **Given** indicadores apagados, **When** termina una conversación, **Then** no se escribe
   ninguna fila y las filas pendientes ya fueron eliminadas sin enviarse.

---

### User Story 4 - Descartar un reconocimiento incorrecto (Priority: P2)

La persona señante ve un reconocimiento que no corresponde a lo que quiso decir y lo descarta
con un gesto simple. El reconocimiento no se publica en la conversación y el descarte se
cuenta como error percibido.

**Why this priority**: es la única retroalimentación real de error disponible en uso real y la
métrica más valiosa de producción. También mejora la conversación por sí misma.

**Independent Test**: provocar un reconocimiento, descartarlo y verificar que no se publica
ni se vocaliza y que el contador de descartes de la sesión aumenta en uno.

**Acceptance Scenarios**:

1. **Given** un reconocimiento propuesto, **When** la persona lo descarta, **Then** no se
   agrega a la conversación, no se ofrece a voz y el contador de descartes sube en uno.
2. **Given** un reconocimiento descartado, **When** se registra el descarte, **Then** en
   producción solo cambia el contador; en evaluación se asocia además al intento.
3. **Given** que la persona no descarta, **When** el reconocimiento se publica, **Then** no se
   lo interpreta como acierto confirmado.

---

### User Story 5 - Análisis de calidad para decidir una publicación (Priority: P2)

El equipo reúne sesiones de validación de distintas personas y entornos y obtiene exactitud
global y por clase, matriz de confusión, pares más confundidos y distribución de confianza de
aciertos frente a errores, siempre con partición por sujeto. Con eso decide si una versión del
modelo mejoró, qué señas fallan, si el umbral separa bien aciertos de errores y si conviene un
umbral global o uno por clase.

**Why this priority**: es el uso final de los datos de la historia 1; sin análisis, el
registro no produce decisiones.

**Independent Test**: alimentar el análisis con sesiones exportadas de al menos dos personas
y verificar que todas las métricas se producen, que se informan por persona y por condición,
y que el análisis rechaza mezclar intentos de una misma persona entre conjuntos.

**Acceptance Scenarios**:

1. **Given** sesiones exportadas de varias personas, **When** se ejecuta el análisis, **Then**
   produce exactitud global y por clase, matriz de confusión 64×64 más la categoría "no
   reconocido", los pares más confundidos y la distribución de confianza separada por
   acierto/error.
2. **Given** las mismas sesiones, **When** se agrupan por condición (mano dominante, entorno,
   iluminación, guantes), **Then** la exactitud se informa por grupo con la cantidad de
   personas e intentos de cada uno.
3. **Given** un grupo con menos personas que el mínimo reportable, **When** se informa,
   **Then** se marca como no concluyente en lugar de presentarse como resultado.
4. **Given** sesiones de distintas versiones de modelo o de contrato, **When** se analizan,
   **Then** nunca se combinan en una misma cifra.

---

### User Story 6 - Envío diferido sin afectar el uso (Priority: P3)

Las filas agregadas de producción se guardan primero en el dispositivo y se envían en segundo
plano cuando hay conexión. Sin conexión, la app funciona igual.

**Why this priority**: sin envío, los indicadores de producción solo existen en cada teléfono;
pero el valor principal del módulo (historias 1, 2 y 5) no depende de la red.

**Independent Test**: generar filas sin conexión, verificar que la app funciona completa;
habilitar la red y verificar que la cola se vacía y que una fila solo desaparece tras la
confirmación de recepción.

**Acceptance Scenarios**:

1. **Given** sin conexión, **When** termina una sesión, **Then** la fila queda persistida y
   ningún flujo de la app espera ni falla por la red.
2. **Given** filas pendientes y conexión disponible, **When** corre el envío, **Then** cada
   fila se elimina solo después de que el destino confirma su recepción.
3. **Given** un envío fallido, **When** se reintenta, **Then** el intervalo entre reintentos
   crece exponencialmente hasta un máximo.
4. **Given** filas que superan el período de retención sin enviarse, **When** corre la
   depuración, **Then** se eliminan sin enviarse.

---

### User Story 7 - Revocar el consentimiento y borrar lo registrado (Priority: P3)

Quien usa el dispositivo de validación puede revocar el consentimiento: desde ese momento no
se registra nada más. Lo ya registrado se conserva hasta su retención, salvo que se use la
acción separada de borrado, que elimina las sesiones de un participante o todas las del
dispositivo.

**Why this priority**: el derecho de supresión de la Ley N.º 25.326 exige poder borrar los
datos; separar revocación y borrado evita perder sesiones de otras personas por accidente.

**Independent Test**: registrar sesiones de dos participantes, revocar y verificar que no se
puede registrar más pero las sesiones siguen; luego borrar las de un participante y
verificar que solo quedan las del otro.

**Acceptance Scenarios**:

1. **Given** consentimiento vigente y sesiones registradas, **When** se revoca, **Then** no
   puede iniciarse ni continuarse ninguna sesión, y las sesiones registradas se conservan.
2. **Given** consentimiento revocado, **When** alguien intenta iniciar una sesión, **Then**
   se le pide consentimiento nuevamente.
3. **Given** sesiones registradas, **When** se usa la acción de borrado eligiendo un
   participante o "todas", **Then** se muestra qué se va a eliminar y, tras confirmar, esas
   sesiones desaparecen del dispositivo, haya o no consentimiento vigente.
4. **Given** sesiones ya exportadas, **When** se borran del dispositivo, **Then** se informa
   que las copias exportadas quedan fuera del dispositivo y cómo pedir su eliminación al
   equipo.

### Edge Cases

- **Persona zurda**: el protocolo registra la mano dominante en las condiciones; el análisis
  informa la exactitud de zurdos por separado. Un resultado bajo es un hallazgo esperado, no
  un error del módulo.
- **Seña fuera de las 64** durante la validación: el protocolo solo pide señas del
  vocabulario; un resultado "no reconocido" frente a una seña pedida cuenta como error de
  recall, no se omite.
- **Seña de emergencia**: el vocabulario no tiene ninguna; el protocolo no las incluye y el
  análisis no puede producir ninguna cifra sobre ellas. El reporte lo declara.
- **Intento sin resultado** (sin hombros en ningún cuadro, sin manos, persona fuera de
  cuadro): se registra como "sin resultado" con causa; la exactitud se informa con y sin
  estos intentos, declarando cuál es cuál.
- **Sesión interrumpida** (app a segundo plano, llamada, pausa larga, cierre forzado):
  en producción, si la sesión termina sin escritura, la fila de esa sesión se pierde y no se
  reconstruye; nunca se escribe una fila parcial durante la sesión. En evaluación, los
  intentos ya completados se conservan y la sesión queda incompleta hasta retomarse en el
  bloque pendiente (FR-040a); el bloque interrumpido se reinicia desde su primer intento sin
  respuesta.
- **Sesión de producción sin reconocimientos**: se escribe la fila con cantidad cero; la
  confianza media y mediana quedan como "sin dato", no como cero.
- **Muy pocos reconocimientos en producción** (1 o 2): la mediana con tan pocos valores
  podría acercarse a revelar confianzas individuales; aun así no revela qué seña fue. Se
  registra igual.
- **Cambio de umbral durante la sesión**: cada reconocimiento se cuenta contra el umbral
  vigente en ese momento; la fila registra además el umbral vigente al cerrar.
- **Modelo actualizado** entre sesiones: cada fila y cada sesión llevan la versión del modelo
  y del contrato; el análisis no mezcla versiones.
- **Almacenamiento lleno**: la falta de espacio para métricas nunca interrumpe la
  conversación; la fila se pierde y no se reintenta escribir durante la sesión.
- **Reloj del dispositivo incorrecto**: la duración se mide con un reloj monótono; la
  retención tolera saltos del reloj de pared sin borrar de más.
- **Descarte de algo bajo el umbral**: lo que no superó el umbral no se publica, así que no
  puede descartarse; solo se descartan reconocimientos propuestos.
- **Misma persona en varias sesiones**: el análisis necesita agrupar sus sesiones por sujeto
  para la partición; eso exige un identificador de participante seudónimo en evaluación (ver
  FR-015), nunca en producción.
- **Exportación sin destino**: si no hay dónde guardar o compartir el archivo, la sesión
  permanece en el dispositivo hasta su retención.

## Requirements *(mandatory)*

### Functional Requirements

**Separación de builds**

- **FR-001**: El sistema MUST ofrecer dos variantes de compilación: **evaluación** y
  **producción**, con capacidades de registro distintas.
- **FR-002**: La recolección detallada (glosas, índices de clase, top-3, seña esperada,
  condiciones de sesión, protocolo y consentimiento de evaluación) MUST existir únicamente
  en la variante de evaluación.
- **FR-003**: La separación MUST resolverse en la compilación: el código de recolección
  detallada MUST NOT estar presente en el artefacto de producción. Una bandera en tiempo de
  ejecución no satisface este requisito.
- **FR-004**: MUST existir una verificación automática, bloqueante de integración continua,
  que inspeccione el artefacto de producción y falle si contiene cualquier componente de
  recolección detallada.
- **FR-005**: La variante de evaluación MUST identificarse visualmente de forma permanente
  (por ejemplo, un distintivo en pantalla) para que nadie la confunda con producción.

**Registro en evaluación**

- **FR-006**: Por cada reconocimiento, la variante de evaluación MUST registrar: índice de
  clase predicho, glosa, confianza, las tres predicciones más probables con sus confianzas,
  umbral vigente y si lo superó.
- **FR-007**: Cuando el protocolo fija la seña esperada, MUST registrar además la seña
  esperada y si el reconocimiento fue correcto. Un resultado "no reconocido" o "sin
  resultado" frente a una seña esperada MUST registrarse, con su causa.
- **FR-008**: Por cada sesión MUST registrar sus condiciones: modelo de dispositivo,
  entorno (interior/exterior y tipo), iluminación, distancia aproximada a la cámara, mano
  dominante (zurda/diestra/ambidiestra), uso de guantes, y versiones de app, modelo y
  contrato de keypoints.
- **FR-009**: MUST poder exportarse el registro completo de una sesión en un archivo
  autocontenido y legible por herramientas de análisis.
- **FR-010**: La variante de evaluación MUST NOT registrar keypoints ni video (alcance
  excluido en cualquier build).

**Consentimiento en evaluación**

- **FR-011**: El consentimiento es **por dispositivo**. Antes de la primera sesión en un
  dispositivo sin consentimiento vigente, MUST mostrarse qué se registra, para qué, por
  cuánto tiempo y quién accede, y MUST consentirse con una acción explícita. No hay
  consentimiento por defecto ni implícito. El aviso MUST indicar que el consentimiento cubre
  a todas las personas que participen en ese dispositivo, para que el equipo lo explique a
  cada participante antes de su sesión.
- **FR-012**: El aviso MUST ser comprensible para una persona sorda sin instrucciones
  externas: lenguaje llano, apoyo visual y sin depender de audio.
  [Ver ambigüedad A-04 sobre versión en LSA.]
- **FR-013**: El consentimiento MUST poder revocarse en cualquier momento desde la app. La
  revocación detiene todo registro futuro (incluida la sesión en curso) y MUST NOT borrar lo
  ya registrado, que se conserva hasta su retención (NFR-011).
- **FR-013a**: MUST existir una acción de borrado, separada de la revocación y disponible
  con o sin consentimiento vigente, que elimine del dispositivo las sesiones de un
  participante elegido o todas las sesiones, previa confirmación que muestre qué se borra.
- **FR-014**: El consentimiento MUST registrar la versión del aviso aceptado; si el aviso
  cambia de forma material, MUST pedirse nuevamente.
- **FR-015**: Cada sesión de evaluación MUST asociarse a un identificador seudónimo de
  participante, elegido o generado en el dispositivo, que permita agrupar sesiones de una
  misma persona para la partición por sujeto sin contener su nombre ni datos de contacto.

**Registro en producción**

- **FR-016**: La variante de producción MUST acumular los indicadores en memoria durante la
  sesión y escribir **una única fila** al finalizarla. MUST NOT escribir por reconocimiento.
- **FR-017**: La fila MUST contener únicamente: identificador de sesión, cantidad de
  reconocimientos, cantidad sobre el umbral, cantidad bajo el umbral, confianza media,
  confianza mediana, cantidad de descartes, duración de la sesión, umbral vigente al cerrar,
  versión de app, versión de modelo, modelo de dispositivo y versión de sistema operativo.
- **FR-018**: La fila MUST NOT contener glosas, índices de clase, top-3, frecuencia por clase,
  keypoints, texto de la conversación, marcas temporales por reconocimiento ni identificadores
  de cuenta, de dispositivo o de publicidad.
- **FR-019**: El identificador de sesión MUST ser aleatorio, generado por sesión, y MUST NOT
  derivarse de ni vincularse con la persona usuaria, su cuenta o el dispositivo.
- **FR-020**: Una sesión de producción MUST definirse como el intervalo entre "Iniciar
  conversación" y su finalización (cierre explícito o invalidación de la conversación por
  pausa prolongada).
- **FR-021**: La recolección de frecuencia por clase en producción MUST NOT existir en esta
  versión. Si se incorpora en el futuro, MUST requerir una autorización separada, apagada por
  defecto, que diga explícitamente que incluye qué señas se usaron, y una enmienda a esta
  especificación.
- **FR-022**: La recolección y el envío de indicadores agregados de producción MUST estar
  **activos por defecto** (opt-out), con:
  - un aviso visible en el primer uso, antes de la primera conversación, que diga qué se
    registra, qué NO se registra (qué se señó) y adónde se envía;
  - un ajuste para apagarlos en cualquier momento, accesible desde la configuración;
  - efecto inmediato al apagarlos: la sesión en curso no escribe fila y las filas pendientes
    de envío se eliminan del dispositivo sin enviarse.
  Volver a encenderlos solo afecta a sesiones futuras.

**Descartes**

- **FR-023**: La persona señante MUST poder descartar un reconocimiento propuesto con un gesto
  simple, alcanzable sin salir de la conversación. El **descarte** es exclusivamente la
  acción "Descartar" sobre la frase propuesta, antes de publicarse. Editar el texto antes de
  confirmar y corregir un mensaje ya publicado MUST NOT contarse como descarte.
- **FR-024**: Un reconocimiento descartado MUST NOT publicarse en la conversación ni ofrecerse
  a voz.
- **FR-025**: Cada descarte MUST contarse como señal de error percibido: en producción solo
  como contador de la sesión; en evaluación asociado además al intento.
- **FR-026**: La ausencia de descarte MUST NOT interpretarse ni presentarse como acierto
  confirmado.

**Envío diferido**

- **FR-027**: Las filas MUST persistirse localmente antes de cualquier intento de envío.
- **FR-028**: El envío MUST ocurrir en segundo plano, solo con conexión, con reintentos de
  retroceso exponencial y un intervalo máximo.
- **FR-029**: La ausencia de conexión MUST NOT bloquear, demorar ni degradar ninguna función
  de la app; la cadena de reconocimiento MUST seguir funcionando sin red.
- **FR-030**: Una fila MUST eliminarse localmente solo cuando el destino confirma su
  recepción; el destino MUST tolerar recibir la misma fila más de una vez sin duplicarla.
- **FR-031**: Las filas no enviadas MUST depurarse al cumplir su período de retención
  (ver NFR-012), y la cola MUST tener un tamaño máximo; al alcanzarlo se descartan las filas
  más antiguas.
- **FR-032**: El destino del envío MUST ser un receptor de métricas **operado por el equipo**
  de Helpi; MUST NOT usarse servicios de analítica de terceros. El receptor:
  - acepta únicamente filas con los campos de FR-017 y rechaza cualquier campo adicional;
  - confirma la recepción de cada fila y deduplica por identificador de sesión (FR-030);
  - recibe las filas por un canal cifrado y no conserva datos de conexión (como la
    dirección IP) asociados a las filas.
- **FR-032a**: El acceso a red de la app MUST usarse exclusivamente para el envío de filas de
  producción. La cadena de reconocimiento MUST NOT depender de la red ni usarla.

**Análisis**

- **FR-033**: A partir de sesiones de evaluación, el equipo MUST poder obtener: exactitud
  global, exactitud por clase, matriz de confusión (incluida la categoría "no reconocido"),
  los pares de señas más confundidos y la distribución de confianza de aciertos frente a
  errores.
- **FR-034**: El análisis MUST informar, para un umbral dado y para un rango de umbrales, la
  proporción de aciertos publicados y de errores publicados, para decidir si el umbral separa
  ambos casos y si conviene un umbral global o uno por clase.
- **FR-035**: Toda métrica reportable MUST calcularse con partición por sujeto. El análisis
  MUST rechazar cualquier partición que reparta intentos de una misma persona entre conjuntos.
- **FR-036**: Las métricas MUST poder desagregarse por condición de sesión (mano dominante,
  entorno, iluminación, distancia, guantes) indicando cantidad de personas e intentos de cada
  grupo; un grupo con menos de 3 personas MUST marcarse como no concluyente.
- **FR-037**: El análisis MUST NOT combinar sesiones de distintas versiones de modelo o de
  contrato en una misma cifra.
- **FR-038**: Todo reporte MUST distinguir en su presentación la **exactitud** (solo de
  evaluación) de los **indicadores de error percibido** (tasa bajo umbral, tasa de descarte,
  confianza en producción), y MUST NOT llamar exactitud a estos últimos.
- **FR-039**: El análisis (FR-033 a FR-038) MUST realizarse **fuera de la app**, en un
  repositorio de análisis que todavía no existe. Esta app MUST limitarse a producir las
  exportaciones de evaluación, y el formato de exportación MUST definirse como un contrato
  versionado, documentado en este repositorio, para que el análisis pueda construirse sin
  depender del código de la app. FR-033 a FR-038 son requisitos para ese análisis y se
  verifican cuando exista.

**Protocolo de validación**

- **FR-040**: MUST existir un protocolo de validación documentado y versionado que defina qué
  señas se piden, en qué orden, cuántas repeticiones por seña y en qué entornos.
- **FR-040a**: La versión inicial del protocolo MUST pedir las **64 señas × 3 repeticiones**
  (192 intentos por sesión y entorno), en orden aleatorio con semilla registrada, divididas
  en **4 bloques de 48 intentos**. Entre bloques MUST ofrecerse una pausa. Una sesión MUST
  poder retomarse en el siguiente bloque pendiente, también en otro día, conservando
  participante, condiciones y semilla; si las condiciones cambian, el bloque se registra en
  una sesión nueva.
- **FR-041**: El orden de las señas MUST evitar que una misma seña se pida en intentos
  consecutivos, para no medir repetición inmediata.
- **FR-042**: Durante la sesión, el sistema MUST indicar qué seña hacer sin depender de audio,
  mostrar el avance y permitir pausar, repetir un intento fallido técnicamente y terminar
  antes. Por defecto se muestra **solo la glosa escrita**; el video de referencia de LSA64
  (con atribución) MUST estar disponible a pedido y nunca reproducirse automáticamente.
- **FR-042a**: Cada intento MUST registrar si se vio el video de referencia antes de
  realizarlo. El análisis MUST informar por separado los intentos con y sin video (señado
  propio frente a imitación) y MUST NOT combinarlos en una misma cifra de exactitud.
- **FR-043**: Cada sesión MUST registrar la versión de protocolo usada y quedar asociada a sus
  condiciones para comparar entre entornos.
- **FR-044**: La persona participante MUST poder marcar un intento como "lo hice mal" para
  separar errores propios de errores del modelo; esos intentos se informan aparte.

**Limitaciones explícitas**

- **FR-045**: Todo reporte del análisis MUST incluir las limitaciones conocidas: vocabulario
  cerrado de 64 señas de LSA64 no diseñado para comunicación asistida y sin señas de
  emergencia; señantes de entrenamiento oyentes, diestros y no nativos; condiciones de
  laboratorio; y la imposibilidad de medir exactitud en producción.

### Non-Functional Requirements

**Privacidad**

- **NFR-001**: En producción, ningún dato que permita reconstruir qué dijo la persona MUST
  persistirse ni salir del dispositivo.
- **NFR-002**: La secuencia de glosas MUST tratarse como dato sensible bajo la Ley N.º 25.326;
  solo existe en la variante de evaluación, con consentimiento.
- **NFR-003**: Video, cuadros y keypoints MUST NOT persistirse ni salir del dispositivo en
  ninguna variante (Constitución, Principio VI).
- **NFR-004**: Toda exportación de evaluación MUST ser una acción explícita de la persona;
  nada de evaluación se envía automáticamente.

**Costo de registro**

- **NFR-005**: El registro MUST NOT degradar el reconocimiento: la latencia de reconocimiento
  con el módulo activo MUST NOT superar en más de un 2 % a la latencia sin él, en ambas
  variantes.
- **NFR-006**: En producción, la escritura a disco MUST ocurrir una sola vez por sesión. En
  evaluación, la escritura MUST ocurrir fuera del camino de reconocimiento.

**Funcionamiento sin conexión**

- **NFR-007**: La app MUST funcionar completa sin red; la red solo sirve para sincronizar
  indicadores ya persistidos.

**Honestidad de las métricas**

- **NFR-008**: Ninguna métrica reportable MUST calcularse con partición aleatoria.
- **NFR-009**: Los indicadores de uso real miden error percibido, no error real, y MUST
  rotularse así en todo lugar donde se presenten.
- **NFR-010**: Toda cifra de exactitud MUST ir acompañada de la cantidad de personas, de
  intentos y de su intervalo de confianza.

**Retención**

- **NFR-011**: Las sesiones de evaluación en el dispositivo MUST depurarse automáticamente:
  a los **90 días de su última exportación** si fueron exportadas, o a los **180 días de su
  registro** si nunca se exportaron. Una sesión retomada (FR-040a) cuenta desde su último
  bloque registrado.
- **NFR-012**: Las filas de producción no enviadas MUST depurarse a los **30 días** de su
  registro (ver A-01).

**Verificabilidad**

- **NFR-013**: Las reglas de agregación de producción (conteos, media, mediana, campos
  permitidos) MUST tener pruebas automatizadas, y la verificación de FR-004 MUST ser bloqueante
  de integración continua.

### Key Entities

- **Variante de compilación**: evaluación o producción; determina qué componentes de registro
  existen en el artefacto.
- **Consentimiento de evaluación**: aceptación explícita, por dispositivo; versión del aviso,
  fecha de otorgamiento y de revocación. No se vincula a un participante.
- **Participante (seudónimo)**: identificador sin datos personales que agrupa las sesiones de
  una misma persona para la partición por sujeto. Solo en evaluación.
- **Protocolo de validación**: versión, lista ordenada de señas pedidas, repeticiones y
  entornos previstos.
- **Sesión de evaluación**: participante, protocolo, condiciones (dispositivo, entorno,
  iluminación, distancia, mano dominante, guantes), versiones de app/modelo/contrato, estado
  (completa/incompleta) y sus intentos.
- **Intento de evaluación**: seña esperada, índice y glosa predichos, confianza, top-3,
  umbral vigente, superación, correcto/incorrecto, "sin resultado" con causa, si se vio el
  video de referencia, descarte y marca
  "lo hice mal".
- **Fila de sesión de producción**: identificador aleatorio y únicamente los campos de FR-017.
- **Cola de envío**: filas de producción pendientes, con intentos de envío y antigüedad.
- **Reporte de calidad**: resultado del análisis sobre un conjunto de sesiones de una misma
  versión de modelo; métricas, desagregaciones, limitaciones y partición utilizada.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El equipo obtiene la primera cifra de exactitud fuera del laboratorio, con
  partición por sujeto, a partir de sesiones de al menos 5 personas en al menos 2 entornos
  distintos.
- **SC-002**: El reporte permite comparar la exactitud de personas zurdas y diestras con al
  menos 3 personas por grupo, o declara explícitamente que el grupo no es concluyente.
- **SC-003**: La verificación de ausencia de recolección detallada en producción detecta el
  100 % de las inserciones deliberadas de prueba y corre en cada integración.
- **SC-004**: Una auditoría de todo lo persistido y enviado por la variante de producción
  durante 20 conversaciones de prueba no encuentra ninguna glosa, índice de clase ni dato que
  permita reconstruir lo señado.
- **SC-005**: Cada conversación de producción produce como máximo una escritura de
  indicadores, verificable en el 100 % de las sesiones de prueba.
- **SC-006**: Una persona sorda, sin ayuda externa, entiende el aviso de consentimiento y
  completa la primera sesión del protocolo: al menos 4 de cada 5 participantes en la prueba
  de usabilidad.
- **SC-007**: La latencia de reconocimiento con el módulo activo no supera en más de un 2 % a
  la medida sin él.
- **SC-008**: Con la red deshabilitada durante 7 días, la app funciona completa y, al
  habilitarla, todas las filas aún dentro del período de retención se envían sin duplicados.
- **SC-009**: Con los datos del reporte, el equipo puede decidir con justificación escrita si
  una versión del modelo se publica, y si el umbral se mantiene, se ajusta o se calibra por
  clase.

## Ambigüedades abiertas

Las tres de mayor impacto se resolvieron en la sesión de aclaraciones del 2026-10-04. Las
restantes tienen un valor por defecto asumido y deben confirmarse en `/speckit-clarify`:

- **A-01 Retención de producción**: la de evaluación quedó fijada en NFR-011. Se asumen 30
  días para filas de producción no enviadas.
- **A-02 Entornos del protocolo**: el contenido quedó fijado en FR-040a. Se asume que cada
  participante lo realiza en al menos dos entornos (interior iluminado e interior con poca luz
  o exterior).
- **A-04 Aviso de consentimiento en LSA**: "comprensible para una persona sorda" puede requerir
  una versión del aviso en LSA (video), no solo texto llano. Se asume texto llano con apoyo
  visual en esta versión, con video en LSA como mejora.
- **A-05 Formato de exportación y canal**: se asume un archivo estructurado por sesión
  compartido mediante el mecanismo estándar del sistema para compartir archivos, sin envío por
  red desde la app.
- **A-07 Definición de "reconocimiento"** para los contadores de producción: se asume cada
  resultado del clasificador sobre una ventana válida, superara o no el umbral; las secuencias
  inválidas (sin hombros) no cuentan como reconocimiento y no se registran.
- **A-08 Quién usa el build de evaluación**: se asume distribución solo al equipo y a
  participantes de sesiones, nunca por tiendas de aplicaciones.

## Assumptions

- La app sigue sin cuentas de usuario; "no vincular a la cuenta" se cumple trivialmente y se
  extiende a no usar ningún identificador del dispositivo.
- La cadena de reconocimiento existente (umbral configurable, top-3 no confirmado,
  vocabulario cerrado) no cambia; el módulo solo la observa.
- La línea base de comparación es ≈0,85 con partición por sujeto sobre LSA64; el módulo no
  la recalcula.
- El rendimiento de la app (latencia, cuadros por segundo, batería, temperatura, crashes) se
  trata en la especificación 002; aquí solo se exige que el registro no lo degrade (NFR-005).
- Quedan fuera de alcance: reentrenamiento automático, panel web de métricas, recolección de
  keypoints o video, y vinculación con la identidad de la persona usuaria.
## Dependencias y riesgos

- **Receptor de métricas (nuevo)**: no existe; lo opera el equipo. Hasta que exista, las
  filas se acumulan en el dispositivo y se depuran por retención (FR-031); la historia 6 no
  puede verificarse de punta a punta.
- **Repositorio de análisis (nuevo)**: no existe. Hasta que exista, la historia 5 y SC-001,
  SC-002 y SC-009 no pueden verificarse; esta app solo garantiza el contrato de exportación.
- **Cambio de postura de red**: hoy la app elimina el permiso de red. Incorporarlo para el
  envío de métricas debe pasar el Constitution Check del plan (Principio VI): solo indicadores
  agregados, nunca conversaciones, keypoints ni video. La cadena de reconocimiento sigue sin
  usar red (FR-032a).
- **Riesgo legal del opt-out**: los indicadores agregados no permiten reconstruir lo señado,
  pero el modelo de dispositivo y la versión de sistema operativo, junto con el envío desde
  la red de la persona, son datos del dispositivo. Se recomienda una revisión bajo la Ley
  N.º 25.326 antes de publicar la variante de producción con envío activo por defecto.
