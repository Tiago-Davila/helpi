# Feature Specification: Traductor LSA de señas aisladas (LSA64) con confianza explícita

**Feature Branch**: `001-lsa-sign-translator`

**Created**: 2026-07-23

**Status**: Draft

**Input**: User description: "Desarrollar un traductor de Lengua de Señas Argentina (LSA) por video que reconozca señas aisladas en tiempo real y las comunique por texto y voz, con confianza explícita, en condiciones de uso reales."

## Clarifications

### Session 2026-07-25

- Q: ¿Cómo se ubican físicamente la persona señante, el interlocutor y el dispositivo, dado que
  cámara frontal y pantalla miran hacia el mismo lado? → A: El interlocutor sostiene el dispositivo
  y apunta la cámara frontal hacia la persona señante, que se ve a sí misma en la pantalla y recibe
  ahí su feedback de encuadre. Terminada la seña, el interlocutor gira el dispositivo hacia sí para
  leer la traducción, o la escucha por voz sin girarlo.
- Q: ¿Quién marca el fin de la seña, si el interlocutor no sabe LSA y no puede juzgar cuándo
  termina? → A: Lo determina el sistema. El interlocutor solo aprieta "capturar" (inicio); la
  grabación continúa hasta que el sistema reconoce la seña completa, con un máximo de 3 intentos de
  reconocimiento por captura. Agotados los 3 sin superar el umbral, comunica que no entendió.
- Q: ¿Cómo se recolectan los datos de la evaluación de campo (NFR-004/005, SC-003) si nada puede
  salir del dispositivo? → A: Con un build de evaluación separado del de producción. El build de
  producción nunca exporta nada; el de estudio instrumenta las mediciones y requiere consentimiento
  explícito, y en ningún caso registra video.
- Q: ¿Qué techo tiene la descarga de la primera carga, dado el compromiso de funcionar sin conexión?
  → A: El funcionamiento sin conexión sale de alcance. El reconocimiento sigue ocurriendo en el
  dispositivo (la privacidad no cambia: nada se transmite), pero se descarta garantizar y testear
  que la aplicación arranque sin red. Con ello desaparece el presupuesto de descarga.
- Q: ¿Qué criterio define el "subconjunto representativo" de señas de la evaluación de campo, dado
  que ese criterio decide de hecho si la puerta de 0.70 se aprueba? → A: Muestra aleatoria fija de
  10 señas, sorteada con semilla registrada y congelada antes de medir, reutilizada en los tres
  entornos y en toda medición futura.

## User Scenarios & Testing *(mandatory)*

**Actores**:

- **Persona señante** (usuaria principal): sorda o hipoacúsica, usa LSA, realiza señas frente a la
  cámara. Necesita saber si el sistema la está entendiendo.
- **Interlocutor oyente**: no conoce LSA. Lee o escucha la traducción.
- **Sistema**: captura video, extrae keypoints, clasifica la seña y decide si la confianza alcanza
  — todo dentro del dispositivo — y presenta el resultado por texto y voz.

**Contexto de uso**: un único dispositivo compartido, sostenido por el interlocutor oyente. Casa,
calle, transporte público. Cargar la aplicación requiere conexión; reconocer, no.

**Disposición física**: el interlocutor sostiene el dispositivo y apunta la cámara frontal hacia la
persona señante. Durante la captura, la pantalla mira hacia la persona señante: se ve a sí misma y
recibe ahí el feedback de encuadre y de estado de captura, a distancia de conversación. Terminada la
seña, el interlocutor gira el dispositivo hacia sí para leer la traducción, o la escucha por voz sin
girarlo. Las manos de la persona señante quedan libres en todo momento.

**Modelo de interacción**: el inicio de la captura es una acción explícita del interlocutor. El fin
lo determina el sistema: sigue grabando hasta reconocer una seña completa, con un máximo de 3
intentos de reconocimiento por captura antes de darse por vencido.

### User Story 1 - Traducir una seña a texto con confianza explícita (Priority: P1)

El interlocutor apunta la cámara a la persona señante y aprieta "capturar". Ella realiza una seña
del vocabulario LSA64. El sistema graba hasta reconocer la seña completa y muestra en pantalla la
palabra correspondiente junto con qué tan seguro está. Si tras sus 3 intentos no está lo bastante
seguro, dice que no entendió en lugar de mostrar una adivinanza. El interlocutor lee la palabra o la
escucha, y la conversación continúa.

**Why this priority**: es el núcleo del producto. Sin esto no hay traductor. La confianza explícita
va incluida en esta historia y no en una posterior porque presentar una traducción errada como
certeza puede dañar la comunicación más que no traducir (constitution, Principio VIII); un MVP que
adivine no es un MVP entregable.

**Independent Test**: una persona realiza 20 señas conocidas frente a la cámara, iniciando cada
captura; se verifica que cada reconocimiento por encima del umbral se muestra como texto con su
nivel de confianza, y que cada captura agotada sin confianza suficiente produce un mensaje de "no
entendí" sin revelar la etiqueta candidata. Entrega valor por sí sola: permite comunicar palabras
sueltas.

**Acceptance Scenarios**:

