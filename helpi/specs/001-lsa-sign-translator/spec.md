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
- Q: ¿El interlocutor aprieta grabar una vez por seña, o una vez para toda la conversación? → A:
  **Una vez para toda la conversación.** Queda grabando y el modelo detecta el inicio y el fin de
  cada seña dentro del stream continuo. Esto incorpora la segmentación temporal completa —la deuda
  del Principio XII— al alcance del MVP (ver DD-002).
- Q: ¿La distancia persona–cámara es un parámetro que se le impone a la persona? → A: No. La
  distancia correcta es aquella en la que el modelo detecta bien los keypoints. Si no los detecta de
  forma confiable, el sistema MUST avisarle a la persona señante. El rango numérico deja de ser
  criterio de admisión y pasa a ser un dato que se registra.
- Q: ¿Qué pasa si la segmentación continua no alcanza calidad usable, ahora que no queda un modo
  manual al que degradar? → A: Se conserva un **modo de respaldo manual** ("capturar una seña"),
  especificado desde el inicio y construido sobre el mismo pipeline, activable si la segmentación no
  alcanza el criterio. Decidirlo ahora cuesta poco; agregarlo en el último mes, mucho.
- Q: ¿Cuándo habla la voz en modo continuo, si cada seña reconocida la dispara? → A: **Agrupada por
  pausa**. El texto de cada seña se muestra al instante; la voz espera a que la persona haga una
  pausa y entrega el bloque. Además, un **LLM** convierte la secuencia de glosas en una frase
  natural antes de pronunciarla (ver DD-003), lo que incorpora al alcance el pulido glosa→frase que
  estaba diferido.
- Q: ¿Quién controla lo que el LLM agrega, si puede decir en nombre de la persona palabras que ella
  no señó? → A: **LLM restringido a palabras funcionales**. Puede agregar conectores, preposiciones,
  artículos y conjugación, pero **ninguna palabra de contenido** que no esté en las glosas. Sin
  confirmación previa, con la glosa cruda siempre visible y descarte posterior (ver DD-004).
- Q: ¿Dónde corre el LLM, si un modelo de 7–8B no entra en el navegador de un teléfono? → A: En un
  **servidor remoto**, alcanzado por datos móviles, **solo para el pulido glosa→frase**. El
  reconocimiento sigue siendo local: no viajan ni video ni keypoints. Lo que viaja son las glosas ya
  reconocidas, lo que obliga a redefinir la frontera de privacidad (ver DD-005).
- Q: ¿Qué umbral tiene NFR-001b, el accuracy del sistema completo que la persona usuaria
  experimenta? → A: **>= 0.70**, el mismo número de NFR-005 y de la puerta de repliegue de NFR-022.
  Un solo umbral con un solo significado, en vez de un cuarto número: incumplir NFR-001b y disparar
  el repliegue al modo manual pasan a ser el mismo evento.
- Q: ¿Contra qué red se mide el presupuesto de 3 s de NFR-023? → A: **4G urbano**, con RTT de
  referencia entre 50 y 150 ms declarado en el protocolo. Es la condición real del escenario de
  calle; medir sobre Wi-Fi haría que el requisito se cumpla en laboratorio y falle en uso. Excedido
  el presupuesto, se pronuncia la glosa cruda.
- Q: ¿Qué requisitos operativos tiene el servicio de pulido, hoy un endpoint público que ejecuta un
  LLM? → A: **Disponibilidad modesta más límite de uso**. Objetivo declarado solo para las ventanas
  de evaluación, porque la degradación a glosa cruda ya cubre las caídas; y límite de peticiones por
  origen, tamaño máximo y validación contra el vocabulario cerrado, porque el abuso no se degrada
  solo. Sin cuentas ni autenticación de usuario.
- Q: ¿Cómo se opera el servicio si NFR-024 prohíbe registrar las glosas? → A: **Métricas agregadas
  sin contenido**: cantidad de peticiones, latencia, tasa de error, tasa de rechazos. Nunca la
  glosa, nunca la respuesta, nunca IP asociada a contenido. Permite diagnosticar una caída durante
  una sesión de evaluación sin guardar qué dijo nadie.
- Q: ¿Cuál es el máximo de señas por bloque de voz (FR-038), del que además cuelga el tamaño máximo
  de petición de NFR-026? → A: **5 señas**. Bloques cortos, frases simples, latencia predecible y
  petición acotada; a cambio corta con más frecuencia los enunciados largos.

### Session 2026-07-29

- Q: ¿Qué separa la categoría "alta" de "media" en FR-013, dado que la spec solo define la frontera
  de "no entendí"? → A: **Las fronteras son las de FR-016**: "alta" = confianza >= umbral del nivel
  *estricto* vigente; "media" = entre el umbral activo y el de estricto. No se introduce ningún
  número nuevo: ambas fronteras se recalibran junto con los umbrales en la medición de NFR-019. Con
  el nivel estricto activo, todo reconocimiento aceptado es "alta", lo cual es coherente.
- Q: ¿Quién de los dos actores humanos configura las preferencias (CHK008)? → A: **La persona
  señante es la dueña de las preferencias.** La pantalla de configuración se diseña para ella:
  operable sin audio (NFR-010) y validada con personas sordas (NFR-021, tarea T4). El interlocutor
  puede accionarla físicamente cuando sostiene el dispositivo, pero el destinatario del diseño es la
  persona señante. En los requisitos de configuración, "la persona usuaria" significa la persona
  señante.
- Q: ¿Qué significa exactamente "muestreado por linspace" en NFR-014, dado que `np.linspace` en
  punto flotante no es reproducible bit a bit entre Python y JavaScript? → A: **La forma entera
  exacta** `idx[i] = (i·(T−1)) div (N−1)` (división entera), idéntica en todos los productores. Es
  la versión exacta del mismo criterio (`linspace` de índices + truncado), sin dependencia de la
  representación en punto flotante. La definición normativa completa vive en el contrato versionado
  de keypoints. La enmienda espejo (PATCH) a la constitution se tramita por el procedimiento de
  Governance, aparte de esta clarificación.
- Q: ¿Cuál es el dispositivo de referencia de NFR-003? → A: **Samsung Galaxy A10, con prioridad
  mobile-first**: el teléfono es la referencia vinculante; la notebook es secundaria (una notebook
  genérica del equipo, modelo a asentar al medir). El A10 (2019) queda por debajo de la banda
  provisional "gama media de los últimos 4 años" — se declara a sabiendas como objetivo **más
  exigente**: si el sistema cumple los 2 s en un A10, cumple en cualquier gama media actual. La
  resolución y los fps efectivos se miden y registran al declararlo en la lista de NFR-020. Rige el
  criterio de revisión de NFR-003 si el presupuesto no se alcanza.
- Q: ¿Hace falta un modo restringido (kiosco) cuando la persona señante entrega su teléfono
  desbloqueado a un interlocutor desconocido (CHK009)? → A: **Fuera de alcance, declarado como
  limitación conocida.** Una aplicación web no puede impedir que quien sostiene el teléfono salga de
  ella; prometer contención sería incumplible. Mitigación con lo que la app sí controla: el
  historial es efímero y la sesión no expone datos personales (NFR-008), y detener la grabación está
  siempre a un toque (FR-001). Un modo kiosco real queda como candidato para la eventual iteración
  nativa.

## Decisiones de diseño registradas

### DD-001 — Quién inicia la grabación *(SUPERSEDIDA por DD-002)*

Esta decisión introdujo dos modos de captura, uno de ellos con cuenta regresiva, para que la persona
señante pudiera iniciar cada seña sin depender de la cooperación del interlocutor. **DD-002 la deja
sin efecto**: al grabar de forma continua, iniciar deja de ser una acción por seña y pasa a ser una
sola acción al comenzar la conversación, de modo que la asimetría que DD-001 intentaba corregir
prácticamente desaparece. Se conserva el registro porque la preocupación de fondo sigue siendo
válida y queda recogida en DD-002.

### DD-002 — Grabación continua con segmentación automática de señas

**Revisa**: la decisión Q2 de la sesión de clarify del 2026-07-25 (captura delimitada por seña) y
deja sin efecto DD-001.

**Decisión**: la grabación se inicia **una vez por conversación**, normalmente por el interlocutor.
Mientras está activa, **el modelo detecta por sí mismo el inicio y el fin de cada seña** dentro del
stream continuo. No hay una acción humana por seña.

