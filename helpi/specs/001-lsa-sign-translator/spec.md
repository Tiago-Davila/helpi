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

## Decisiones de diseño registradas

### DD-001 — Quién inicia la captura: se agrega un modo autónomo y pasa a ser el predeterminado

**Revisa**: las decisiones Q1 y Q2 de la sesión de clarify del 2026-07-25.

**Problema detectado**: tal como quedaron Q1 y Q2, el sistema exigía que el interlocutor oyente
sostuviera el dispositivo y accionara el control de inicio. Eso significa que **la persona sorda no
podía iniciar una comunicación sin la cooperación previa de un desconocido**: tenía que lograr que
aceptara tomar su teléfono, entendiera qué hacer y apretara un botón — todo antes de poder decir la
primera palabra. Para una herramienta de accesibilidad eso invierte la relación que se busca
reparar: la persona que necesita comunicarse queda dependiendo de la buena voluntad y la
comprensión de la otra parte justo en el momento en que aún no puede explicarse.

**Alternativas evaluadas**:

| Alternativa | Evaluación |
|-------------|------------|
| La persona señante inicia la captura ella misma | Viable. Requiere resolver que el movimiento de acercarse al dispositivo y volver a posición no contamine la secuencia clasificada. Se resuelve con cuenta regresiva (FR-033). |
| Dispositivo apoyado (mesa, baranda, soporte, mochila) en lugar de sostenido | Viable y es la disposición natural del modo autónomo. Pierde la estabilidad de encuadre que da una persona apuntando, pero gana independencia total. |
| Inicio por gesto detectado | Descartado: es detección automática de inicio de seña, explícitamente fuera de alcance (deuda del Principio XII). |
| Temporizador de repetición automática | Descartado para el MVP: multiplica capturas vacías y agrava el riesgo de parada opcional de NFR-019. |
| Mantener solo el modo asistido | **Descartado.** Deja al usuario primario sin forma de iniciar la comunicación por sí mismo. |

**Decisión**: se definen **dos modos de captura**. El **modo autónomo** —dispositivo apoyado, inicio
por la persona señante, cuenta regresiva— es el **predeterminado**. El **modo asistido** —dispositivo
sostenido por el interlocutor, que inicia la captura— queda disponible como alternativa para cuando
la otra parte ya está cooperando, situación en la que es más rápido y da mejor encuadre.

**Justificación**: ninguna funcionalidad de accesibilidad puede depender de que un tercero acepte
colaborar antes de que la persona pueda comunicarse. El modo asistido no se elimina porque, una vez
establecida la cooperación, es genuinamente mejor: el encuadre lo controla alguien que ve el
resultado, y desaparece la cuenta regresiva.

**Costo asumido**: dos modos que mantener, probar y explicar en la interfaz. En modo autónomo el
encuadre es peor —nadie corrige el ángulo— y la cuenta regresiva agrega tiempo antes de cada seña.
Además, con el dispositivo apoyado mirando a la persona señante, **la voz (US2) pasa a ser el único
canal que alcanza al interlocutor sin manipular el aparato**: US1 sola entrega bastante menos valor
en este modo que en el asistido.

**Si el interlocutor se niega a sostener el dispositivo**: el sistema funciona igual, en modo
autónomo. Esa era exactamente la situación que dejaba al usuario primario sin salida.

## User Scenarios & Testing *(mandatory)*

**Actores** (personas con interés en el sistema; el sistema mismo no es un actor sino el sujeto bajo
prueba):

- **Persona señante** (usuaria principal): sorda o hipoacúsica, usa LSA, realiza señas frente a la
  cámara. Necesita saber si el sistema la está entendiendo.
- **Interlocutor oyente**: no conoce LSA. **Opera el dispositivo**: lo sostiene, encuadra a la
  persona señante, inicia cada captura, y lee o escucha la traducción.

**Sistema bajo prueba**: captura video, extrae keypoints, clasifica la seña y decide si la confianza
alcanza —todo dentro del dispositivo— y presenta el resultado por texto y voz.

**Contexto de uso**: un único dispositivo compartido. Casa, calle, transporte público. Cargar la
aplicación requiere conexión; reconocer, no.

**Disposición física — dos modos** (ver DD-001). En ambos, la cámara frontal apunta a la persona
señante, que se ve a sí misma en la pantalla y recibe ahí el feedback de encuadre y de estado de
captura. En ambos, sus manos quedan libres.

- **Modo autónomo (predeterminado)**: el dispositivo queda apoyado en una superficie o soporte
  (mesa, baranda, mochila). La persona señante inicia la captura ella misma y una cuenta regresiva
  le da tiempo de volver a posición. No requiere cooperación de nadie. El resultado llega al
  interlocutor por voz, o girando el dispositivo después.
- **Modo asistido**: el interlocutor sostiene el dispositivo, encuadra e inicia la captura.
  Terminada la seña, lo gira hacia sí para leer, o escucha la voz sin girarlo. Más rápido y con
  mejor encuadre, pero exige que la otra parte ya esté cooperando.

**Modelo de interacción**: el inicio de la captura es una acción humana explícita —de la persona
señante en modo autónomo, del interlocutor en modo asistido. El fin lo determina el sistema: sigue
grabando hasta que un intento de reconocimiento supera el umbral o se alcanza la duración máxima,
con un máximo de 3 intentos por captura antes de darse por vencido.

### User Story 1 - Traducir una seña a texto con confianza explícita (Priority: P1)

La persona señante apoya el dispositivo, aprieta "capturar" y una cuenta regresiva le da tiempo de
volver a posición. Realiza una seña del vocabulario LSA64. El sistema graba hasta reconocerla y
muestra en pantalla la palabra correspondiente junto con qué tan seguro está. Si tras sus 3 intentos
no está lo bastante seguro, dice que no entendió en lugar de mostrar una adivinanza. El interlocutor
escucha la palabra, o la lee cuando le acercan el dispositivo, y la conversación continúa.