1. **Given** la cámara apuntando a la persona correctamente encuadrada, **When** el interlocutor
   inicia la captura y ella realiza una seña del vocabulario que el sistema reconoce por encima del
   umbral, **Then** el sistema muestra la palabra en texto grande junto con un indicador de
   confianza comprensible, en menos de 2 segundos desde que ella terminó de señar.
2. **Given** una captura en curso, **When** el sistema agota sus 3 intentos de reconocimiento sin
   superar el umbral, **Then** detiene la captura, comunica que no entendió, y no muestra ni
   pronuncia ninguna etiqueta candidata.
3. **Given** una captura en curso, **When** la persona realiza un gesto que no pertenece al
   vocabulario LSA64, **Then** el sistema responde "no entendí" en lugar de forzar la seña más
   parecida.
4. **Given** una traducción mostrada, **When** el interlocutor inicia la captura de la siguiente
   seña, **Then** el resultado anterior se desplaza al historial sin ambigüedad sobre cuál es el
   reconocimiento actual.
5. **Given** una captura en curso, **When** la persona señante mira la pantalla, **Then** ve de
   forma inequívoca si el sistema está capturando, intentando reconocer, o ya terminó.

---

### User Story 2 - Escuchar la traducción (interlocutor oyente) (Priority: P2)

El interlocutor oyente no mira la pantalla todo el tiempo (está manejando, cargando bolsas, o
simplemente conversando). El sistema reproduce por voz la palabra reconocida en español.

**Why this priority**: dada la disposición física adoptada, la voz es lo que evita que el
interlocutor tenga que girar el dispositivo después de cada seña. Sin voz el sistema funciona, pero
cada palabra cuesta un giro completo del teléfono, lo que vuelve la conversación notablemente más
lenta. Queda en P2 y no en P1 porque el texto de US1 ya entrega valor por sí solo, pero es la
historia que más mejora el ritmo conversacional.

**Independent Test**: con la voz activada, se realizan 10 señas y se verifica que cada
reconocimiento por encima del umbral se pronuncia de forma inteligible; los que están por debajo del
umbral no se pronuncian.

**Acceptance Scenarios**:

1. **Given** la reproducción por voz activada, **When** se reconoce una seña por encima del umbral,
   **Then** el sistema la pronuncia en español, en voz rioplatense si el dispositivo la ofrece y en
   español neutro en caso contrario.
2. **Given** la reproducción por voz activada, **When** un reconocimiento queda bajo el umbral,
   **Then** el sistema no pronuncia ninguna palabra candidata.
3. **Given** el dispositivo sin ninguna voz en español disponible, **When** se activa la voz,
   **Then** el sistema lo informa explícitamente y continúa funcionando solo con texto.

---

### User Story 3 - Saber si el sistema me está viendo (Priority: P2)

Antes y durante la seña, la persona señante necesita saber si está bien encuadrada y si las
condiciones permiten el reconocimiento. El sistema le da feedback visual continuo: captura activa,
persona detectada (manos y torso en cuadro), o el problema concreto que lo impide.

**Why this priority**: sin este feedback la persona no puede distinguir "el sistema no me entendió"
de "el sistema no me está viendo", y repite señas a ciegas. Es la diferencia entre una herramienta
usable y una frustrante, pero llega después del reconocimiento básico.

**Independent Test**: se provocan deliberadamente cuatro condiciones —manos fuera de cuadro,
persona demasiado lejos, persona demasiado cerca, luz insuficiente— y se verifica que cada una
produce un aviso visual distinto y accionable en menos de 1 segundo.

**Acceptance Scenarios**:

1. **Given** la captura activa, **When** las manos y el torso están en cuadro y detectados,
   **Then** el sistema muestra un indicador visual persistente de "te estoy viendo".
2. **Given** la captura activa, **When** las manos salen del cuadro, **Then** el sistema avisa
   visualmente cuál es el problema y cómo corregirlo, sin depender de sonido.
3. **Given** la captura activa, **When** la luz es insuficiente para detectar keypoints de forma
   estable, **Then** el sistema lo comunica explícitamente en lugar de quedarse en silencio.
4. **Given** la persona demasiado cerca o demasiado lejos, **When** el torso o las manos no entran
   en el encuadre útil, **Then** el sistema indica en qué dirección corregir la distancia.
5. **Given** una captura en curso, **When** las manos salen del cuadro antes de que el sistema
   reconozca la seña, **Then** el sistema descarta la captura con aviso y no la clasifica.

---

### User Story 4 - Repetir y descartar un reconocimiento incorrecto (Priority: P2)

Cuando el sistema entiende mal, la persona señante necesita corregirlo de inmediato: descartar lo
que se mostró (para que el interlocutor no se quede con la palabra equivocada) y repetir la seña.

**Why this priority**: cierra el ciclo de control de la persona señante sobre lo que se comunica en
su nombre. Depende de US1 pero es independientemente verificable.

**Independent Test**: se fuerza un reconocimiento incorrecto, se lo descarta, y se verifica que
desaparece de la presentación y del historial, que queda registrado localmente como descarte, y que
la persona puede repetir la seña con una sola acción.

**Acceptance Scenarios**:

1. **Given** una traducción mostrada, **When** la persona señante la descarta, **Then** el sistema
   la retira de la vista y del historial de sesión, y detiene la reproducción por voz si está en
   curso.