**Qué detecta el modelo y qué no**: detecta que *hay* actividad de señado y dónde empieza y termina;
no sabe *cuál* seña es hasta clasificarla. Son dos capacidades distintas y la spec las trata por
separado (FR-007 y FR-008 vs. FR-003).

**Consecuencia sobre el alcance**: esto incorpora al MVP la **segmentación temporal completa**, que
la constitution declara deuda no resuelta (Principio XII) y que las sesiones anteriores habían
dejado explícitamente fuera de alcance. Deja de ser deuda diferida y pasa a ser **trabajo central
del proyecto**.

**Justificación**: es la única forma de que la conversación fluya. Con captura por seña, cada palabra
costaba una acción de alguien; en una conversación real eso es inviable. Además elimina la asimetría
que DD-001 intentaba corregir: una sola acción al principio pesa mucho menos que una por seña.

**Riesgo asumido — es el mayor del proyecto**:

- La fase exploratoria ya encontró que la ventana deslizante produce **confianza inestable**. Ese
  problema pasa a estar en el camino crítico: si la segmentación no funciona, no funciona nada.
  Con captura por seña, un fallo de segmentación degradaba a "apretá de nuevo"; ahora no hay a qué
  degradar.
- El modelo se entrenó con **LSA64 versión cut**: señas ya recortadas, sin transiciones. Un stream
  continuo contiene los movimientos de transición entre señas y entre reposo y seña, que **no
  existen en los datos de entrenamiento**. Es una desalineación de distribución respecto del
  contrato del Principio IV, y es exactamente el tipo de fallo silencioso contra el que ese
  principio advierte.
- Aparece un problema nuevo que antes no existía: **distinguir señado de movimiento que no es seña**
  (acomodarse el pelo, gesticular, saludar, moverse). Un falso positivo aquí produce una traducción
  inventada a partir de un gesto cualquiera, que es el peor resultado posible según el Principio
  VIII.

**Mitigación exigida**: FR-034 (no clasificar movimiento no identificado como seña), FR-037 (modo de
respaldo manual), NFR-019 (compensación del umbral), y NFR-022 (medición explícita del costo de la
segmentación, con puerta de decisión).

**Respaldo**: como la segmentación deja de tener un camino manual al que degradar, FR-037 conserva
uno explícito. La puerta que decide si se activa está en NFR-022: si con segmentación continua la
accuracy no alcanza 0.70 en E1 —el entorno más favorable— el modo manual pasa a ser el
predeterminado y la segmentación continua queda como funcionalidad opcional. Esa decisión se toma en
la primera medición, no al final.

### DD-003 — Voz agrupada por pausa y pulido glosa→frase con LLM

**Decisión**: el texto de cada seña se muestra apenas se reconoce. La **voz** no se dispara por seña:
el sistema acumula las glosas reconocidas y, al detectar una pausa de la persona señante, pasa el
bloque por un **LLM** que lo convierte en una frase natural en español, y esa frase es la que
se pronuncia. "agua · beber · gracias" se escucha como una oración, no como tres palabras sueltas.

**Encaje constitucional**: el Principio II permite exactamente este uso y solo este. El LLM
**nunca** recibe video, frames ni keypoints, **nunca** reconoce ni clasifica señas, y opera
únicamente sobre la secuencia de glosas ya producida por el clasificador. Es la última etapa del
pipeline, no una vía paralela.

**Dónde corre**: en un **servidor remoto propio del proyecto**, alcanzado por red, porque un modelo
de 7–8B no entra en el navegador de un teléfono de gama media. El reconocimiento **no** se mueve: la
extracción de keypoints y la clasificación siguen siendo locales. Las implicancias de privacidad de
esta decisión se tratan en DD-005, que redefine la frontera.

**Riesgo principal — el sistema empieza a poner palabras que nadie señó**: convertir "agua beber
gracias" en "necesito agua para beber, gracias" agrega *necesito* y *para*. Si el agregado
distorsiona la intención, el sistema habla en nombre de una persona sorda diciendo algo que ella no
dijo, con la fluidez de una oración bien formada que no deja ver dónde termina lo señado y dónde
empieza lo inventado. Bajo el Principio VIII eso es más dañino que no traducir: **una frase fluida
lava la incertidumbre**. FR-038 y FR-039 acotan el problema; la agencia de la persona sobre lo que
se dice en su nombre se trata en DD-004.

**Riesgo secundario — latencia y tamaño**: un modelo de 7–8B no corre en un navegador de teléfono de
gama media dentro de ningún presupuesto razonable. La decisión es compatible con NFR-003 solo porque
el pulido ocurre **en la pausa**, no por seña: el texto sigue apareciendo en menos de 2 s y la frase
hablada tiene su propio presupuesto (NFR-023). Aun así, dónde corre ese modelo es un problema
abierto para la fase de plan y tensiona el supuesto de MVP web en dispositivo de gama media.

### DD-004 — El LLM solo puede agregar palabras funcionales

**Decisión**: el pulido glosa→frase MUST restringirse a **palabras de clase cerrada** —preposiciones,
artículos, conjunciones, pronombres, auxiliares— y a la flexión de las glosas reconocidas. **Ninguna
palabra de contenido** (sustantivo, verbo principal, adjetivo, adverbio) puede aparecer en la frase
si no corresponde a una glosa reconocida por encima del umbral. No hay confirmación previa: la frase
se pronuncia directamente, con la glosa cruda visible al lado y la posibilidad de descartar después
(US4).

**Justificación**: el riesgo real no es que la frase suene mal, es que **diga algo distinto de lo que
la persona señó**. Restringir el vocabulario del modelo ataca esa causa directamente, en lugar de
agregar un paso de aprobación que la disposición física vuelve incómodo —con el dispositivo apoyado
o en manos del interlocutor, cancelar exige alcanzarlo, justo cuando la conversación debería fluir.

**Alternativas descartadas**: confirmación explícita por bloque (rompe el ritmo en cada pausa);
ventana de veto de ~2 s (mismo problema de alcance físico, con menos margen); sin restricción
(convierte al sistema en un generador de frases plausibles atribuidas a alguien que no las dijo).

**Costo asumido**: la frase será a veces más torpe que lo que un modelo libre produciría. Es un
intercambio deliberado: se prefiere una frase imperfecta pero atribuible a una fluida pero
potencialmente inventada, en línea con el Principio VIII.

**Verificable**: FR-040 y SC-021. Toda palabra de contenido de la salida debe trazar a una glosa de
la entrada, comparando por lema.

### DD-005 — La frontera de privacidad se redefine: salen glosas, no keypoints ni video

**Revisa**: NFR-006, que hasta ahora prohibía que saliera cualquier dato de la sesión.

**Contexto**: el "nada sale del dispositivo" de las sesiones anteriores era una restricción **más
estricta que la constitution**, no un mandato de ella. El Principio VII exige que el video crudo
nunca salga y permite explícitamente que **los keypoints viajen al servidor**. Al mover el LLM a un
servidor remoto (DD-003), esa restricción autoimpuesta deja de sostenerse y hay que declarar la
frontera real en lugar de dejarla implícita.

**Decisión — qué cruza y qué no**:

| Dato | ¿Sale del dispositivo? |
|------|------------------------|
| Video, frames, miniaturas | **Nunca.** Principio VII, sin excepción. |
| Keypoints | **No.** El clasificador es local; aunque la constitution lo permitiría, no hay motivo para transmitirlos. |
| Secuencia de glosas reconocidas | **Sí**, únicamente hacia el servicio de pulido, y solo cuando el pulido está activado. |
| Identificadores de persona o sesión | **Nunca.** Las peticiones no se correlacionan entre sí. |
| Historial, descartes, preferencias | **Nunca.** Locales y borrables (NFR-009). |

**Lo que esto significa en concreto**: sale **el contenido de lo que la persona sorda está
diciendo**, expresado como palabras de un vocabulario cerrado y públicamente conocido de 64 glosas.
Es menos revelador que texto libre —el espacio de mensajes está acotado— pero es el mensaje, no una
representación anónima. Llamarlo de otro modo sería engañarse.

**Salvaguardas exigidas**: NFR-024 (el servicio no retiene ni registra), NFR-025 (la persona debe
poder desactivar el pulido y seguir usando el sistema), y FR-041 (declararlo en la interfaz antes de
que ocurra, no en una política que nadie lee).

**Costo asumido**: el pulido depende de la conexión. Sin red, el sistema sigue reconociendo y
hablando, pero pronuncia la glosa cruda. Es una degradación aceptable porque lo esencial
—reconocer— permanece local.

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