En modo asistido (US8) el ciclo es el mismo salvo que quien apoya, encuadra e inicia es el
interlocutor, y no hay cuenta regresiva.

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

1. **Given** la cámara apuntando a la persona correctamente encuadrada, **When** se inicia la
   captura en cualquiera de los dos modos y ella realiza una seña del vocabulario que el sistema
   reconoce por encima del umbral, **Then** el sistema muestra la palabra en texto grande junto con
   su categoría de confianza, en menos de 2 segundos desde que ella terminó de señar.
6. **Given** el modo autónomo con el dispositivo apoyado, **When** la persona señante inicia la
   captura, **Then** una cuenta regresiva visible de al menos 3 segundos le permite volver a
   posición, y ni su movimiento de acercamiento ni el de regreso forman parte de lo que se
   clasifica.
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

**Independent Test**: con la voz activada se realizan 20 reproducciones y ≥5 oyentes
hispanohablantes las transcriben; el criterio es ≥95% de palabras transcritas correctamente. Se
verifica además que ningún reconocimiento bajo umbral se pronuncia.

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
   permisivo, **Then** el umbral de confianza cambia en consecuencia y cada opción muestra una
   explicación de una frase sobre qué implica elegirla.
3. **Given** el modo espejo activado, **When** la persona se ve en la cámara, **Then** la imagen se
   muestra reflejada, sin que esto altere el reconocimiento.
4. **Given** preferencias modificadas, **When** la persona vuelve a abrir la aplicación en el mismo
   dispositivo, **Then** sus preferencias se conservan.

---

### User Story 8 - Operar la captura para otra persona (modo asistido) (Priority: P3)

El interlocutor oyente, que no conoce LSA y probablemente nunca usó la aplicación, acepta sostener
el dispositivo: encuadra a la persona señante, inicia cada captura y necesita saber cuándo terminó
— todo mirando una pantalla que apunta hacia la otra persona, no hacia él.

**Why this priority**: bajó de P2 a P3 tras DD-001. El modo asistido dejó de ser la única vía de uso
y pasó a ser una mejora para cuando la otra parte ya coopera. Sigue valiendo la pena porque en esa
situación da mejor encuadre y elimina la cuenta regresiva, pero el sistema ya es utilizable sin él.

**Independent Test**: 5 personas que no conocen LSA ni la aplicación reciben el dispositivo y la
consigna "grabá a esta persona para que el sistema la entienda", sin más instrucción; se mide
cuántas logran un encuadre válido y una captura completa en menos de 1 minuto.

**Acceptance Scenarios**:

1. **Given** el dispositivo en manos del interlocutor con la pantalla mirando a la persona señante,
   **When** necesita saber si el encuadre es correcto, **Then** dispone de una señal perceptible
   desde su lado, sin ver la pantalla principal, que le indica si el sistema detecta a la persona.
2. **Given** una captura en curso, **When** termina, **Then** el interlocutor lo percibe sin
   necesidad de girar el dispositivo.
3. **Given** la sesión en curso, **When** el interlocutor quiere detener la cámara, **Then** dispone
   de un control accesible en todo momento.
4. **Given** que el interlocutor giró el dispositivo para leer, **When** lo vuelve a girar hacia la
   persona señante, **Then** puede iniciar la siguiente captura sin reconfigurar nada.

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
- **El interlocutor se niega a sostener el dispositivo, o no entiende qué se le pide**: el sistema
  funciona igual en modo autónomo, que es el predeterminado. En ningún caso la imposibilidad de
  comunicarse depende de la cooperación del interlocutor (DD-001).
- **No hay superficie donde apoyar el dispositivo y el interlocutor no coopera**: es la situación de
  peor caso. El sistema no la resuelve; la interfaz MUST sugerir el modo asistido en lugar de dejar
  a la persona intentando capturas con el dispositivo en la mano, que le ocuparía una mano y
  violaría FR-007.
- **La cuenta regresiva termina antes de que la persona esté en posición**: cae en el caso de manos
  no detectadas y la captura se descarta con aviso; puede reiniciarse con una sola acción.
- **La persona sigue señando después de que el sistema ya reconoció**: el sistema debe dejar claro
  que la captura terminó, para que ella no continúe creyendo que aún la está viendo.

**Reconocimiento**

- **Seña fuera del vocabulario**: la persona realiza una seña LSA que no está entre las 64. El
  sistema responde "no entendí"; nunca fuerza la clase más parecida.
- **Seña del vocabulario mal ejecutada o incompleta**: caso frecuente en quien está aprendiendo. El
  comportamiento esperado es el mismo que para una seña fuera del vocabulario: "no entendí". El
  sistema no intenta corregir ni completar lo que vio.
- **Señas visualmente similares dentro de LSA64**: pares confundibles deben quedar por debajo del
  umbral en lugar de resolverse arbitrariamente hacia una de las dos.
- **Seña mucho más rápida o más lenta que en el dataset**: el muestreo a largo fijo por linspace
  normaliza la duración, de modo que una seña muy lenta o muy rápida altera el contenido temporal
  efectivo de la secuencia. El sistema debe tratarla como cualquier otra captura; si no alcanza el
  umbral, responde "no entendí". El rango de duración con el que el reconocimiento se mantiene sobre
  el umbral MUST medirse y documentarse en la fase de plan, no asumirse.
- **Persona zurda**: LSA64 no declara la lateralidad de sus sujetos, de modo que el sistema **no
  puede afirmar** que el reconocimiento es independiente de la mano dominante. Es una limitación
  conocida: MUST reportarse la accuracy desagregada por lateralidad en la evaluación de campo, y si
  hay diferencia significativa MUST declararse en la interfaz y en la documentación.