2. **Given** una traducción descartada, **When** se consulta el registro local de descartes,
   **Then** figura la seña descartada con su confianza y su momento, para poder calcular la tasa de
   error percibida.
3. **Given** cualquier estado tras un reconocimiento, **When** la persona quiere reintentar,
   **Then** puede iniciarse una nueva captura con una sola acción, sin reiniciar la cámara.
4. **Given** el registro local de descartes, **When** la persona borra los datos de la sesión,
   **Then** el registro se borra con ellos.

---

### User Story 5 - Historial de la conversación (Priority: P3)

Las señas reconocidas se acumulan en orden para dar contexto: el interlocutor puede releer lo dicho
y la persona señante puede verificar qué se comunicó. El historial se limpia cuando la conversación
termina.

**Why this priority**: mejora la conversación real (una palabra suelta sin contexto se pierde) pero
el producto funciona sin él.

**Independent Test**: se realizan 8 señas consecutivas y se verifica que aparecen en orden
cronológico, que los descartes no figuran, y que una sola acción vacía el historial.

**Acceptance Scenarios**:

1. **Given** varias señas reconocidas en la sesión, **When** el interlocutor mira la pantalla,
   **Then** ve las señas en el orden en que fueron realizadas.
2. **Given** un historial con contenido, **When** la persona lo limpia, **Then** el historial queda
   vacío tras una confirmación, y ese contenido no queda accesible después.
3. **Given** que la sesión termina o la aplicación se cierra, **When** se vuelve a abrir, **Then**
   el historial no se restaura.

---

### User Story 6 - Saber qué puedo señar y qué esperar (Priority: P3)

La persona señante consulta la lista de las 64 señas soportadas con su significado, y la interfaz
deja claro de forma permanente que el vocabulario es limitado y que el sistema es una ayuda, no un
intérprete.

**Why this priority**: encuadra expectativas y evita el fracaso silencioso de señar algo que el
sistema nunca podrá reconocer. Es además una obligación constitucional (Principio VIII) y no puede
omitirse del MVP aunque su prioridad de desarrollo sea baja.

**Independent Test**: una persona que nunca vio el sistema encuentra la lista de señas soportadas y
el aviso de alcance sin ayuda externa, en menos de 30 segundos.

**Acceptance Scenarios**:

1. **Given** la aplicación abierta, **When** la persona busca qué señas puede hacer, **Then**
   accede a una lista de las 64 señas de LSA64 con su significado en español.
2. **Given** cualquier pantalla de la aplicación, **When** la persona la observa, **Then** encuentra
   visible que el vocabulario es limitado y que el sistema asiste pero no reemplaza a un intérprete
   humano.
3. **Given** la aplicación abierta, **When** la persona consulta la información del proyecto,
   **Then** encuentra la atribución al dataset LSA64 y su condición de uso no comercial.

---

### User Story 7 - Ajustar el sistema a mi situación (Priority: P3)

La persona señante ajusta el comportamiento: voz activada o no, qué voz, qué tan exigente es el
sistema antes de aventurar una traducción, y si la vista de cámara se muestra en espejo.

**Why this priority**: la exigencia correcta depende del contexto (una conversación casual tolera
más error que un trámite), pero valores por defecto razonables cubren el MVP.

**Independent Test**: se cambia el umbral de "normal" a "estricto" y se verifica que la proporción
de respuestas "no entendí" aumenta sobre el mismo conjunto de señas realizadas; se verifica que las
preferencias sobreviven a recargar la aplicación.

**Acceptance Scenarios**:

1. **Given** la pantalla de preferencias, **When** la persona activa o desactiva la voz o cambia la
   voz seleccionada, **Then** el cambio se aplica al siguiente reconocimiento sin reiniciar.
2. **Given** la pantalla de preferencias, **When** la persona elige entre estricto, normal y
   permisivo, **Then** el umbral de confianza cambia en consecuencia y la interfaz explica en
   lenguaje llano qué implica cada opción.
3. **Given** el modo espejo activado, **When** la persona se ve en la cámara, **Then** la imagen se
   muestra reflejada, sin que esto altere el reconocimiento.
4. **Given** preferencias modificadas, **When** la persona vuelve a abrir la aplicación en el mismo
   dispositivo, **Then** sus preferencias se conservan.

---

### Edge Cases

**Captura y detección de fin**

- **Duración máxima alcanzada**: la persona nunca produce una seña reconocible. El sistema agota sus
  3 intentos o la duración máxima, descarta con aviso y no clasifica la captura acumulada.
- **La persona no empieza a señar**: entre el inicio de la captura y el comienzo de la seña hay
  tiempo muerto. Ese tramo inicial sin movimiento no debe formar parte de lo que se clasifica.
- **Manos fuera de cuadro a mitad de la seña**: el intento se descarta con aviso; no se clasifica
  una seña truncada.
- **Captura iniciada dos veces**: la segunda acción reinicia la captura o se ignora, pero nunca deja
  el sistema en un estado ambiguo sobre qué se está capturando.
- **Seña realizada sin haber iniciado la captura**: no se produce ningún reconocimiento; el estado
  visible de "no estoy capturando" debe hacer evidente por qué.
- **La persona sigue señando después de que el sistema ya reconoció**: el sistema debe dejar claro
  que la captura terminó, para que ella no continúe creyendo que aún la está viendo.