**Disposición física**: la cámara frontal apunta a la persona señante, que se ve a sí misma en la
pantalla y recibe ahí el feedback de detección y de estado. Sus manos quedan libres. El dispositivo
puede estar **sostenido por el interlocutor** —lo habitual, y da mejor encuadre porque alguien
corrige el ángulo— o **apoyado** en una superficie o soporte, lo que permite a la persona señante
iniciar la grabación ella misma sin depender de que la otra parte colabore. Ambas disposiciones son
válidas y no requieren funcionalidad distinta: el control de grabación es el mismo.

La distancia correcta a la cámara **no se prescribe**: es aquella en la que el modelo detecta los
keypoints de forma confiable. Cuando no lo hace, el sistema avisa a la persona señante (FR-035).

**Modelo de interacción**: la grabación se inicia **una vez por conversación**, con una acción
humana explícita, normalmente del interlocutor. Mientras está activa, el sistema detecta por sí
mismo el inicio y el fin de cada seña dentro del stream, la clasifica, y presenta la traducción por
texto y voz. No hay una acción humana por seña (DD-002).

### User Story 1 - Traducir una seña a texto con confianza explícita (Priority: P1)

El interlocutor apunta la cámara a la persona señante y aprieta "grabar" una sola vez. A partir de
ahí ella seña con normalidad: cada vez que realiza una seña del vocabulario LSA64, el sistema la
detecta, la reconoce, y muestra la palabra en pantalla junto con qué tan seguro está, mientras la
pronuncia por voz. Cuando no está lo bastante seguro dice que no entendió, en lugar de mostrar una
adivinanza. La conversación fluye sin que nadie toque el dispositivo entre seña y seña.

**Why this priority**: es el núcleo del producto. Sin esto no hay traductor. La confianza explícita
va incluida en esta historia y no en una posterior porque presentar una traducción errada como
certeza puede dañar la comunicación más que no traducir (constitution, Principio VIII); un MVP que
adivine no es un MVP entregable.

**Independent Test**: con una sola acción de inicio, una persona realiza 20 señas conocidas
separadas por pausas naturales; se verifica que el sistema produce exactamente 20 eventos de
reconocimiento —ni fusiona dos señas ni duplica una—, que cada reconocimiento sobre umbral se
muestra con su categoría de confianza, y que los que no llegan al umbral producen "no entendí" sin
revelar la etiqueta candidata.

**Acceptance Scenarios**:

1. **Given** la grabación activa y la persona detectada, **When** realiza una seña del vocabulario
   que el sistema reconoce por encima del umbral, **Then** el sistema muestra la palabra en texto
   grande junto con su categoría de confianza, en menos de 2 segundos desde que ella terminó de
   señar, sin que nadie haya tocado el dispositivo.
2. **Given** la grabación activa, **When** el sistema detecta una seña pero ningún intento de
   reconocimiento supera el umbral, **Then** comunica que no entendió y no muestra ni pronuncia
   ninguna etiqueta candidata.
3. **Given** la grabación activa, **When** la persona realiza un gesto que no pertenece al
   vocabulario LSA64, **Then** el sistema responde "no entendí" en lugar de forzar la seña más
   parecida.
4. **Given** la grabación activa, **When** la persona realiza dos señas consecutivas separadas por
   una pausa natural, **Then** el sistema produce dos reconocimientos distintos, sin fusionarlas ni
   duplicar ninguna.
5. **Given** la grabación activa, **When** la persona se acomoda el pelo, saluda o hace cualquier
   movimiento que no es una seña del vocabulario, **Then** el sistema no produce ninguna traducción.
6. **Given** una traducción mostrada, **When** el sistema reconoce la seña siguiente, **Then** el
   resultado anterior se desplaza al historial sin ambigüedad sobre cuál es el reconocimiento
   actual.
7. **Given** la grabación activa, **When** la persona señante mira la pantalla, **Then** ve de forma
   inequívoca si el sistema la está detectando, si está procesando una seña, o si está en reposo
   esperando.

---

### User Story 2 - Escuchar la traducción (interlocutor oyente) (Priority: P2)

El interlocutor oyente no mira la pantalla todo el tiempo (está manejando, cargando bolsas, o
simplemente conversando). El sistema espera a que la persona señante haga una pausa, arma con las
señas reconocidas una frase en español y la pronuncia. En lugar de "agua… beber… gracias" escucha
una oración.

**Why this priority**: dada la disposición física adoptada, la voz es lo que evita que el
interlocutor tenga que girar el dispositivo después de cada seña. Sin voz el sistema funciona, pero
cada palabra cuesta un giro completo del teléfono, lo que vuelve la conversación notablemente más
lenta. Queda en P2 y no en P1 porque el texto de US1 ya entrega valor por sí solo, pero es la
historia que más mejora el ritmo conversacional.

**Independent Test**: con la voz activada se realizan 20 reproducciones y ≥5 oyentes
hispanohablantes las transcriben; el criterio es ≥95% de palabras transcritas correctamente. Se
verifica además que ningún reconocimiento bajo umbral se pronuncia.

**Acceptance Scenarios**:

1. **Given** la voz activada, **When** la persona señante realiza varias señas y hace una pausa,
   **Then** el sistema pronuncia el bloque como una frase en español, en voz rioplatense si el
   dispositivo la ofrece y en español neutro en caso contrario, mientras el texto de cada seña ya se
   mostró al reconocerse.
2. **Given** la voz activada, **When** un reconocimiento queda bajo el umbral, **Then** esa seña no
   se incorpora a la frase ni se pronuncia como candidata.
3. **Given** la frase pulida mostrada, **When** el interlocutor o la persona señante la miran,
   **Then** ven junto a ella la glosa cruda, de modo que puedan distinguir qué señó la persona y qué
   agregó el sistema.
4. **Given** el pulido desactivado o sin conexión, **When** la persona hace una pausa, **Then** el
   sistema pronuncia la glosa cruda y avisa que la frase no pudo componerse, en lugar de callarse.
5. **Given** el dispositivo sin ninguna voz en español disponible, **When** se activa la voz,
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

1. **Given** la grabación activa, **When** las manos y el torso están en cuadro y detectados,
   **Then** el sistema muestra un indicador visual persistente de "te estoy viendo".
2. **Given** la grabación activa, **When** las manos salen del cuadro, **Then** el sistema avisa
   visualmente cuál es el problema y cómo corregirlo, sin depender de sonido.
3. **Given** la grabación activa, **When** la luz es insuficiente para detectar keypoints de forma
   estable, **Then** el sistema lo comunica explícitamente en lugar de quedarse en silencio.
4. **Given** la persona demasiado cerca o demasiado lejos, **When** el torso o las manos no entran
   en el encuadre útil, **Then** el sistema indica en qué dirección corregir la distancia.
5. **Given** una captura en curso, **When** las manos salen del cuadro antes de que el sistema
   reconozca la seña, **Then** el sistema descarta el evento con aviso y no lo clasifica.

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

### User Story 8 - Sostener el dispositivo para otra persona (Priority: P3)

El interlocutor oyente, que no conoce LSA y nunca usó la aplicación, sostiene el dispositivo y
apunta la cámara a la persona señante: tiene que encuadrarla bien y saber que el sistema la está
detectando, mirando una pantalla que apunta hacia ella y no hacia él.

**Why this priority**: bajó de P2 a P3 tras DD-002. Al grabar de forma continua, su participación se
redujo a apretar una vez y sostener el aparato; ya no acciona un control por seña. Sigue importando
porque el encuadre depende de él, pero el sistema es utilizable con el dispositivo apoyado.

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

**Segmentación: detección de inicio y fin de seña**

- **Movimiento que no es seña** (acomodarse el pelo, saludar, gesticular, ajustarse la ropa): no
  produce reconocimiento (FR-034). Es el falso positivo más dañino del sistema, porque inventa una
  traducción a partir de un gesto cualquiera.
- **Dos señas encadenadas sin pausa**: el sistema debe producir dos reconocimientos o ninguno, nunca
  uno solo que fusione ambas. Si no puede separarlas, calla.
- **Seña sostenida o repetida**: no debe producir reconocimientos duplicados en cadena (FR-036).
- **Transición entre señas**: el movimiento de llevar las manos de una configuración a la siguiente
  no es seña y no debe clasificarse. No existe en LSA64 versión cut, que contiene señas ya
  recortadas: es la desalineación de distribución central de DD-002.