- **Seña bimanual con una mano ocluida o fuera de cuadro**: distinto de señar con una sola mano. La
  seña es válida pero la observación es parcial. El sistema descarta la captura con aviso de encuadre
  en lugar de clasificar con información faltante.
- **Persona que seña con una sola mano**: la ausencia de una mano es un estado válido de entrada, no
  un error, cuando la seña efectivamente es de una mano.
- **Más de una persona en cuadro**: el sistema sigue a la persona de mayor área de torso detectada al
  iniciarse la captura y la mantiene hasta que la captura termina, sin alternar. Si dos personas
  tienen áreas equivalentes, descarta la captura y lo comunica.

**Entorno y dispositivo**

- **Permiso de cámara denegado o cámara ocupada por otra aplicación**: mensaje explícito con la
  acción correctiva; nunca una pantalla en negro sin explicación.
- **Cámara ocluida (lente tapada)**: distinto de los anteriores, porque la cámara entrega imagen
  válida pero sin persona detectable. Cae en el camino de "no detecto a nadie", con aviso propio que
  sugiere revisar la lente.
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
  íntegramente en el dispositivo de la persona usuaria. (Qué no puede cruzar la frontera del
  dispositivo se especifica en NFR-006; qué no puede persistirse, en NFR-007.)
- **FR-003**: El sistema MUST reconocer señas aisladas pertenecientes al vocabulario LSA64 (64
  señas), produciendo por cada captura una única seña candidata acompañada de un valor de confianza.
- **FR-004**: El sistema MUST indicar visualmente, de forma continua, si la captura está activa o
  detenida.
- **FR-005**: El sistema MUST indicar visualmente cuando detecta a la persona (manos y torso dentro
  del cuadro) y cuando deja de detectarla.
- **FR-006**: El sistema MUST avisar cuando el encuadre impide el reconocimiento, distinguiendo al
  menos: manos fuera de cuadro, persona demasiado cerca, persona demasiado lejos, torso no visible.
- **FR-007**: La captura de cada seña MUST iniciarse con una acción humana explícita, en cualquiera
  de los dos modos de DD-001: la persona señante en **modo autónomo**, el interlocutor en **modo
  asistido**. El sistema MUST ofrecer el modo autónomo por defecto y MUST permitir cambiar de modo
  sin reiniciar la sesión. En ambos modos la acción MUST dejar ambas manos de la persona señante
  libres durante la seña: ningún control puede exigirle ocupar una mano mientras seña. El sistema
  MUST NOT inferir por su cuenta cuándo empieza una seña.
- **FR-033**: En modo autónomo, entre la acción de inicio y el comienzo de la captura el sistema
  MUST mostrar una cuenta regresiva visible de al menos 3 segundos, para que la persona señante
  vuelva a su posición. El movimiento de acercarse al dispositivo y de regresar MUST NOT formar
  parte de la secuencia que se clasifica.
- **FR-008**: El fin de la seña MUST determinarlo el sistema. Una captura MUST terminar cuando un
  intento de reconocimiento supera el umbral de confianza, o cuando se alcanza la duración máxima de
  FR-010, lo que ocurra primero. El sistema MUST realizar como máximo 3 intentos de reconocimiento
  por captura. Agotados los 3 sin superar el umbral, MUST detener la captura y comunicar que no
  entendió, sin presentar ninguna etiqueta candidata. *"Seña completa" no es un estado observable
  independiente del clasificador y MUST NOT usarse como criterio de corte.*
- **FR-009**: El sistema MUST comunicar a la persona señante, de forma continua, en cuál de estos
  cuatro estados mutuamente excluyentes se encuentra: no capturando, capturando, intentando
  reconocer, terminado. Cada estado MUST tener un indicador visual distinto en color y en forma. En
  prueba con ≥5 personas señantes, ≥80% MUST identificar el estado correcto sin explicación previa.
- **FR-010**: Toda captura MUST tener una duración máxima declarada. El sistema MUST descartar sin
  clasificar toda captura que alcance esa duración sin reconocimiento, que pierda la detección de
  manos durante la captura, o que contenga menos frames con manos detectadas que el mínimo requerido
  por el preprocesamiento para muestrear una secuencia. MUST comunicar cuál de los tres motivos
  causó el descarte.
- **FR-032**: El sistema MUST emitir una señal audible dirigida al interlocutor que le permita
  conocer, sin ver la pantalla, si el sistema detecta a la persona señante y cuándo la captura
  terminó. Esta señal complementa el feedback visual de FR-009, que sigue siendo la vía de la
  persona señante; ningún estado queda cubierto solo por sonido (NFR-010) ni solo por imagen.

**Presentación de la traducción**

- **FR-011**: El sistema MUST mostrar la seña reconocida como texto en pantalla, dimensionado según
  NFR-011 para ser legible por la persona señante a 1–2 m antes de que el dispositivo se gire, y por
  el interlocutor a ~40 cm después. Ella necesita poder verificar qué se comunicó en su nombre.
- **FR-012**: El sistema MUST poder reproducir la traducción por voz en español. La selección de voz
  MUST seguir este orden de preferencia, tomando la etiqueta de locale que declara el dispositivo:
  (1) `es-AR`; (2) cualquier otra variante rioplatense declarada (`es-UY`); (3) cualquier `es-*`;
  (4) ninguna disponible → FR-012 no aplica y rige el escenario 3 de US2.