**Reconocimiento**

- **Seña fuera del vocabulario**: la persona realiza una seña LSA que no está entre las 64. El
  sistema responde "no entendí"; nunca fuerza la clase más parecida.
- **Señas visualmente similares dentro de LSA64**: pares confundibles deben quedar por debajo del
  umbral en lugar de resolverse arbitrariamente hacia una de las dos.
- **Persona zurda o que seña con una sola mano**: el reconocimiento no debe degradarse por la mano
  dominante; la ausencia de una mano es un estado válido, no un error.
- **Más de una persona en cuadro**: el sistema debe indicar que no puede determinar a quién seguir,
  o seguir de forma estable a una sola persona, sin alternar entre ambas.

**Entorno y dispositivo**

- **Permiso de cámara denegado o cámara ocupada por otra aplicación**: mensaje explícito con la
  acción correctiva; nunca una pantalla en negro sin explicación.
- **Luz insuficiente o contraluz**: se comunica como condición ambiental, no como fallo de la
  persona.
- **Voz TTS no disponible en el dispositivo**: se informa y se continúa solo con texto.
- **Rotación del dispositivo a mitad de sesión**: el encuadre y el historial se conservan.
- **El interlocutor gira el dispositivo antes de tiempo**: si lo gira hacia sí mientras la captura
  sigue en curso, el intento se descarta con aviso en lugar de clasificar una seña perdida de vista.
- **Movimiento de cámara durante la seña**: el reconocimiento debe tolerar el pulso normal de una
  mano; un movimiento brusco que saca a la persona de cuadro cae en el caso de manos fuera de cuadro.
- **Dispositivo que no sostiene la tasa de cuadros o el tiempo de cómputo necesarios**: el sistema
  debe advertir la degradación en lugar de producir reconocimientos poco fiables en silencio.
- **Sesión larga**: el rendimiento y la temperatura del dispositivo no deben degradar el
  reconocimiento sin aviso.

**Conectividad**

- **Acceso sin conexión**: la aplicación no puede cargarse; se explica el motivo en lugar de fallar
  en blanco.
- **Conexión perdida a mitad de sesión**: el reconocimiento continúa sin interrupción, porque es
  local. El sistema no debe mostrar errores de red que sugieran lo contrario.

## Requirements *(mandatory)*

### Functional Requirements

**Captura y reconocimiento**

- **FR-001**: El sistema MUST permitir iniciar y detener la captura de cámara desde la interfaz, con
  el control accesible en todo momento durante la sesión.
- **FR-002**: El sistema MUST ejecutar la extracción de keypoints y la clasificación de la seña
  íntegramente en el dispositivo de la persona usuaria. El video crudo MUST NOT transmitirse fuera
  del dispositivo ni almacenarse en ningún medio.
- **FR-003**: El sistema MUST reconocer señas aisladas pertenecientes al vocabulario LSA64 (64
  señas), produciendo por cada captura una única seña candidata acompañada de un valor de confianza.
- **FR-004**: El sistema MUST indicar visualmente, de forma continua, si la captura está activa o
  detenida.
- **FR-005**: El sistema MUST indicar visualmente cuando detecta a la persona (manos y torso dentro
  del cuadro) y cuando deja de detectarla.
- **FR-006**: El sistema MUST avisar cuando el encuadre impide el reconocimiento, distinguiendo al
  menos: manos fuera de cuadro, persona demasiado cerca, persona demasiado lejos, torso no visible.
- **FR-007**: La captura de cada seña MUST iniciarse con una acción explícita del interlocutor. Esa
  acción MUST dejar ambas manos de la persona señante libres: ningún control puede exigirle ocupar
  una mano mientras seña. El sistema MUST NOT inferir por su cuenta cuándo empieza una seña.
- **FR-008**: El fin de la seña MUST determinarlo el sistema. La captura continúa hasta que el
  sistema reconoce una seña completa por encima del umbral de confianza, y MUST realizar como máximo
  3 intentos de reconocimiento por captura. Agotados los 3 intentos sin superar el umbral, el
  sistema MUST detener la captura y comunicar que no entendió, sin presentar ninguna etiqueta
  candidata.
- **FR-009**: El sistema MUST comunicar a la persona señante, de forma continua e inequívoca, en
  cuál de estos estados se encuentra: no capturando, capturando, intentando reconocer, terminado.
  Ella necesita saber si debe seguir señando, repetir o esperar.
- **FR-010**: Toda captura MUST tener una duración máxima. El sistema MUST descartar sin clasificar
  todo intento que alcance esa duración sin reconocimiento, que pierda la detección de manos durante
  la captura, o que no contenga material suficiente para reconocer, y MUST comunicar el motivo del
  descarte.

**Presentación de la traducción**

- **FR-011**: El sistema MUST mostrar la seña reconocida como texto en pantalla, legible por el
  interlocutor a la distancia a la que sostiene el dispositivo (~40 cm), y MUST mostrarla también
  en un tamaño legible por la persona señante a distancia de conversación (1–2 m) antes de que el
  dispositivo se gire, para que ella pueda verificar qué se comunicó en su nombre.
- **FR-012**: El sistema MUST poder reproducir la traducción por voz en español, usando una voz
  rioplatense cuando el dispositivo la ofrezca y español neutro como mínimo aceptable.