- **La persona no seña durante un rato largo**: el sistema permanece en reposo, sin producir
  reconocimientos ni consumir intentos, y lo muestra en su estado visible.
- **Manos fuera de cuadro a mitad de la seña**: el evento se descarta con aviso; no se clasifica una
  seña truncada.
- **Grabación iniciada dos veces**: la segunda acción detiene o se ignora, pero nunca deja el sistema
  en un estado ambiguo sobre si está grabando.
- **Seña realizada con la grabación detenida**: no se produce reconocimiento; el estado visible de
  "grabación detenida" debe hacer evidente por qué.
- **El interlocutor se niega a sostener el dispositivo, o no entiende qué se le pide**: la persona
  señante puede apoyar el dispositivo e iniciar la grabación ella misma. Al ser una sola acción por
  conversación, no queda dependiendo de la cooperación ajena para cada palabra.

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
  seña es válida pero la observación es parcial. El sistema descarta el evento con aviso de encuadre
  en lugar de clasificar con información faltante.
- **Persona que seña con una sola mano**: la ausencia de una mano es un estado válido de entrada, no
  un error, cuando la seña efectivamente es de una mano.
- **Más de una persona en cuadro**: el sistema sigue a la persona de mayor área de torso detectada al
  iniciarse la grabación y la mantiene durante toda la sesión, sin alternar. Si dos personas tienen
  áreas equivalentes, no inicia la grabación y lo comunica.

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
- **El interlocutor gira el dispositivo mientras la persona seña**: el sistema pierde la detección y
  descarta el evento en curso con aviso, en lugar de clasificar una seña perdida de vista. Al
  volver a encuadrar, la grabación sigue activa sin necesidad de reiniciarla.
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
  local. El pulido deja de estar disponible: la voz pasa a pronunciar la glosa cruda y el sistema lo
  avisa, en lugar de callarse o esperar.
- **El servicio de pulido responde tarde o no responde**: se pronuncia la glosa cruda dentro del
  presupuesto de NFR-023. Nunca se retiene la voz esperando al servidor.
- **El servicio rechaza la petición por límite de uso**: se pronuncia la glosa cruda. El cliente no
  reintenta en bucle ni informa el detalle técnico a la persona usuaria.
- **El servicio de pulido devuelve una frase con palabras de contenido inventadas**: se descarta la
  frase y se pronuncia la glosa cruda (FR-040).

## Requirements *(mandatory)*

### Functional Requirements

**Captura y reconocimiento**

- **FR-001**: El control de grabación MUST estar accesible en todo momento durante la sesión, tanto
  para iniciar como para detener. Detener la grabación MUST apagar la cámara, no solo suspender el
  reconocimiento.
- **FR-002**: El sistema MUST ejecutar la extracción de keypoints y la clasificación de la seña
  íntegramente en el dispositivo de la persona usuaria. (Qué no puede cruzar la frontera del
  dispositivo se especifica en NFR-006; qué no puede persistirse, en NFR-007.)
- **FR-003**: El sistema MUST reconocer señas aisladas pertenecientes al vocabulario LSA64 (64
  señas), produciendo por cada captura una única seña candidata acompañada de un valor de confianza.
- **FR-004**: El sistema MUST indicar visualmente, de forma continua, si la grabación está activa o
  detenida.
- **FR-005**: El sistema MUST indicar visualmente cuando detecta a la persona (manos y torso dentro
  del cuadro) y cuando deja de detectarla.
- **FR-006**: El sistema MUST avisar cuando el encuadre impide el reconocimiento, distinguiendo al
  menos: manos fuera de cuadro, persona demasiado cerca, persona demasiado lejos, torso no visible.
- **FR-007**: La grabación MUST iniciarse y detenerse con una acción humana explícita, **una sola
  vez por conversación**. Esa acción MUST dejar ambas manos de la persona señante libres durante
  todo el uso: ningún control puede exigirle ocupar una mano mientras seña. El control MUST poder
  accionarse tanto por el interlocutor con el dispositivo en la mano como por la persona señante con
  el dispositivo apoyado, sin funcionalidad distinta entre ambos casos.
- **FR-008**: Con la grabación activa, el sistema MUST detectar por sí mismo el **inicio** y el
  **fin** de cada seña dentro del stream continuo, sin acción humana por seña. Detectar que hay
  actividad de señado es una capacidad distinta de clasificar qué seña es (FR-003) y MUST tratarse
  por separado: el sistema puede saber que empezó *alguna* seña sin saber cuál.
- **FR-009**: Por cada seña detectada, el sistema MUST realizar como máximo **3 intentos de
  reconocimiento**, correspondientes a hasta 3 segmentaciones candidatas del mismo evento. Si
  ninguno supera el umbral, MUST comunicar que no entendió sin presentar etiqueta candidata alguna.
- **FR-010**: El sistema MUST comunicar a la persona señante, de forma continua, en cuál de estos
  cuatro estados mutuamente excluyentes se encuentra: grabación detenida; grabando y detectándola;
  grabando pero sin detectarla; procesando una seña. Cada estado MUST tener un indicador visual
  distinto en color y en forma. En prueba con ≥5 personas señantes, ≥80% MUST identificar el estado
  correcto sin explicación previa.
- **FR-034**: El sistema MUST NOT producir traducción a partir de movimiento que no haya
  identificado como seña del vocabulario. Movimientos habituales que no son señas —acomodarse el
  pelo, saludar, gesticular al hablar, desplazarse, ajustarse la ropa— MUST NOT generar
  reconocimientos. Ante duda entre seña y no-seña, el sistema MUST callar: un falso positivo aquí
  produce una traducción inventada a partir de un gesto cualquiera, que es el peor resultado posible
  bajo el Principio VIII.
- **FR-035**: Cuando la detección de keypoints no es confiable, el sistema MUST avisar a la persona
  señante e indicarle qué corregir, distinguiendo al menos: no se la detecta, manos fuera de cuadro,
  detección intermitente. La distancia adecuada a la cámara MUST NOT prescribirse como número al
  usuario: la distancia correcta es aquella en la que la detección es confiable, y el sistema guía
  hacia ella con este aviso.
- **FR-036**: Dos señas consecutivas MUST producir dos reconocimientos distintos, y una seña
  sostenida o repetida MUST NOT producir reconocimientos duplicados en cadena.
- **FR-037 — Modo de respaldo manual**: el sistema MUST ofrecer un modo alternativo en el que una
  acción humana delimita el inicio de **una** captura individual, y el sistema detecta su fin como
  en FR-008. Usa el mismo pipeline de extracción, clasificación y decisión de confianza que el modo
  continuo; solo cambia el disparador. MUST poder activarse desde preferencias sin reinstalar ni
  recargar. Su razón de ser es de gestión de riesgo, no de producto: es el camino que queda si la
  segmentación continua no alcanza el criterio de NFR-022. En modo de respaldo, la acción de inicio
  MUST seguir dejando ambas manos de la persona señante libres durante la seña (FR-007).
- **FR-032**: El sistema MUST emitir una señal audible dirigida al interlocutor que le permita
  conocer, sin ver la pantalla, si el sistema está detectando a la persona señante. Esta señal
  complementa el feedback visual de FR-010, que es la vía de la persona señante; ningún estado queda
  cubierto solo por sonido (NFR-010) ni solo por imagen.

**Presentación de la traducción**

- **FR-011**: El sistema MUST mostrar la seña reconocida como texto en pantalla, dimensionado según
  NFR-011 para ser legible por la persona señante a 1–2 m antes de que el dispositivo se gire, y por
  el interlocutor a ~40 cm después. Ella necesita poder verificar qué se comunicó en su nombre.
- **FR-038**: La voz MUST agruparse por pausa, no dispararse por seña: el sistema acumula las glosas
  reconocidas y las pronuncia como bloque al detectar una pausa de la persona señante. Se considera
  pausa la ausencia de actividad de señado durante **1,5 segundos** — **valor provisional**, a
  calibrar con las personas participantes de NFR-021, porque el ritmo natural entre señas varía
  entre personas y una pausa mal medida corta frases al medio o las hace esperar de más. El
  **texto** de cada seña MUST mostrarse apenas se reconoce, sin esperar la pausa. El bloque MUST
  tener un máximo de **5 señas**, tras el cual se pronuncia aunque no haya habido pausa. Ese máximo
  acota además el tamaño de petición del servicio (NFR-026). Es un **valor provisional**: se calibra
  junto con la pausa de 1,5 s en las rondas de NFR-021, observando con qué frecuencia corta
  enunciados al medio.