- **FR-013**: El sistema MUST presentar el nivel de confianza en 3 categorías nombradas —alta, media,
  no entendí— acompañadas de indicador visual, y MUST NOT presentarlo únicamente como número. En
  prueba con ≥5 participantes sin formación técnica, ≥80% MUST interpretar correctamente qué
  significa cada categoría. En el caso "no entendí" MUST presentarse solo esa categoría, sin valor
  numérico y sin etiqueta candidata.
- **FR-014**: El sistema MUST mantener un historial de la sesión con las señas reconocidas en orden
  cronológico.
- **FR-015**: El sistema MUST permitir limpiar el historial de la sesión mediante una acción
  explícita con confirmación.

**Confianza e incertidumbre**

- **FR-016**: El sistema MUST aplicar un umbral de confianza configurable, con estos valores
  iniciales sobre la confianza normalizada del clasificador (0–1): **estricto = 0,85**,
  **normal = 0,70** (predeterminado), **permisivo = 0,55**. Son **valores provisionales de
  arranque**, fijados por analogía con el baseline y sin curva de confianza medida. MUST
  recalibrarse con la medición obligatoria de NFR-019 antes del cierre del proyecto, y los valores
  finales MUST documentarse junto con la medición que los sustenta.
- **FR-017**: Cuando ningún intento de reconocimiento de la captura supera el umbral, el sistema
  MUST comunicar que no entendió y MUST NOT revelar, mostrar ni pronunciar ninguna etiqueta
  candidata de ninguno de los intentos.
- **FR-018**: El sistema MUST permitir iniciar una nueva captura para repetir la seña con una sola
  acción, sin reiniciar la cámara.
- **FR-019**: El sistema MUST permitir descartar un reconocimiento incorrecto, retirándolo de la
  presentación y del historial y deteniendo su reproducción por voz si está en curso.
- **FR-020**: El sistema MUST registrar localmente cada descarte (seña presentada, confianza,
  momento) para permitir el cálculo de la tasa de error percibida, y ese registro MUST borrarse
  junto con los datos de la sesión.

**Robustez de entorno**

- **FR-021**: El sistema MUST funcionar con webcams y cámaras de teléfonos, sin hardware
  especializado (sin sensores de profundidad, sin guantes, sin marcadores). El alcance concreto se
  verifica contra la lista de dispositivos de NFR-020.
- **FR-022**: El sistema MUST funcionar con la persona sentada o de pie, y con los tres tipos de
  fondo declarados en NFR-004: estático liso, estático con textura y dinámico con personas en
  movimiento.
- **FR-023**: El sistema MUST comunicar toda condición que impida el reconocimiento (luz
  insuficiente, manos no detectadas, permiso de cámara denegado, cámara no disponible, cámara
  ocluida, rendimiento insuficiente). Cada mensaje MUST nombrar la causa y una acción concreta que
  quien lo lee pueda ejecutar; verificable por revisión del catálogo de mensajes. El sistema MUST
  NOT fallar en silencio.

**Configuración**

- **FR-024**: El sistema MUST permitir activar y desactivar la reproducción por voz, y elegir entre
  las voces en español disponibles en el dispositivo.
- **FR-025**: El sistema MUST permitir ajustar el umbral de confianza mediante un control con tres
  opciones nombradas (estricto / normal / permisivo), cada una acompañada de una explicación de una
  frase sobre qué implica elegirla. Los valores numéricos son los de FR-016.
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
  producir avisos de red. Verificable por SC-014. Nótese la distinción con el alcance: *arrancar*
  la aplicación sin conexión está fuera de alcance; *sobrevivir* a la caída de la red durante una
  sesión ya cargada, no.

### Non-Functional Requirements

**Performance del reconocimiento**

- **NFR-001**: El desempeño del reconocimiento MUST medirse y reportarse en dos niveles, siempre
  juntos:
  - **NFR-001a — clasificador**: accuracy >= 0.85 sobre **LSA64 versión cut**, split POR SUJETO con
    **sujeto 10 held-out**, sobre secuencias ya recortadas. MUST registrarse la seed, la versión del
    dataset y las versiones exactas de dependencias. Es el baseline heredado de la fase exploratoria
    y el único número comparable con ella.
  - **NFR-001b — sistema desplegado**: accuracy extremo a extremo del procedimiento completo
    —captura iniciada por el interlocutor, tiempo muerto inicial, hasta 3 intentos, regla de parada
    y umbral— sobre personas no vistas en entrenamiento. Es el número que la persona usuaria
    experimenta.
  - Reportar NFR-001a sin NFR-001b está prohibido. Sobre clips ya recortados no existe la decisión
    de parada que el sistema real sí toma, de modo que NFR-001a **no caracteriza el sistema
    desplegado**. Toda diferencia entre ambos MUST atribuirse explícitamente a la regla de parada.
- **NFR-002**: Toda métrica reportada MUST provenir de un split por sujeto. El split aleatorio está
  prohibido para reportes.

**Latencia**