- **FR-013**: El sistema MUST mostrar el nivel de confianza del reconocimiento en un formato
  comprensible para personas no técnicas (categorías cualitativas y/o indicador visual), no
  únicamente como un número.
- **FR-014**: El sistema MUST mantener un historial de la sesión con las señas reconocidas en orden
  cronológico.
- **FR-015**: El sistema MUST permitir limpiar el historial de la sesión mediante una acción
  explícita con confirmación.

**Confianza e incertidumbre**

- **FR-016**: El sistema MUST aplicar un umbral de confianza configurable por la persona usuaria.
- **FR-017**: Cuando la confianza del reconocimiento queda por debajo del umbral, el sistema MUST
  comunicar que no entendió y MUST NOT revelar, mostrar ni pronunciar la etiqueta candidata.
- **FR-018**: El sistema MUST permitir iniciar una nueva captura para repetir la seña con una sola
  acción, sin reiniciar la cámara.
- **FR-019**: El sistema MUST permitir descartar un reconocimiento incorrecto, retirándolo de la
  presentación y del historial y deteniendo su reproducción por voz si está en curso.
- **FR-020**: El sistema MUST registrar localmente cada descarte (seña presentada, confianza,
  momento) para permitir el cálculo de la tasa de error percibida, y ese registro MUST borrarse
  junto con los datos de la sesión.

**Robustez de entorno**

- **FR-021**: El sistema MUST funcionar con webcams y cámaras de teléfonos de uso común, sin
  hardware especializado (sin sensores de profundidad, sin guantes, sin marcadores).
- **FR-022**: El sistema MUST funcionar con la persona sentada o de pie y con fondo no controlado.
- **FR-023**: El sistema MUST comunicar de forma explícita y accionable toda condición que impida el
  reconocimiento (luz insuficiente, manos no detectadas, permiso de cámara denegado, cámara no
  disponible, rendimiento insuficiente). El sistema MUST NOT fallar en silencio.

**Configuración**

- **FR-024**: El sistema MUST permitir activar y desactivar la reproducción por voz, y elegir entre
  las voces en español disponibles en el dispositivo.
- **FR-025**: El sistema MUST permitir ajustar el umbral de confianza mediante un control simple con
  opciones nombradas en lenguaje llano (estricto / normal / permisivo), explicando qué implica cada
  una.
- **FR-026**: El sistema MUST permitir activar y desactivar el modo espejo de la vista de cámara,
  sin que esta opción afecte el resultado del reconocimiento. El modo espejo MUST venir activado por
  defecto, porque la persona señante se ve a sí misma en la pantalla y la imagen reflejada es la
  única que le permite corregir su encuadre de forma intuitiva.
- **FR-027**: El sistema MUST conservar las preferencias de la persona usuaria localmente entre
  sesiones en el mismo dispositivo.

**Vocabulario y encuadre de expectativas**

- **FR-028**: El sistema MUST mostrar la lista completa de las 64 señas soportadas con su
  significado en español.
- **FR-029**: La interfaz MUST declarar de forma visible y persistente que el vocabulario es
  limitado y que el sistema es una herramienta de asistencia, no un reemplazo de intérpretes
  humanos.
- **FR-030**: El sistema MUST mostrar la atribución al dataset LSA64 (autores, paper/sitio) y su
  condición de uso no comercial en la documentación accesible desde la interfaz.

**Conectividad**

- **FR-031**: El sistema MUST requerir conexión únicamente para cargarse. Una vez cargado, la
  pérdida de conectividad MUST NOT interrumpir ni degradar el reconocimiento, que es local, ni
  producir avisos de red que confundan a la persona usuaria. Arrancar la aplicación sin conexión
  está fuera de alcance (ver Out of Scope).

### Non-Functional Requirements

**Performance del reconocimiento**

- **NFR-001**: El modelo MUST sostener accuracy >= 0.85 medida con split POR SUJETO sobre LSA64
  (personas del conjunto de test nunca vistas en entrenamiento), en la misma configuración que se
  ejecuta en el dispositivo.
- **NFR-002**: Toda métrica reportada MUST provenir de un split por sujeto. El split aleatorio está
  prohibido para reportes.

**Latencia**

- **NFR-003**: El tiempo entre el momento en que la persona señante termina de señar y la
  presentación de la traducción MUST ser menor a 2 segundos en el dispositivo de referencia definido
  en el protocolo de prueba. Al no haber servicio remoto, este presupuesto es íntegramente de
  cómputo local, y los hasta 3 intentos de reconocimiento de FR-008 se consumen dentro de él.

**Robustez medible**

- **NFR-004**: MUST existir un protocolo de prueba documentado que cubra al menos 3 entornos:
  interior con buena luz, interior con luz pobre, y exterior o en movimiento. Las mediciones se
  recolectan con el build de evaluación de NFR-017.
- **NFR-005**: El desempeño MUST reportarse por separado para cada entorno del protocolo, y MUST
  alcanzar al menos 0.70 de accuracy en cada uno de los tres para dar la robustez por cumplida.
  Este umbral es provisional: se fija sin evidencia previa de campo y MUST revisarse tras la primera
  medición completa, documentando el cambio.