- **FR-039**: Cuando se use un LLM para convertir glosas en frase natural (DD-003), el sistema MUST:
  (a) enviarle **únicamente la secuencia de glosas**, sin ningún otro dato de la sesión (DD-005);
  (b) alimentarlo
  **solo** con la secuencia de glosas reconocidas, nunca con video, frames ni keypoints; (c)
  mantener **visible la glosa cruda** junto a la frase pulida, para que se pueda distinguir qué señó
  la persona y qué agregó el modelo; (d) no incluir en la frase ninguna seña que no haya superado el
  umbral de confianza. El LLM MUST NOT reconocer, clasificar ni inferir señas (Principio II).
- **FR-040**: La frase generada MUST restringirse a **palabras de clase cerrada** añadidas
  —preposiciones, artículos, conjunciones, pronombres, auxiliares— y a la flexión de las glosas
  reconocidas. **Toda palabra de contenido** de la salida (sustantivo, verbo principal, adjetivo,
  adverbio) MUST trazar por lema a una glosa de la entrada. Si la salida contiene una palabra de
  contenido no trazable, el sistema MUST descartar la frase generada y pronunciar la glosa cruda en
  su lugar (DD-004).
- **FR-012**: El sistema MUST poder reproducir la traducción por voz en español. La selección de voz
  MUST seguir este orden de preferencia, tomando la etiqueta de locale que declara el dispositivo:
  (1) `es-AR`; (2) cualquier otra variante rioplatense declarada (`es-UY`); (3) cualquier `es-*`;
  (4) ninguna disponible → FR-012 no aplica y rige el escenario 3 de US2.
- **FR-013**: El sistema MUST presentar el nivel de confianza en 3 categorías nombradas —alta, media,
  no entendí— acompañadas de indicador visual, y MUST NOT presentarlo únicamente como número. Las
  fronteras entre categorías son las de FR-016: **"alta"** = confianza >= umbral del nivel
  *estricto* vigente; **"media"** = confianza entre el umbral activo y el de estricto; **"no
  entendí"** = por debajo del umbral activo. No se introduce ningún valor adicional: ambas fronteras
  se recalibran junto con los umbrales según NFR-019. En prueba con ≥5 participantes sin formación
  técnica, ≥80% MUST interpretar correctamente qué significa cada categoría. En el caso "no entendí"
  MUST presentarse solo esa categoría, sin valor numérico y sin etiqueta candidata.
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
- **FR-017**: Cuando ningún intento de reconocimiento de una seña detectada supera el umbral, el
  sistema MUST comunicar que no entendió y MUST NOT revelar, mostrar ni pronunciar ninguna etiqueta
  candidata de ninguno de los intentos.
- **FR-018**: Repetir una seña MUST NO requerir ninguna acción sobre el dispositivo: con la
  grabación activa, basta con volver a señar.
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

En esta sección, "la persona usuaria" es la **persona señante**: las preferencias afectan su
comunicación (umbral, espejo, la voz que habla en su nombre) y por eso ella es la dueña del diseño.
La pantalla de preferencias MUST ser operable sin audio (NFR-010); su usabilidad se valida con la
tarea T4 del protocolo de NFR-021, ejecutada por personas sordas sin ayuda. El interlocutor puede
accionarla físicamente cuando sostiene el dispositivo, sin funcionalidad distinta.

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

- **FR-031**: La pérdida de conectividad MUST NOT interrumpir ni degradar el **reconocimiento**, que
  es local. Sí degrada el **pulido**: sin red, el sistema MUST pronunciar la glosa cruda y avisar
  que la frase no pudo componerse, en lugar de quedarse callado o esperar. Verificable por SC-014.
  Distinción de alcance: *arrancar* la aplicación sin conexión está fuera de alcance; *seguir
  reconociendo* tras la caída de la red, no.
- **FR-041**: Antes de que cualquier glosa salga del dispositivo por primera vez, el sistema MUST
  informar a la persona usuaria, en la interfaz y en lenguaje directo, que activar el pulido envía
  las palabras reconocidas a un servidor, y MUST permitirle decidir en ese momento. No basta con
  declararlo en una política de privacidad. La opción de desactivarlo MUST seguir accesible después
  (NFR-025).

### Non-Functional Requirements

**Performance del reconocimiento**

- **NFR-001**: El desempeño del reconocimiento MUST medirse y reportarse en dos niveles, siempre
  juntos:
  - **NFR-001a — clasificador**: accuracy >= 0.85 sobre **LSA64 versión cut**, split POR SUJETO con
    **sujeto 10 held-out**, sobre secuencias ya recortadas. MUST registrarse la seed, la versión del
    dataset y las versiones exactas de dependencias. Es el baseline heredado de la fase exploratoria
    y el único número comparable con ella.
  - **NFR-001b — sistema desplegado**: accuracy **>= 0.70** extremo a extremo del procedimiento
    completo —grabación continua, detección de inicio y fin de cada seña, hasta 3 intentos, regla de
    parada y umbral de confianza— sobre personas no vistas en entrenamiento. Es el número que la
    persona usuaria experimenta, y por eso es el que tiene puerta. El 0.70 es deliberadamente el
    mismo de NFR-005 y de la puerta de NFR-022: incumplirlo y disparar el repliegue al modo manual
    de FR-037 son el mismo evento, no dos criterios que puedan contradecirse. Comparte con NFR-005
    el carácter **provisional** y su mismo criterio de revisión.
  - Reportar NFR-001a sin NFR-001b está prohibido. Sobre clips ya recortados no existe ni la
    segmentación ni la decisión de parada que el sistema real sí toma, de modo que NFR-001a **no
    caracteriza el sistema desplegado**. Toda diferencia entre ambos MUST atribuirse explícitamente
    a la segmentación y a la regla de parada.
- **NFR-022 — Costo de la segmentación (medición obligatoria)**: MUST medirse y reportarse por
  separado cuánto del error total introduce la segmentación automática, distinguiendo al menos:
  (a) señas realizadas que el sistema **no detectó**; (b) segmentos detectados que **no eran señas**
  (falsos positivos de FR-034); (c) señas detectadas pero **mal delimitadas**, que llegan al
  clasificador recortadas o fusionadas; (d) señas bien delimitadas y mal clasificadas. Sin este
  desglose es imposible saber si un fallo viene del modelo o de la segmentación, y la diferencia
  entre NFR-001a y NFR-001b queda sin explicar. La medición MUST hacerse contra una anotación humana
  de referencia sobre grabación externa, la misma que sustenta L2 en NFR-003.
  - **Puerta de decisión**: la primera medición completa de NFR-022 MUST ejecutarse en cuanto US1
    esté operativa, no al final del proyecto. Si con segmentación continua la accuracy no alcanza
    **0.70 en E1** —el entorno más favorable—, el modo de respaldo manual de FR-037 pasa a ser el
    predeterminado y la segmentación continua queda como funcionalidad opcional, documentando la
    decisión y la evidencia. Postergar esta medición al cierre elimina la posibilidad de reaccionar
    y convierte el respaldo en letra muerta.
- **NFR-002**: Toda métrica reportada MUST provenir de un split por sujeto. El split aleatorio está
  prohibido para reportes.

**Latencia**