- **NFR-003**: La latencia MUST medirse desde instantes **observables**. "Cuando la persona termina
  de señar" no lo es: determinar ese instante es el problema de segmentación que FR-008 delega en el
  propio sistema, de modo que usar su detector de fin haría que un detector más lento *mejorara* la
  latencia medida. Se definen dos métricas:
  - **L1 — proxy automatizable**: tiempo entre la acción de inicio de captura del interlocutor y la
    presentación de la traducción. No requiere juicio humano; es la métrica de regresión en CI.
  - **L2 — métrica constitucional (Principio IX)**: tiempo entre el último frame de la seña y la
    presentación. El último frame lo marca, offline y a ciegas respecto del resultado del sistema,
    una persona competente en LSA, sobre grabación tomada con **un dispositivo externo a la
    aplicación** —NFR-017(c) prohíbe que la aplicación registre video, y esa prohibición no se
    relaja para medir. L2 MUST ser < 2 s en al menos el 95% de una muestra de >= 50 capturas, en el
    dispositivo de referencia.
  - El presupuesto es íntegramente de cómputo local y los hasta 3 intentos de FR-008 se consumen
    dentro de él.
  - **Dispositivo de referencia — VALOR PROVISIONAL**: hasta que se fije, se toma como referencia un
    teléfono de gama media de los últimos 4 años y una notebook equivalente. El modelo concreto MUST
    declararse al iniciar la fase de plan, junto con su resolución y fps efectivos, y MUST formar
    parte de la lista de NFR-020. **Criterio de revisión**: si el dispositivo declarado no alcanza
    los 2 s, MUST reportarse el percentil real alcanzado y decidirse explícitamente entre optimizar,
    subir el presupuesto con justificación en `research.md` (Principio IX), o declarar un
    dispositivo de referencia distinto — nunca dejar el número sin cumplir y sin decisión.
  - Sin dispositivo de referencia declarado, NFR-003 no es verificable: un mismo sistema cumple o
    incumple según el hardware en que se lo mida.

**Robustez medible**

- **NFR-004**: MUST existir un protocolo de prueba documentado con 3 entornos definidos por
  parámetros observables, cuyos valores efectivos MUST registrarse en cada sesión: iluminancia sobre
  el rostro (lux), distancia persona–cámara (m), resolución y tasa de cuadros efectivas, y tipo de
  fondo (estático liso / estático con textura / dinámico con personas).
  - **Mínimos comunes a los tres entornos**: resolución de captura >= 640 × 480 px y tasa de cuadros
    efectiva >= 15 fps. Por debajo de cualquiera de los dos la sesión es inválida y no computa.
  - **E1 — interior bien iluminado**: 300–750 lux, 1,5–2,5 m, fondo estático liso o con textura.
  - **E2 — interior con luz pobre**: 50–150 lux, 1,5–2,5 m, fondo estático liso o con textura.
  - **E3 — exterior en movimiento**: >= 1000 lux o contraluz, 1,5–2,5 m, fondo dinámico con personas
    en movimiento, cámara sostenida a pulso por una segunda persona en desplazamiento. E3 exige
    **ambas** condiciones, exterior *y* movimiento; satisfacer solo una no cumple el entorno.
  - Toda sesión cuyos valores efectivos caigan fuera del rango declarado MUST descartarse y
    repetirse. Las mediciones se recolectan con el build de evaluación de NFR-017.
  - **Cobertura de modos**: E1 y E2 MUST medirse en **modo autónomo** (dispositivo apoyado), que es
    el predeterminado. E3 MUST medirse en **modo asistido**, porque su definición exige cámara
    sostenida a pulso en desplazamiento. Reportar solo uno de los dos modos deja sin evaluar la
    forma de uso principal.
  - Los rangos de lux y distancia son **provisionales**: se fijan sin medición de campo previa. MUST
    revisarse junto con NFR-005 tras la primera corrida completa del protocolo.
- **NFR-020**: MUST declararse una lista de al menos 3 dispositivos de prueba con su resolución y
  tasa de cuadros efectivas registradas. FR-021 se da por cumplido cuando el criterio de NFR-005 se
  alcanza en todos ellos.
- **NFR-005**: El desempeño MUST reportarse por separado para cada entorno del protocolo, y MUST
  alcanzar al menos **0.70 de accuracy — VALOR PROVISIONAL** en cada uno de los tres para dar la
  robustez por cumplida.
  - **Por qué es provisional**: se fijó por analogía con el baseline de 0.85 en condiciones de
    laboratorio, sin ninguna medición de campo previa. Nadie sabe todavía cuánto cae el
    reconocimiento en E2 o E3.
  - **Criterio de revisión**: tras la **primera corrida completa** del protocolo en los tres
    entornos. Si el resultado de E3 queda por debajo de 0.70 pero por encima de 0.55, el umbral MUST
    reajustarse por entorno en lugar de declarar el proyecto fallido, documentando el nuevo valor y
    la evidencia. Si queda por debajo de 0.55, se trata como fallo de robustez y se revisa el
    enfoque, no el umbral.
  - **Momento**: la primera corrida MUST ejecutarse en cuanto US1 y US3 estén completas, no al final
    del proyecto, precisamente para que quede tiempo de reaccionar al número real.
  - Un umbral provisional declarado con criterio y momento de revisión es verificable; un umbral
    arbitrario presentado como definitivo, no.
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
- **NFR-007**: No se almacena video ni frames en ningún momento ni en ningún medio. Verificable:
  tras una sesión de al menos 10 capturas, la inspección del almacenamiento local del navegador y
  del sistema de archivos no revela ningún artefacto de video, frame ni miniatura.
- **NFR-008**: El historial de sesión y el registro de descartes MUST ser locales al dispositivo y
  borrables por la persona usuaria.
- **NFR-017**: MUST existir un build de evaluación, separado del de producción, que instrumente las
  mediciones de NFR-004 y NFR-005. Ese build MUST: (a) requerir consentimiento explícito e informado
  antes de cada sesión de medición, informando como mínimo qué se registra, qué no se registra,
  dónde quedan los datos, quién accede a ellos y cómo se revoca el consentimiento; (b) declarar de
  forma visible y permanente que está registrando; (c) no registrar ni exportar video bajo ninguna
  circunstancia —incluida la medición de latencia L2, que usa un dispositivo externo (NFR-003);
  (d) exportar únicamente métricas (seña esperada, seña reconocida, confianza, cantidad de intentos,
  entorno, dispositivo, lateralidad declarada). El build de producción MUST NOT contener esta
  instrumentación.

**Accesibilidad y usabilidad**