- **NFR-018**: El subconjunto de señas de la evaluación de campo MUST ser una muestra aleatoria de
  10 señas del vocabulario, sorteada con semilla registrada y congelada ANTES de la primera
  medición. MUST reutilizarse idéntico en los tres entornos y en toda medición posterior. Cambiar el
  subconjunto MUST documentarse como cambio de protocolo, no aplicarse en silencio: es el criterio
  que impide que la puerta de NFR-005 se acomode eligiendo señas fáciles.

**Privacidad**

- **NFR-006**: En el build de producción, ningún dato de la sesión —video, frames, keypoints,
  transcripciones, métricas— MUST abandonar el dispositivo. El reconocimiento local hace innecesaria
  toda transmisión. Esta política es más estricta que el mínimo constitucional (Principio VII permite
  transmitir keypoints) y por lo tanto compatible con él. La única excepción es el build de
  evaluación de NFR-017, que nunca se distribuye como producto.
- **NFR-007**: No se almacena video en ningún momento ni en ningún medio.
- **NFR-008**: El historial de sesión y el registro de descartes MUST ser locales al dispositivo y
  borrables por la persona usuaria.
- **NFR-017**: MUST existir un build de evaluación, separado del de producción, que instrumente las
  mediciones de NFR-004 y NFR-005. Ese build MUST: (a) requerir consentimiento explícito e informado
  antes de cada sesión de medición; (b) declarar de forma visible y permanente que está registrando;
  (c) no registrar ni exportar video bajo ninguna circunstancia; (d) exportar únicamente métricas
  (seña esperada, seña reconocida, confianza, cantidad de intentos, entorno, dispositivo). El build
  de producción MUST NOT contener esta instrumentación.

**Accesibilidad y usabilidad**

- **NFR-009**: La interfaz MUST ser utilizable por una persona sorda sin instrucciones externas:
  iconografía clara y feedback visual para todo estado relevante.
- **NFR-010**: Ningún estado, alerta o error del sistema MUST depender únicamente de sonido.
- **NFR-011**: Todo elemento dirigido a la persona señante (encuadre, estado de captura, resultado,
  avisos) MUST ser legible a 1–2 metros, la distancia a la que se encuentra del dispositivo que
  sostiene el interlocutor. Todo elemento dirigido al interlocutor MUST ser legible a ~40 cm.
- **NFR-016**: El reconocimiento MUST tolerar el movimiento propio de una cámara sostenida a pulso
  por una segunda persona, sin exigir que el dispositivo esté apoyado o estabilizado.

**Legales y de licencia**

- **NFR-012**: El uso de LSA64 MUST respetar su licencia no comercial, con cita a sus autores
  (LIDI, UNLP).
- **NFR-013**: El proyecto completo MUST mantenerse compatible con esa restricción no comercial.

**Calidad**

- **NFR-014**: El contrato de datos de keypoints (201 coordenadas: 63 + 63 + 75, centrado en el
  punto medio de los hombros) MUST tener tests automatizados bloqueantes que verifiquen a cada
  productor de keypoints contra secuencias de referencia.
- **NFR-015**: Las reglas del pipeline (captura, extracción, clasificación, decisión de confianza,
  post-procesamiento) MUST estar separadas de la interfaz, sin que ningún cliente conozca detalles
  internos del modelo.

### Constitutional Requirements *(mandatory — see `.specify/memory/constitution.md`)*

- **Privacy (Principle VII)**: nada sale del dispositivo en el build de producción. El
  reconocimiento es local, por lo que no se transmiten ni video ni keypoints. El build de evaluación
  (NFR-017) exporta solo métricas, con consentimiento, y nunca video. Cubierto por FR-002, NFR-006,
  NFR-007, NFR-017.
- **Explicit confidence (Principle VIII)**: por debajo del umbral el sistema dice "no entendí" y no
  revela la etiqueta candidata (FR-017). La confianza se muestra siempre (FR-013). La UI declara de
  forma persistente que Helpi asiste y no reemplaza a un intérprete (FR-029).
- **Latency (Principle IX)**: presupuesto de 2 s desde que la persona termina de señar hasta la
  presentación (NFR-003), íntegramente de cómputo en el dispositivo, incluidos los hasta 3 intentos
  de reconocimiento.
- **Temporal segmentation debt (Principle XII)**: esta feature resuelve parcialmente la deuda —
  detecta el fin de la seña con el inicio conocido y un tope de 3 intentos (FR-008)— en lugar de
  agravarla. La segmentación continua sin delimitación humana sigue pendiente y fuera de alcance.
- **Evaluation (Principle V)**: métrica reportable = accuracy con split por sujeto sobre LSA64
  (sujeto held-out), contra el baseline 0.85 (NFR-001, NFR-002). La evaluación de campo por entorno
  (NFR-004, NFR-005) es una medición distinta y se reporta por separado.
- **Data contract (Principle IV)**: los productores de keypoints de esta feature son el cliente de
  la aplicación y el preprocesamiento de entrenamiento; ambos quedan cubiertos por los tests de
  contrato bloqueantes de NFR-014.

### Key Entities

- **Captura**: grabación iniciada por el interlocutor y terminada por el sistema. Contiene hasta 3
  intentos de reconocimiento. Puede resultar en una seña reconocida, en un "no entendí" tras agotar
  los intentos, o en un descarte por invalidez (duración máxima, material insuficiente, manos
  perdidas).