- **NFR-003**: La latencia MUST medirse desde instantes **observables**. "Cuando la persona termina
  de señar" no lo es: determinar ese instante es el problema de segmentación que FR-008 delega en el
  propio sistema, de modo que usar su detector de fin haría que un detector más lento *mejorara* la
  latencia medida. Se definen dos métricas:
  - **L1 — proxy automatizable**: tiempo entre el fin de seña **detectado por el propio sistema** y
    la presentación del texto. Mide solo el costo de cómputo posterior a la segmentación, no la
    latencia percibida, y por eso **no sustituye a L2**; sirve como métrica de regresión en CI.
    MUST ser menor a **1 segundo** en el dispositivo de referencia — **valor provisional**, derivado
    de dejar margen dentro de los 2 s de L2 para el retardo de detección, y a revisar con la primera
    medición de L2. Sin umbral propio, L1 no puede hacer fallar a CI y no sirve como guarda de
    regresión.
  - **L2 — métrica constitucional (Principio IX)**: tiempo entre el último frame de la seña y la
    presentación. El último frame lo marca, offline y a ciegas respecto del resultado del sistema,
    una persona competente en LSA, sobre grabación tomada con **un dispositivo externo a la
    aplicación** —NFR-017(c) prohíbe que la aplicación registre video, y esa prohibición no se
    relaja para medir. L2 MUST ser < 2 s en al menos el 95% de una muestra de >= 50 capturas, en el
    dispositivo de referencia.
  - El presupuesto es íntegramente de cómputo local y los hasta 3 intentos de FR-008 se consumen
    dentro de él.
  - **Dispositivo de referencia — DECLARADO (Session 2026-07-29)**: **Samsung Galaxy A10**, con
    prioridad **mobile-first** — el teléfono es la referencia vinculante para L1 y L2; la notebook
    es secundaria (notebook genérica del equipo, modelo a asentar al registrar la primera medición).
    El A10 (2019) queda por debajo de la banda provisional original ("gama media de los últimos 4
    años") y se declara a sabiendas como objetivo más exigente: cumplir los 2 s en él implica
    cumplirlos en cualquier gama media actual. Su resolución y fps efectivos MUST medirse y
    registrarse al incorporarlo a la lista de NFR-020. **Criterio de revisión**: si el dispositivo
    declarado no alcanza los 2 s, MUST reportarse el percentil real alcanzado y decidirse
    explícitamente entre optimizar, subir el presupuesto con justificación en `research.md`
    (Principio IX), o declarar un dispositivo de referencia distinto — nunca dejar el número sin
    cumplir y sin decisión. Dado que el A10 es deliberadamente más exigente que la banda original,
    redeclarar hacia un gama media de los últimos 4 años es una salida prevista y no un fracaso del
    requisito.
  - Sin dispositivo de referencia declarado, NFR-003 no es verificable: un mismo sistema cumple o
    incumple según el hardware en que se lo mida.
  - **Alcance**: NFR-003 aplica a la presentación del **texto** de cada seña. La frase hablada,
    que espera la pausa y pasa por el LLM, tiene su propio presupuesto en NFR-023.
- **NFR-023**: El tiempo entre la pausa detectada y el comienzo de la frase hablada MUST ser menor a
  **3 segundos**, incluidos el viaje de ida y vuelta al servicio de pulido y la generación del LLM.
  - **Red de referencia**: **4G urbano**, con RTT entre 50 y 150 ms, medido y registrado en cada
    corrida del protocolo. No se mide sobre Wi-Fi: hacerlo produciría un requisito que se cumple en
    el escritorio y falla en la calle, que es donde el producto se usa.
  - **Degradación obligatoria**: excedido el presupuesto, el sistema MUST pronunciar la glosa cruda
    en lugar de seguir esperando (FR-031). El presupuesto es un límite de espera, no una aspiración.
  - Los 3 segundos y el rango de RTT son **valores provisionales**: se fijan sin medición previa de
    un modelo de este tamaño respondiendo sobre red móvil. **Criterio de revisión**: primera
    medición con el servicio desplegado; si no se alcanza, MUST decidirse explícitamente entre un
    modelo más chico, pronunciar siempre la glosa cruda, o subir el presupuesto con justificación en
    `research.md`.
  **Criterio de revisión**: si en la primera medición no se alcanza, MUST decidirse explícitamente
  entre un modelo más chico, pronunciar la glosa cruda sin pulir, o subir el presupuesto con
  justificación en `research.md` — nunca dejar al interlocutor esperando sin decisión tomada.

**Robustez medible**

- **NFR-004**: MUST existir un protocolo de prueba documentado con 3 entornos definidos por
  parámetros observables, cuyos valores efectivos MUST registrarse en cada sesión: iluminancia sobre
  el rostro (lux), distancia persona–cámara (m), resolución y tasa de cuadros efectivas, y tipo de
  fondo (estático liso / estático con textura / dinámico con personas).
  - **Mínimos comunes a los tres entornos**: resolución de captura >= 640 × 480 px y tasa de cuadros
    efectiva >= 15 fps. Por debajo de cualquiera de los dos la sesión es inválida y no computa.
  - **E1 — interior bien iluminado**: 300–750 lux, fondo estático liso o con textura, dispositivo
    apoyado.
  - **E2 — interior con luz pobre**: 50–150 lux, fondo estático liso o con textura, dispositivo
    apoyado.
  - **E3 — exterior en movimiento**: >= 1000 lux o contraluz, fondo dinámico con personas en
    movimiento, cámara sostenida a pulso por una segunda persona en desplazamiento. E3 exige
    **ambas** condiciones, exterior *y* movimiento; satisfacer solo una no cumple el entorno.
  - **La distancia persona–cámara se registra, no se impone**: se anota la distancia efectiva de cada
    sesión como dato de reproducibilidad, pero no es criterio de admisión. La distancia válida es
    aquella en la que la detección de keypoints es confiable, y el sistema guía hacia ella con
    FR-035. Una sesión no se descarta por distancia sino por detección fallida.
  - Toda sesión cuyos valores de iluminancia, resolución o fps caigan fuera del rango declarado MUST
    descartarse y repetirse. Las mediciones se recolectan con el build de evaluación de NFR-017.
  - Los rangos de lux son **provisionales**: se fijan sin medición de campo previa. MUST revisarse
    junto con NFR-005 tras la primera corrida completa del protocolo.
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

- **NFR-006**: La frontera de datos del build de producción es la de DD-005. **Video, frames,
  keypoints, identificadores, historial, descartes y preferencias MUST NOT abandonar el
  dispositivo**, sin excepción. La **secuencia de glosas** MUST poder enviarse únicamente al
  servicio de pulido de DD-003, solo cuando el pulido está activado, y sin ningún dato adicional que
  permita identificar o correlacionar sesiones. La otra excepción es el build de evaluación de
  NFR-017, que nunca se distribuye como producto.
- **NFR-024**: El servicio de pulido MUST NOT retener, registrar ni persistir las glosas recibidas
  ni sus respuestas, más allá del tiempo de procesamiento de la petición. MUST NOT registrar
  direcciones IP asociadas al contenido. El transporte MUST estar cifrado. Estas propiedades MUST
  ser verificables por inspección del código del servicio, que forma parte del proyecto y de su
  licencia.
- **NFR-025**: La persona usuaria MUST poder desactivar el pulido y seguir usando el sistema
  completo. Con el pulido desactivado, ninguna glosa sale del dispositivo y la voz pronuncia la
  glosa cruda. Esta opción MUST estar disponible sin penalización funcional distinta de la calidad
  de la frase hablada.
- **NFR-026 — Operación del servicio de pulido**: el servicio MUST declarar:
  - **Disponibilidad**: >= 95% durante las ventanas de evaluación declaradas (rondas de NFR-021 y
    corridas del protocolo de NFR-004). Fuera de ellas, mejor esfuerzo. El objetivo puede ser
    modesto porque la caída del servicio **no rompe el producto**: degrada a glosa cruda (FR-031).
  - **Límite de uso por origen**: máximo declarado de peticiones por minuto — valor inicial **30**,
    provisional, holgado frente a lo que produce una conversación real. Superado el límite, el
    cliente MUST degradar a glosa cruda, nunca quedar esperando ni reintentar en bucle.
  - **Tamaño máximo de petición**: **5 glosas**, el máximo de señas por bloque de FR-038. Toda
    petición mayor MUST rechazarse sin procesar.
  - **Validación contra vocabulario cerrado**: el servicio MUST rechazar toda petición cuyo
    contenido no sea una secuencia de glosas del vocabulario LSA64. Como el vocabulario tiene 64
    entradas conocidas, esta validación vuelve al endpoint **inútil como LLM de propósito general**,
    que es la defensa más barata y efectiva contra el abuso: no hay cuentas que administrar y no hay
    clave que extraer del cliente.
  - Estas propiedades MUST ser verificables por inspección del código del servicio, que forma parte
    del proyecto.
- **NFR-027 — Observabilidad del servicio sin contenido**: el servicio MUST exponer métricas
  **agregadas** suficientes para operarlo: cantidad de peticiones, distribución de latencia, tasa de
  error, tasa de rechazo por límite de uso y por validación de vocabulario, y disponibilidad. MUST
  NOT registrar la glosa recibida, la frase generada, ni ninguna dirección IP asociada a contenido,
  ni ningún identificador que permita correlacionar peticiones con una persona o sesión (NFR-024).
  Las métricas MUST ser agregadas, sin registros por petición que puedan reconstruir contenido
  cruzándolos. Contar peticiones y medir latencia no revela qué dijo nadie; guardar la glosa, sí, y
  esa es exactamente la línea. Sin estas métricas el servicio queda ciego: un fallo durante una
  sesión de evaluación con participantes sordos sería indiagnosticable, y la ronda se perdería.
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
    linspace **en su forma entera exacta**: `idx[i] = (i·(T−1)) div (N−1)` (división entera),
    idéntica en todos los productores y sin dependencia del punto flotante; la definición normativa
    completa está en el contrato versionado de keypoints (`contracts/keypoints/`). **Fixture**: conjunto versionado en el repositorio que cubra las 64 clases al menos una
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
- **NFR-019**: Los umbrales de confianza MUST compensar el número de evaluaciones por evento de seña
  detectada. Antes
  de fijar los valores de estricto / normal / permisivo MUST medirse la tasa de falsos positivos con
  1, 2 y 3 intentos sobre el conjunto de test por sujeto. Los umbrales MUST elegirse de modo que la
  tasa de falsos positivos con 3 intentos no supere en más de 2 puntos porcentuales a la de 1
  intento. La medición y los valores elegidos MUST documentarse. Sin esta compensación, la regla de
  parada de FR-008 busca activamente una ventana que produzca confianza alta, lo que infla la
  confianza aparente y erosiona el Principio VIII. El margen de 2 puntos es provisional y MUST
  revisarse con la primera medición.

### Constitutional Requirements *(mandatory — see `.specify/memory/constitution.md`)*

- **Privacy (Principle VII)**: el video crudo nunca sale del dispositivo, sin excepción. Los
  keypoints tampoco, porque el reconocimiento es local — más estricto que el mínimo constitucional,
  que sí los permitiría viajar. Lo que sale, y solo con el pulido activado, es la **secuencia de
  glosas** hacia el servicio de DD-003. Esa frontera está declarada en DD-005 y acotada por NFR-006,
  NFR-024, NFR-025 y FR-041. El build de evaluación (NFR-017) exporta solo métricas, con
  consentimiento, y nunca video.
- **Explicit confidence (Principle VIII)**: por debajo del umbral el sistema dice "no entendí" y no
  revela la etiqueta candidata (FR-017). La confianza se muestra siempre (FR-013). La UI declara de
  forma persistente que Helpi asiste y no reemplaza a un intérprete (FR-029).
- **Latency (Principle IX)**: presupuesto de 2 s medido como L2 —desde el último frame de la seña,
  anotado a ciegas sobre grabación externa, hasta la presentación (NFR-003)—, íntegramente de
  cómputo en el dispositivo e incluidos los hasta 3 intentos. L1 sirve de proxy automatizable en CI.
  Medir desde el detector de fin del propio sistema queda prohibido por circular.
- **Temporal segmentation debt (Principle XII)**: DD-002 **asume la deuda completa** como trabajo
  central: el sistema detecta inicio y fin de cada seña sobre stream continuo (FR-008), sin
  delimitación humana por seña. Deja de ser deuda diferida y pasa al camino crítico. El Principio
  XII exige que esta deuda se salde y se trace como tarea; esta feature la salda, con el riesgo que
  DD-002 documenta y las mediciones que NFR-022 obliga.
- **Evaluation (Principle V)**: métrica reportable = accuracy con split por sujeto sobre LSA64 cut,
  sujeto 10 held-out, contra el baseline 0.85 (NFR-001a), reportada siempre junto a la métrica
  extremo a extremo del sistema desplegado (NFR-001b). La evaluación de campo por entorno (NFR-004,
  NFR-005) es una tercera medición distinta y se reporta por separado.
- **Data contract (Principle IV)**: los productores obligados son el cliente de la aplicación y el
  preprocesamiento de entrenamiento, ambos cubiertos por el test de Nivel 1 de NFR-014, bloqueante
  de CI con tolerancia 1e-6. El Nivel 2 es informativo porque la igualdad extremo a extremo entre
  implementaciones distintas de MediaPipe no es alcanzable.

### Key Entities

- **Sesión de grabación**: intervalo entre la acción humana de iniciar y la de detener. Contiene cero
  o más eventos de seña detectada. Es la única unidad que requiere acción humana.
- **Evento de seña detectada**: tramo del stream que el sistema identificó como actividad de señado,
  con su instante de inicio y de fin. Puede resultar en una seña reconocida, en un "no entendí" tras
  agotar los 3 intentos, o en un descarte por pérdida de detección. Existe independientemente de que
  se logre clasificarlo: el sistema puede saber que hubo una seña sin saber cuál.
- **Intento de reconocimiento**: cada una de las hasta 3 segmentaciones candidatas que el sistema
  evalúa sobre un mismo evento de seña detectada. Atributos: número de intento, límites del segmento,
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
  cut, con sujeto 10 held-out y seed y dependencias registradas (NFR-001a); **y** el sistema
  desplegado alcanza al menos 0.70 extremo a extremo sobre personas no vistas (NFR-001b). Ambos se
  reportan juntos, con la diferencia atribuida explícitamente a la segmentación y a la regla de
  parada.
- **SC-002**: En al menos el 95% de una muestra de >= 50 capturas, la latencia L2 —desde el último
  frame de la seña, anotado a ciegas por una persona competente en LSA sobre grabación externa,
  hasta la presentación— es menor a 2 segundos en el dispositivo de referencia.
- **SC-003**: El desempeño se mide y reporta por separado en los 3 entornos definidos en NFR-004
  (E1, E2, E3), con sus parámetros efectivos de lux, distancia, resolución, fps y fondo registrados
  por sesión, sobre el subconjunto congelado de 10 señas de NFR-018, con al menos 10 intentos por
  seña en cada entorno, y alcanza al menos 0.70 de accuracy en cada entorno.
- **SC-004**: Con el pulido desactivado, cero peticiones de red salen del dispositivo durante una
  sesión completa. Con el pulido activado, la inspección del tráfico saliente muestra únicamente
  secuencias de glosas hacia el servicio de pulido: cero frames, cero keypoints, cero
  identificadores de sesión o de persona.
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
- **SC-011**: En el 100% de los eventos inválidos provocados (manos perdidas a mitad de seña,
  material insuficiente), el sistema descarta y explica el motivo, y en cero casos clasifica el
  tramo.
- **SC-012**: Ningún evento de seña detectada ejecuta más de 3 intentos de reconocimiento,
  verificable por instrumentación local.
- **SC-018**: Sobre una sesión continua con 20 señas separadas por pausas naturales, el sistema
  produce exactamente 20 eventos de seña detectada: cero fusiones de dos señas en una, cero
  duplicados de una misma seña.
- **SC-019**: Sobre una sesión de 3 minutos en la que la persona no seña —conversa, se acomoda el
  pelo, gesticula, se desplaza— el sistema produce **cero** traducciones (FR-034).
- **SC-020**: El informe de evaluación desglosa el error según las cuatro categorías de NFR-022 (no
  detectada / falso positivo / mal delimitada / mal clasificada), de modo que la diferencia entre
  NFR-001a y NFR-001b queda explicada y no meramente reportada.
- **SC-021**: Sobre un conjunto de al menos 50 secuencias de glosas, el 100% de las palabras de
  contenido de las frases generadas traza por lema a una glosa de entrada. Cero palabras de
  contenido inventadas (FR-040).
- **SC-022**: En el 100% de las presentaciones, la glosa cruda es visible junto a la frase pulida,
  de modo que un observador puede distinguir qué señó la persona y qué agregó el modelo.
- **SC-023**: En una instalación nueva, ninguna glosa sale del dispositivo antes de que la persona
  haya visto el aviso de FR-041 y decidido. Verificable por inspección del tráfico saliente durante
  el primer uso.
- **SC-024**: Con el pulido desactivado, el sistema completa una sesión de 10 señas con voz agrupada
  por pausa pronunciando glosa cruda, sin ninguna petición de red y sin degradación del
  reconocimiento.
- **SC-025**: El servicio de pulido rechaza el 100% de las peticiones cuyo contenido no sea una
  secuencia de glosas del vocabulario LSA64, verificable con un conjunto de peticiones adversarias
  que incluya texto libre.
- **SC-026**: Con el servicio caído, con el límite de uso excedido o con la red por encima del
  presupuesto de NFR-023, el sistema completa una sesión de 10 señas pronunciando glosa cruda, sin
  bloqueos, sin esperas visibles y sin reintentos en bucle.
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
- Traducción de **frases**: el sistema segmenta y traduce señas aisladas dentro de un stream
  continuo, pero no interpreta gramática, orden ni concordancia de LSA. La salida es una secuencia
  de palabras sueltas, no una oración en español. (La segmentación temporal continua **sí** está en
  alcance desde DD-002; lo que queda fuera es la interpretación lingüística de lo segmentado.)
- Inferencia en servidor y cualquier servicio remoto de reconocimiento.
- **Funcionamiento sin conexión**: arrancar la aplicación sin red, empaquetado y cacheo local para
  uso offline, y todo presupuesto de tamaño de descarga asociado. El reconocimiento sigue siendo
  local —de ahí que la privacidad no cambie— pero cargar la aplicación requiere conexión y no se
  garantiza ni se testea el arranque sin ella. Es la primera candidata a recuperar si el MVP valida,
  porque el subte y la calle son escenarios centrales del producto.
- **LLM de terceros**: el pulido glosa→frase corre en un servicio propio del proyecto (DD-003). Queda
  fuera de alcance delegarlo a una API comercial de terceros, que agregaría un destinatario no
  controlado para lo que una persona sorda está diciendo y sobre el que el proyecto no puede
  garantizar NFR-024.
- **Inferencia del reconocimiento en servidor**: el clasificador permanece en el dispositivo. Aunque
  la constitution permitiría transmitir keypoints, moverlo allá no aporta nada una vez que corre
  local, y ampliaría la frontera de datos sin necesidad.
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

Con la aplicación ya cargada y sin red, el **reconocimiento sigue funcionando** (es local) pero el
**pulido no**: la voz pronuncia la glosa cruda, "agua, beber, gracias" en lugar de una oración. El
sistema no deja de servir, pero entrega menos.

**El funcionamiento sin conexión es requisito de una iteración posterior, no una mejora opcional.**
Es la primera candidata a recuperar si el MVP valida. Hasta entonces, toda comunicación pública del
proyecto MUST describir el alcance como "requiere conexión para abrirse", sin sugerir cobertura del
escenario de transporte.

### Otras limitaciones declaradas

- **Vocabulario de 64 señas**: no es una lengua, es un subconjunto cerrado. Una conversación real
  excede este vocabulario casi de inmediato (FR-029 lo declara en la interfaz).
- **Señas aisladas, no frases**: el sistema segmenta señas dentro de un stream continuo, pero no
  interpreta gramática ni orden de LSA. La salida es una secuencia de palabras sueltas, no una
  oración. La traducción lingüística queda para LSA-T.
- **Lateralidad no verificada**: LSA64 no declara la lateralidad de sus sujetos, de modo que el
  proyecto **no puede afirmar** que el reconocimiento sea independiente de la mano dominante. Se
  reporta desagregado y, si hay diferencia, se declara en la interfaz.
- **Dirección única**: el sistema traduce de LSA a español. La respuesta del interlocutor oyente
  hacia la persona sorda no está cubierta por ningún requisito; ocurre por los medios que las
  personas ya usaran antes (escribir, gestos, lectura labial).
- **Sin modo restringido al entregar el dispositivo**: el modelo de uso implica entregar el teléfono
  desbloqueado a un interlocutor que puede ser un desconocido, y una aplicación web no puede impedir
  que quien lo sostiene salga de ella. No hay modo kiosco en el MVP (Session 2026-07-29, CHK009).
  Mitigación dentro de lo controlable: historial efímero y sin datos personales expuestos en sesión
  (NFR-008), y control de detener la grabación siempre accesible (FR-001). Un modo kiosco real queda
  como candidato para la eventual iteración nativa.

## Assumptions

- **Un solo dispositivo, sostenido o apoyado**: la cámara frontal apunta a la persona señante y ella
  se ve a sí misma. Sostenerlo da mejor encuadre porque alguien corrige el ángulo; apoyarlo permite
  a la persona señante iniciar sola. No requiere funcionalidad distinta ni se contempla emparejar
  dos dispositivos.
- **La cámara puede estar apoyada o sostenida a pulso**: sostenida introduce movimiento de cámara,
  que es condición normal de uso y no un caso degradado.
- **La voz importa mucho con el dispositivo apoyado**: en esa disposición la pantalla mira a la
  persona señante, de modo que la voz es el único canal que alcanza al interlocutor sin manipular el
  aparato. US1 sin US2 entrega bastante menos valor cuando el dispositivo está apoyado.
- **MVP web**: la persona accede desde un navegador en computadora o teléfono; no hay instalación.
  Cargar la aplicación requiere conexión; una vez cargada, el reconocimiento no la necesita.
- **La privacidad del reconocimiento no depende de la red**: video y keypoints nunca se transmiten
  porque la inferencia es local. Lo único que puede viajar son las glosas, y solo hacia el servicio
  de pulido, que es desactivable (DD-005, NFR-025).
- **El pulido depende de la red; el reconocimiento no**: sin conexión el sistema sigue reconociendo
  y hablando, con glosa cruda. Es la degradación deliberada de FR-031.
- **Segmentación continua** (DD-002): una acción humana por conversación; el sistema detecta inicio y
  fin de cada seña. Se asume que el problema que la fase exploratoria dejó sin resolver —ventana
  deslizante con confianza inestable— es abordable en el plazo del proyecto. **Es el supuesto más
  fuerte de toda la spec**, y por eso es el único que tiene camino de repliegue construido de
  antemano (FR-037) y una puerta de decisión con fecha y número (NFR-022).
- **Entrenamiento con señas recortadas vs. uso sobre stream continuo**: LSA64 versión cut no contiene
  transiciones ni reposo, que sí abundan en el uso real. Cerrar esa brecha —por aumento de datos,
  por reentrenamiento con material continuo, o por diseño del segmentador— es trabajo de la fase de
  plan, y NFR-022 obliga a medir cuánto cuesta.
- **Riesgo de parada opcional (optional stopping)**: si el criterio para dejar de grabar es "hasta
  que el clasificador esté seguro", el sistema busca activamente una ventana que produzca confianza
  alta, lo que infla la confianza aparente y aumenta los falsos positivos. Esto presiona
  directamente el Principio VIII. El tope de 3 intentos acota el problema pero no lo elimina; por eso
  la compensación dejó de ser un supuesto y es hoy un requisito verificable (**NFR-019**, **SC-016**).
- **Distinguir seña de no-seña**: problema nuevo introducido por DD-002 que no existía con captura
  por seña, donde la acción humana garantizaba que lo grabado era un intento de señar. Ahora el
  sistema debe decidirlo solo, sobre movimiento humano espontáneo. FR-034 fija el comportamiento
  ante duda —callar—, pero la técnica queda para la fase de plan.
- **Dispositivo de referencia**: ver NFR-003, donde quedó declarado como valor provisional con
  criterio y momento de revisión.
- **Tensión reconocimiento local vs. baseline**: ejecutar el modelo en el dispositivo puede exigir
  una versión más liviana que la validada en la fase exploratoria. NFR-001a mide esa configuración
  sobre el dataset; toda caída por debajo de 0.85 requiere justificación escrita según el Principio
  V. Resolver esta tensión es trabajo de la fase de plan.
- **Decisiones diferidas a la fase de plan, con dueño**: (a) modelos concretos del dispositivo de
  referencia (NFR-003); (b) cota empírica del Nivel 2 del contrato de keypoints (NFR-014); (c) rango
  de duración de seña con el que el reconocimiento se mantiene sobre el umbral; (d) dónde se
  despliega el servicio de pulido. Ninguna es una omisión: las cuatro dependen de mediciones o
  decisiones de infraestructura que aún no existen, y todas tienen un requisito que las obliga. Los
  valores numéricos de los umbrales de confianza dejaron de estar diferidos: están en FR-016 como
  provisionales, sujetos a la recalibración de NFR-019.
- **Vocabulario cerrado**: las 64 etiquetas de LSA64 se presentan con su traducción al español; no
  hay ampliación de vocabulario por parte de la persona usuaria.
- **Historial efímero**: el historial de sesión no sobrevive al cierre de la aplicación; solo las
  preferencias persisten.
- **Valores provisionales declarados**: todos los números de esta spec fijados sin evidencia llevan
  criterio y momento de revisión escritos en su propio requisito — umbrales de confianza (FR-016),
  0.70 de robustez y de sistema desplegado (NFR-005, NFR-001b), rangos de lux (NFR-004), dispositivo
  de referencia y umbral de L1 (NFR-003), margen de 2 puntos (NFR-019), presupuesto de 3 s y red de
  referencia 4G (NFR-023), pausa de 1,5 s y máximo de 5 señas por bloque (FR-038), y límite de 30
  peticiones por minuto (NFR-026). Ninguno se presenta como definitivo.
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