- **NFR-009**: La interfaz MUST ser utilizable sin instrucciones externas por personas sordas o
  hipoacúsicas usuarias de LSA, con feedback visual para todo estado relevante. Su validación se
  rige por NFR-021; ninguna afirmación de accesibilidad de esta spec puede darse por cumplida sobre
  la base del juicio del equipo de desarrollo.
- **NFR-021 — Protocolo de validación con personas sordas (DEPENDENCIA EXTERNA)**: la accesibilidad
  MUST validarse con usuarias reales de LSA, no por inspección interna. Un requisito de
  accesibilidad evaluado únicamente por personas oyentes que además diseñaron la interfaz no está
  validado.
  - **Participantes**: mínimo **3 personas sordas o hipoacúsicas usuarias de LSA**. Se admite
    sustituir como máximo **1** de las 3 por un intérprete de LSA titulado si el reclutamiento de la
    tercera persona sorda no se concreta, dejándolo asentado en el informe. Se registra el nivel de
    fluidez declarado por cada participante y si es usuaria nativa o tardía de LSA. Nunca se
    sustituyen las 3.
  - **Tareas**, a completar sin ayuda ni explicación previa: (T1) iniciar una sesión y lograr que el
    sistema reconozca una seña; (T2) descartar un reconocimiento incorrecto; (T3) encontrar la lista
    de señas soportadas; (T4) cambiar el nivel de umbral; (T5) identificar, ante un fallo provocado,
    cuál fue el motivo por el que el sistema no entendió.
  - **Criterio de aprobado**: cada tarea MUST ser completada sin ayuda por al menos 2 de cada 3
    participantes. T1 MUST completarse en menos de 2 minutos. Si una tarea no alcanza el criterio,
    MUST rediseñarse y volver a evaluarse antes de cerrar el proyecto.
  - **Conducción**: la observación la conduce una persona que no participó del diseño de la
    interfaz, con guion fijo, sin asistir durante la tarea y sin sugerir. Se registra si la
    comunicación con el participante se hizo por escrito o con intérprete.
  - **Momento**: **dos rondas**. Una **formativa**, sobre prototipo navegable, al alcanzarse US1 y
    US3 (sirve para corregir, no para aprobar). Una **sumativa**, sobre el sistema completo, antes
    del cierre del proyecto. El resultado de la sumativa es el que cuenta para el criterio de
    aprobado.
  - **Dependencia externa**: el reclutamiento requiere coordinación con terceros —asociaciones de
    personas sordas, cátedras de LSA, o el grupo LIDI (UNLP), ya vinculado al proyecto por el
    dataset. MUST iniciarse la gestión con al menos **6 semanas** de anticipación a cada ronda. Es
    la dependencia con mayor riesgo de calendario de todo el proyecto: si no se consigue
    participantes, el requisito **no** se declara cumplido por sustitución interna; se declara **no
    validado** y se documenta como limitación.
  - **Consentimiento**: rige NFR-017(a). No se registra video de los participantes.
- **NFR-010**: Ningún estado, alerta o error del sistema MUST depender únicamente de sonido.
- **NFR-011**: La altura de caracter MUST dimensionarse por ángulo visual, no por píxeles fijos:
  >= 0,4° de ángulo visual a la distancia de lectura prevista, con ratio de contraste >= 4,5:1.
  Para elementos dirigidos a la persona señante la distancia de referencia es 2 m (el extremo
  desfavorable de 1–2 m); para los dirigidos al interlocutor, 40 cm. Validado bajo iluminación del
  entorno E1.
- **NFR-016**: El reconocimiento MUST alcanzar el criterio de NFR-005 en el entorno E3, cuya
  definición incluye cámara sostenida a pulso por una segunda persona en desplazamiento. No se
  requiere que el dispositivo esté apoyado ni estabilizado.

**Legales y de licencia**

- **NFR-012**: El uso de LSA64 MUST respetar su licencia no comercial, con cita a sus autores
  (LIDI, UNLP).
- **NFR-013**: El proyecto completo MUST mantenerse compatible con esa restricción no comercial.

**Calidad**

- **NFR-014**: El contrato de datos de keypoints MUST verificarse en **dos niveles**. La razón es
  concreta: MediaPipe en Python y en el navegador NO producen los mismos landmarks a partir del
  mismo video, porque son implementaciones distintas. Exigir igualdad extremo a extremo sería
  inalcanzable y llevaría a relajar la tolerancia hasta que el test dejara de detectar nada.
  - **Nivel 1 — transformación (BLOQUEANTE de CI)**: dados los mismos landmarks crudos de entrada,
    todo productor MUST producir el mismo vector de 201 coordenadas (63 + 63 + 75), centrado en el
    punto medio de los hombros (landmarks 11 y 12), con z sin centrar y largo fijo muestreado por
    linspace. **Fixture**: conjunto versionado en el repositorio que cubra las 64 clases al menos una
    vez, generado por el preprocesamiento de referencia y congelado. **Tolerancia**: error absoluto
    máximo por coordenada <= 1e-6. **Productores obligados**: preprocesamiento Python de
    entrenamiento y cliente de la aplicación.
  - **Nivel 2 — extremo a extremo (informativo, no bloqueante)**: mismo video procesado por ambos
    caminos. Se verifica dimensión, orden de landmarks y ausencia de desalineación estructural. La
    cota de diferencia por coordenada MUST establecerse **midiéndola empíricamente** en la primera
    corrida, nunca asumiéndola.
- **NFR-015**: Las reglas del pipeline (captura, extracción, clasificación, decisión de confianza,
  post-procesamiento) MUST estar separadas de la interfaz, sin que ningún cliente conozca detalles
  internos del modelo.