- **Intento de reconocimiento**: cada una de las hasta 3 evaluaciones que el sistema realiza sobre
  la captura en curso para decidir si la seña ya terminó y qué seña es. Atributos: número de intento,
  etiqueta candidata, confianza.
- **Seña reconocida**: resultado de un intento válido. Atributos: etiqueta del vocabulario (solo si
  supera el umbral), valor de confianza, momento, estado (presentada / descartada / bajo umbral).
- **Secuencia de keypoints**: representación numérica anónima de la seña realizada, de largo fijo,
  producida y consumida dentro del dispositivo.
- **Vocabulario LSA64**: conjunto cerrado de 64 señas con su significado en español. Es la frontera
  de lo reconocible.
- **Sesión**: conversación en curso. Contiene el historial ordenado de señas reconocidas; es local,
  efímera y borrable.
- **Registro de descartes**: lista local de reconocimientos que la persona señante marcó como
  incorrectos, con confianza y momento; base de la tasa de error percibida.
- **Preferencias**: umbral de confianza (estricto/normal/permisivo), voz activada y voz elegida,
  modo espejo. Locales al dispositivo, persistentes.
- **Condición de entorno**: estado detectado que impide o degrada el reconocimiento (luz, encuadre,
  detección, rendimiento, permisos), con su mensaje accionable asociado.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El reconocimiento alcanza al menos 0.85 de accuracy sobre las 64 señas de LSA64 con
  personas de test nunca vistas en entrenamiento, en la configuración que efectivamente corre en el
  dispositivo.
- **SC-002**: En al menos el 95% de los reconocimientos, la traducción se presenta dentro de los 2
  segundos posteriores al momento en que la persona termina de señar, en el dispositivo de
  referencia.
- **SC-003**: El desempeño se mide y reporta por separado en los 3 entornos del protocolo (interior
  con buena luz, interior con luz pobre, exterior/movimiento), sobre el subconjunto congelado de 10
  señas definido en NFR-018, con al menos 10 intentos por seña en cada entorno, y alcanza al menos
  0.70 de accuracy en cada entorno.
- **SC-004**: En el build de producción, cero peticiones de red salen del dispositivo durante una
  sesión completa, verificable mediante inspección del tráfico saliente.
- **SC-013**: El build de producción no contiene ninguna ruta de código de instrumentación o
  exportación de la evaluación, verificable por inspección del artefacto distribuido.
- **SC-005**: En una auditoría de 100 reconocimientos por debajo del umbral, en cero casos se
  muestra o pronuncia una etiqueta candidata.
- **SC-006**: Al menos el 80% de las personas señantes que usan el sistema por primera vez logran su
  primer reconocimiento correcto en menos de 2 minutos, sin instrucciones externas (prueba con un
  mínimo de 5 participantes).
- **SC-007**: El 100% de los interlocutores evaluados lee correctamente la traducción sosteniendo el
  dispositivo (~40 cm), y el 100% de las personas señantes evaluadas lee correctamente su propio
  feedback de encuadre y estado a 1–2 metros, en condiciones de luz normales.
- **SC-008**: Al final de una sesión de prueba, la tasa de error percibida (descartes sobre
  reconocimientos presentados) es calculable a partir de los datos locales, sin instrumentación
  adicional.
- **SC-009**: Toda condición que impide el reconocimiento produce un aviso visible en menos de 1
  segundo; cero casos de fallo silencioso en el protocolo de prueba.
- **SC-010**: Una persona que abre la aplicación por primera vez encuentra el vocabulario soportado
  y el aviso de "asistencia, no intérprete" en menos de 30 segundos, sin ayuda.
- **SC-011**: En el 100% de los intentos inválidos provocados (duración máxima alcanzada sin
  reconocer, material insuficiente, manos perdidas), el sistema descarta y explica el motivo, y en
  cero casos clasifica la captura.
- **SC-012**: Ninguna captura ejecuta más de 3 intentos de reconocimiento, verificable por
  instrumentación local.

## Out of Scope

- Traducción de frases continuas (dataset LSA-T) — fase futura.
- Dirección inversa: voz o texto hacia señas.
- Detección automática del **inicio** de la seña: el inicio de la captura siempre es una acción
  humana. La segmentación temporal continua sobre un stream sin delimitar —la deuda del Principio
  XII en su forma completa— queda fuera de este MVP. La detección del **fin** sí está en alcance
  (FR-008), acotada por un inicio conocido y por un máximo de 3 intentos.
- Segmentación de frases encadenadas: varias señas dentro de una misma captura.
- Inferencia en servidor y cualquier servicio remoto de reconocimiento.
- **Funcionamiento sin conexión**: arrancar la aplicación sin red, empaquetado y cacheo local para
  uso offline, y todo presupuesto de tamaño de descarga asociado. El reconocimiento sigue siendo
  local —de ahí que la privacidad no cambie— pero cargar la aplicación requiere conexión y no se
  garantiza ni se testea el arranque sin ella. Es la primera candidata a recuperar si el MVP valida,
  porque el subte y la calle son escenarios centrales del producto.