- **NFR-019**: Los umbrales de confianza MUST compensar el número de evaluaciones por captura. Antes
  de fijar los valores de estricto / normal / permisivo MUST medirse la tasa de falsos positivos con
  1, 2 y 3 intentos sobre el conjunto de test por sujeto. Los umbrales MUST elegirse de modo que la
  tasa de falsos positivos con 3 intentos no supere en más de 2 puntos porcentuales a la de 1
  intento. La medición y los valores elegidos MUST documentarse. Sin esta compensación, la regla de
  parada de FR-008 busca activamente una ventana que produzca confianza alta, lo que infla la
  confianza aparente y erosiona el Principio VIII. El margen de 2 puntos es provisional y MUST
  revisarse con la primera medición.

### Constitutional Requirements *(mandatory — see `.specify/memory/constitution.md`)*

- **Privacy (Principle VII)**: nada sale del dispositivo en el build de producción. El
  reconocimiento es local, por lo que no se transmiten ni video ni keypoints. El build de evaluación
  (NFR-017) exporta solo métricas, con consentimiento, y nunca video. Cubierto por FR-002, NFR-006,
  NFR-007, NFR-017.
- **Explicit confidence (Principle VIII)**: por debajo del umbral el sistema dice "no entendí" y no
  revela la etiqueta candidata (FR-017). La confianza se muestra siempre (FR-013). La UI declara de
  forma persistente que Helpi asiste y no reemplaza a un intérprete (FR-029).
- **Latency (Principle IX)**: presupuesto de 2 s medido como L2 —desde el último frame de la seña,
  anotado a ciegas sobre grabación externa, hasta la presentación (NFR-003)—, íntegramente de
  cómputo en el dispositivo e incluidos los hasta 3 intentos. L1 sirve de proxy automatizable en CI.
  Medir desde el detector de fin del propio sistema queda prohibido por circular.
- **Temporal segmentation debt (Principle XII)**: esta feature resuelve parcialmente la deuda —
  detecta el fin de la seña con el inicio conocido y un tope de 3 intentos (FR-008)— en lugar de
  agravarla. La segmentación continua sin delimitación humana sigue pendiente y fuera de alcance.
- **Evaluation (Principle V)**: métrica reportable = accuracy con split por sujeto sobre LSA64 cut,
  sujeto 10 held-out, contra el baseline 0.85 (NFR-001a), reportada siempre junto a la métrica
  extremo a extremo del sistema desplegado (NFR-001b). La evaluación de campo por entorno (NFR-004,
  NFR-005) es una tercera medición distinta y se reporta por separado.
- **Data contract (Principle IV)**: los productores obligados son el cliente de la aplicación y el
  preprocesamiento de entrenamiento, ambos cubiertos por el test de Nivel 1 de NFR-014, bloqueante
  de CI con tolerancia 1e-6. El Nivel 2 es informativo porque la igualdad extremo a extremo entre
  implementaciones distintas de MediaPipe no es alcanzable.

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

- **SC-001**: El clasificador alcanza al menos 0.85 de accuracy sobre las 64 señas de LSA64 versión
  cut, con sujeto 10 held-out y seed y dependencias registradas (NFR-001a); y se reporta junto a él
  la accuracy extremo a extremo del sistema desplegado sobre personas no vistas (NFR-001b), con la
  diferencia entre ambos atribuida explícitamente a la regla de parada.
- **SC-002**: En al menos el 95% de una muestra de >= 50 capturas, la latencia L2 —desde el último
  frame de la seña, anotado a ciegas por una persona competente en LSA sobre grabación externa,
  hasta la presentación— es menor a 2 segundos en el dispositivo de referencia.
- **SC-003**: El desempeño se mide y reporta por separado en los 3 entornos definidos en NFR-004
  (E1, E2, E3), con sus parámetros efectivos de lux, distancia, resolución, fps y fondo registrados
  por sesión, sobre el subconjunto congelado de 10 señas de NFR-018, con al menos 10 intentos por
  seña en cada entorno, y alcanza al menos 0.70 de accuracy en cada entorno.
- **SC-004**: En el build de producción, cero peticiones de red salen del dispositivo durante una
  sesión completa, verificable mediante inspección del tráfico saliente.
- **SC-013**: El build de producción no contiene ninguna ruta de código de instrumentación o
  exportación de la evaluación, verificable por inspección del artefacto distribuido.
- **SC-005**: En una auditoría de 100 capturas sin reconocimiento sobre umbral, en cero casos se
  muestra o pronuncia una etiqueta candidata. Las 100 capturas se generan de forma reproducible
  ejecutando el subconjunto congelado de NFR-018 con el umbral en modo estricto, más gestos
  deliberadamente fuera de vocabulario, en la proporción declarada en el protocolo.
- **SC-006**: En la ronda sumativa del protocolo de NFR-021, cada una de las cinco tareas (T1–T5) es
  completada sin ayuda por al menos 2 de cada 3 participantes sordos o hipoacúsicos usuarios de LSA,
  y T1 se completa en menos de 2 minutos.
- **SC-007**: El 100% de los interlocutores evaluados lee correctamente la traducción sosteniendo el
  dispositivo (~40 cm), y el 100% de las personas señantes evaluadas lee correctamente su propio
  feedback de encuadre y estado a 2 metros, bajo la iluminación del entorno E1.
- **SC-015**: Al menos el 80% de 5 personas que no conocen LSA ni la aplicación logran encuadrar y
  completar una captura en menos de 1 minuto, recibiendo solo la consigna de grabar a la otra
  persona.
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
- **SC-014**: Con la conectividad del dispositivo deshabilitada después de cargar la aplicación, una
  sesión de 10 capturas se completa sin errores de red visibles y sin degradación medible del
  reconocimiento respecto de la misma sesión con conexión.
- **SC-016**: La tasa de falsos positivos con 3 intentos de reconocimiento no supera en más de 2
  puntos porcentuales a la medida con 1 intento, sobre el conjunto de test por sujeto (NFR-019).
- **SC-017**: El test de contrato de keypoints de Nivel 1 pasa para los dos productores obligados
  con error absoluto máximo por coordenada <= 1e-6, y falla el build cuando se lo altera
  deliberadamente.

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

## Limitaciones conocidas del MVP

Esta sección existe para que ninguna de estas limitaciones quede implícita. Se declaran aquí, no en
una nota al pie, porque afectan a lo que el proyecto puede afirmar que resuelve.

### El MVP NO cubre el caso de uso que motiva el proyecto

El escenario norte del proyecto es **una persona sorda que necesita comunicarse en el subte**. Ese
escenario **queda fuera del MVP**, y la spec no debe leerse como si lo cubriera.

La razón es directa: el funcionamiento sin conexión salió de alcance, y en el subte no hay
conectividad. El reconocimiento ocurre en el dispositivo, pero **cargar la aplicación requiere red**,
de modo que una persona que baja al subte con la aplicación cerrada no puede usarla. Si la dejó
abierta antes de bajar, funciona; esa es toda la cobertura disponible y depende de una precaución
que no se le puede exigir a nadie en una situación real.

Lo mismo aplica, en menor grado, a zonas sin señal, con datos agotados o con red saturada — es
decir, a buena parte del escenario "calle y transporte" que justifica el producto.

**El funcionamiento sin conexión es requisito de una iteración posterior, no una mejora opcional.**
Es la primera candidata a recuperar si el MVP valida. Hasta entonces, toda comunicación pública del
proyecto MUST describir el alcance como "requiere conexión para abrirse", sin sugerir cobertura del
escenario de transporte.

### Otras limitaciones declaradas

- **Vocabulario de 64 señas**: no es una lengua, es un subconjunto cerrado. Una conversación real
  excede este vocabulario casi de inmediato (FR-029 lo declara en la interfaz).
- **Señas aisladas, no frases**: una seña por captura. La traducción continua queda para LSA-T.
- **Lateralidad no verificada**: LSA64 no declara la lateralidad de sus sujetos, de modo que el
  proyecto **no puede afirmar** que el reconocimiento sea independiente de la mano dominante. Se
  reporta desagregado y, si hay diferencia, se declara en la interfaz.
- **Dirección única**: el sistema traduce de LSA a español. La respuesta del interlocutor oyente
  hacia la persona sorda no está cubierta por ningún requisito; ocurre por los medios que las
  personas ya usaran antes (escribir, gestos, lectura labial).

## Assumptions

- **Un solo dispositivo, dos disposiciones** (DD-001): apoyado e iniciado por la persona señante
  (modo autónomo, predeterminado) o sostenido e iniciado por el interlocutor (modo asistido). En
  ambos la cámara frontal apunta a la persona señante y ella se ve a sí misma. No se contempla
  emparejar dos dispositivos.
- **La cámara puede estar apoyada o sostenida a pulso**: el modo asistido introduce movimiento de
  cámara, que es condición normal de uso y no un caso degradado; el modo autónomo no lo tiene, pero
  a cambio nadie corrige el encuadre.
- **La voz importa más en modo autónomo**: con el dispositivo apoyado mirando a la persona señante,
  la voz es el único canal que alcanza al interlocutor sin manipular el aparato. US1 sin US2 entrega
  bastante menos valor en el modo predeterminado que en el asistido.
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
  directamente el Principio VIII. El tope de 3 intentos acota el problema pero no lo elimina; por eso
  la compensación dejó de ser un supuesto y es hoy un requisito verificable (**NFR-019**, **SC-016**).
- **Tiempo muerto inicial**: entre el inicio de la captura y el comienzo real de la seña hay un
  tramo sin movimiento que no existe en los datos de entrenamiento (LSA64 versión cut contiene señas
  ya recortadas). Cómo se excluye ese tramo es decisión de la fase de plan, pero incluirlo sin más
  desalinearía el contrato del Principio IV.
- **Dispositivo de referencia**: ver NFR-003, donde quedó declarado como valor provisional con
  criterio y momento de revisión.
- **Tensión reconocimiento local vs. baseline**: ejecutar el modelo en el dispositivo puede exigir
  una versión más liviana que la validada en la fase exploratoria. NFR-001a mide esa configuración
  sobre el dataset; toda caída por debajo de 0.85 requiere justificación escrita según el Principio
  V. Resolver esta tensión es trabajo de la fase de plan.
- **Decisiones diferidas a la fase de plan, con dueño**: (a) valores numéricos de estricto / normal /
  permisivo, condicionados a la medición de NFR-019; (b) modelos concretos del dispositivo de
  referencia; (c) cota empírica del Nivel 2 del contrato de keypoints; (d) rango de duración de seña
  con el que el reconocimiento se mantiene sobre el umbral. Ninguna es una omisión: las cuatro
  dependen de mediciones que aún no existen y todas tienen requisito que las obliga.
- **Vocabulario cerrado**: las 64 etiquetas de LSA64 se presentan con su traducción al español; no
  hay ampliación de vocabulario por parte de la persona usuaria.
- **Historial efímero**: el historial de sesión no sobrevive al cierre de la aplicación; solo las
  preferencias persisten.
- **Valores provisionales declarados**: cuatro números de esta spec se fijaron sin evidencia y
  tienen criterio y momento de revisión escritos en su propio requisito — umbrales de confianza
  (FR-016), 0.70 de robustez (NFR-005), rangos de lux y distancia de los entornos (NFR-004),
  dispositivo de referencia (NFR-003), y margen de 2 puntos de NFR-019. Ninguno se presenta como
  definitivo.
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