- Pulido de glosa a frase natural con LLM — deseable, prioridad baja, solo si el resto del MVP está
  completo.
- Cuentas de usuario y sincronización en la nube.
- Señas fuera del vocabulario LSA64.
- Aplicación móvil nativa: el MVP es web. Una iteración con Flutter queda condicionada a que el MVP
  valide.

## Assumptions

- **Un solo dispositivo, sostenido por el interlocutor**: el interlocutor oyente sostiene el
  dispositivo y apunta la cámara frontal a la persona señante, que se ve a sí misma. Tras la seña,
  el interlocutor gira el dispositivo para leer, o escucha la voz sin girarlo. No se contempla
  emparejar dos dispositivos.
- **La cámara se sostiene a pulso**: el dispositivo no está apoyado ni estabilizado durante la
  captura. El movimiento de cámara es la condición normal de uso, no un caso degradado.
- **MVP web**: la persona accede desde un navegador en computadora o teléfono; no hay instalación.
  Cargar la aplicación requiere conexión; una vez cargada, el reconocimiento no la necesita.
- **La privacidad no depende del alcance offline**: nada se transmite porque la inferencia es local,
  no porque la aplicación esté empaquetada para uso sin conexión. Descartar el funcionamiento
  offline no toca NFR-006.
- **Inicio manual, fin automático**: el interlocutor inicia la captura; el sistema decide cuándo
  terminó la seña. Se asume que conocer el inicio reduce el problema de segmentación lo suficiente
  como para hacerlo tratable, a diferencia de la ventana deslizante sobre stream continuo que la
  fase exploratoria dejó sin resolver.
- **Riesgo de parada opcional (optional stopping)**: si el criterio para dejar de grabar es "hasta
  que el clasificador esté seguro", el sistema busca activamente una ventana que produzca confianza
  alta, lo que infla la confianza aparente y aumenta los falsos positivos. Esto presiona
  directamente el Principio VIII (preferimos el silencio al error). El tope de 3 intentos acota el
  problema pero no lo elimina: la fase de plan MUST cuantificar cuánto se infla la confianza con 3
  evaluaciones y compensar el umbral en consecuencia, midiéndolo con split por sujeto.
- **Tiempo muerto inicial**: entre el inicio de la captura y el comienzo real de la seña hay un
  tramo sin movimiento que no existe en los datos de entrenamiento (LSA64 versión cut contiene señas
  ya recortadas). Cómo se excluye ese tramo es decisión de la fase de plan, pero incluirlo sin más
  desalinearía el contrato del Principio IV.
- **Dispositivo de referencia**: gama media de uso común (teléfono o notebook de los últimos ~4
  años). Los modelos concretos se fijan en el protocolo de prueba durante la fase de plan; NFR-003 y
  SC-002 se miden contra ellos.
- **Tensión reconocimiento local vs. baseline**: ejecutar el modelo en el dispositivo puede exigir
  una versión más liviana que la validada en la fase exploratoria. NFR-001 y SC-001 aplican a la
  configuración que efectivamente corre en el dispositivo; toda caída por debajo de 0.85 requiere
  justificación escrita según el Principio V. Resolver esta tensión es trabajo de la fase de plan.
- **Vocabulario cerrado**: las 64 etiquetas de LSA64 se presentan con su traducción al español; no
  hay ampliación de vocabulario por parte de la persona usuaria.
- **Historial efímero**: el historial de sesión no sobrevive al cierre de la aplicación; solo las
  preferencias persisten.
- **Umbrales por defecto**: "normal" es el valor por defecto; "estricto" y "permisivo" desplazan el
  umbral en direcciones opuestas. Los valores numéricos concretos se fijan en la fase de plan a
  partir de la curva de confianza medida, no en esta especificación.
- **Umbral de robustez provisional**: el 0.70 de NFR-005 es una estimación previa a toda medición de
  campo; se revisa tras la primera corrida del protocolo.
- **Muestra de campo congelada por sorteo**: se elige el azar con semilla registrada, y no una
  selección por criterio experto, porque es la única forma de que el subconjunto no pueda ajustarse
  después de ver los resultados. El costo es que el sorteo puede dejar afuera los pares confundibles
  más interesantes; ese análisis se hace por separado sobre la matriz de confusión del sujeto
  held-out, sin tocar la muestra de campo.
- **Sin analítica remota**: el registro de descartes es local y no se envía a ningún servidor; sirve
  para evaluación con participantes, no para telemetría de producto.
- **Dos builds**: producción (sin instrumentación, sin salida de datos) y evaluación (instrumentado,
  con consentimiento, nunca distribuido como producto). El costo asumido es mantener y verificar dos
  configuraciones; a cambio, la promesa de privacidad del producto no admite excepciones ni matices
  que haya que explicarle a la persona usuaria.
- **Una persona en cuadro**: el escenario de diseño supone una única persona señante frente a la
  cámara.
- **Reconocimiento de señas aisladas**: la persona realiza una seña por vez, no frases encadenadas.
- **Voces TTS del dispositivo**: la calidad y disponibilidad de voces en español depende del
  sistema operativo del usuario; el sistema no incorpora voces propias.
- **Evaluación con LSA64**: las métricas reportables se miden sobre LSA64 con sujeto held-out; las
  pruebas en entornos reales son complementarias y se reportan por separado.
