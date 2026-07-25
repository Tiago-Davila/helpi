# Checklist de Testeabilidad: Traductor LSA de señas aisladas (LSA64)

**Purpose**: Determinar si cada requisito de la spec puede verificarse de forma objetiva por una
persona distinta de quien lo escribió, sin pedir aclaraciones.
**Created**: 2026-07-25
**Feature**: [spec.md](../spec.md)
**Alcance de esta revisión**: la especificación como documento. No evalúa implementación (no
existe), no evalúa `plan.md` (aún no generado).

## Cómo leer los estados

| Estado | Significado |
|--------|-------------|
| **OK** | El requisito puede verificarse objetivamente tal como está escrito. |
| **CORREGIR** | El requisito existe pero no es verificable sin interpretación. Se propone reformulación medible. |
| **AMBIGUO** | Falta una decisión, no una redacción. Requiere que alguien decida, no que alguien reescriba. |

---

## 1. Completitud

| ID | Ítem | Estado | Hallazgo / Reformulación propuesta |
|----|------|--------|-------------------------------------|
| CHK001 | ¿Cada historia de usuario tiene criterios de aceptación? [Completeness, Spec §US1–US7] | OK | Las 7 historias tienen entre 3 y 5 escenarios Given/When/Then. |
| CHK002 | ¿Existe una historia que cubra el rol operativo del interlocutor (sostener, apuntar, iniciar captura, girar)? [Gap] | CORREGIR | Tras la sesión de clarify el interlocutor pasó de lector pasivo a **operador del sistema**: es quien encuadra, inicia la captura y decide cuándo girar. Ninguna historia cubre ese rol; US2 solo lo trata como receptor de voz. Añadir *US8 – Operar la captura para otra persona*, con criterios sobre encuadrar sin ver la pantalla, iniciar captura y saber cuándo terminó. |
| CHK003 | ¿Cada requisito funcional tiene al menos un criterio verificable asociado? [Traceability] | CORREGIR | Sin cobertura: **FR-001** (detener la cámara no aparece en ningún escenario ni SC), **FR-021**, **FR-022**, **FR-031**. Añadir escenario o SC para cada uno. |
| CHK004 | ¿FR-031 conserva una vía de verificación tras eliminarse US8 y SC-011? [Gap, Spec §FR-031] | CORREGIR | Al descartarse el funcionamiento sin conexión se eliminaron US8 y SC-011, pero FR-031 quedó en el documento **sin historia, sin escenario y sin criterio de éxito**: es hoy el único requisito huérfano. Añadir SC: *"Con la conectividad deshabilitada tras la carga, una sesión de 10 capturas se completa sin errores de red visibles ni degradación medible del reconocimiento."* |
| CHK005 | ¿Cada requisito no funcional declara métrica **y** método de medición? [Measurability] | CORREGIR | Declaran métrica sin método: NFR-007, NFR-009, NFR-011, NFR-014, NFR-016. Declaran método incompleto: NFR-001, NFR-003, NFR-004. Detalle en la dimensión 4. |
| CHK006 | ¿Los tres actores declarados tienen cobertura? [Coverage, Spec §Actores] | CORREGIR | Persona señante: US1, US3, US4, US6, US7 ✓. Interlocutor: solo US2 (ver CHK002). **"Sistema" está listado como actor pero no es un stakeholder: es el sujeto bajo prueba.** Listarlo junto a personas confunde el modelo de actores; moverlo a una sección de "componentes" o eliminarlo. |
| CHK007 | ¿Hay requisitos implícitos en el objetivo que no estén numerados? [Gap] | OK | El objetivo (accesibilidad, texto+voz, confianza explícita, condiciones reales) traza a FR-003, FR-011/012, FR-013/016/017, NFR-004/005. |
| CHK008 | ¿Está especificado quién de los dos actores humanos configura las preferencias? [Gap, Spec §FR-016, §FR-024–027] | AMBIGUO | FR-016 y FR-025 dicen "la persona usuaria" sin resolver cuál. El dispositivo lo sostiene el interlocutor pero las preferencias afectan sobre todo a la persona señante (umbral, espejo). Decidir y escribirlo. |
| CHK009 | ¿Se especifica de quién es el dispositivo y qué pasa al entregarlo a un desconocido? [Gap] | AMBIGUO | El modelo de uso implica que la persona señante entrega su teléfono desbloqueado a un interlocutor que puede ser un desconocido en la calle. La spec no dice si hace falta un modo restringido. Es una decisión de alcance, no de redacción. |

---

## 2. Claridad y ausencia de subjetividad

Todos los términos siguientes aparecen en requisitos normativos (MUST) y ninguno es medible tal
como está escrito.

| ID | Término y ubicación | Estado | Reformulación propuesta |
|----|---------------------|--------|--------------------------|
| CHK010 | **"seña completa"** [Spec §FR-008] | CORREGIR | Es el término más crítico del documento: define cuándo el sistema deja de grabar, y no está definido en ninguna parte. Proponer: *"Una captura se considera terminada cuando un intento de reconocimiento supera el umbral, o cuando se alcanza la duración máxima. 'Seña completa' no es un estado observable independiente del clasificador y no debe usarse como criterio."* |
| CHK011 | **"material suficiente para reconocer"** [Spec §FR-010] | CORREGIR | Proponer: *"...que contenga menos de N frames con ambas manos detectadas, siendo N el mínimo de frames que el preprocesamiento requiere para muestrear la secuencia."* |
| CHK012 | **"comprensible para personas no técnicas"** [Spec §FR-013] | CORREGIR | Proponer: *"La confianza se presenta en 3 categorías nombradas (alta / media / no entendí). En prueba con ≥5 participantes sin formación técnica, ≥80% interpreta correctamente qué significa cada categoría en una tarea de clasificación de ejemplos."* |
| CHK013 | **"legible"** a 1–2 m y a ~40 cm [Spec §FR-011, §NFR-011] | CORREGIR | La distancia está acotada pero no el tamaño ni el contraste. Proponer altura mínima de caracter en función de la distancia (regla de ángulo visual) y ratio de contraste mínimo, más la condición de iluminación bajo la que se valida. |
| CHK014 | **"de uso común"** (cámaras) [Spec §FR-021] | CORREGIR | Proponer: *"Se declara una lista de al menos 3 dispositivos de prueba con resolución y fps efectivos registrados; el requisito se da por cumplido si el sistema alcanza el criterio de NFR-005 en todos ellos."* |
| CHK015 | **"fondo no controlado"** [Spec §FR-022] | CORREGIR | Proponer clasificación observable: estático liso / estático con textura / dinámico con personas en movimiento; declarar cuáles deben soportarse. |
| CHK016 | **"buena luz" / "luz pobre"** [Spec §NFR-004] | CORREGIR | Ver CHK029: requieren rangos de lux. |
| CHK017 | **"condiciones de luz normales"** [Spec §SC-007] | CORREGIR | Sustituir por el rango de lux del entorno E1 definido en CHK029. |
| CHK018 | **"accionable"** (mensajes de error) [Spec §FR-023] | CORREGIR | Proponer: *"Todo mensaje de condición impeditiva nombra la causa y una acción concreta que la persona puede ejecutar. Verificable por revisión: cada mensaje del catálogo contiene un verbo de acción dirigido a quien lo lee."* |
| CHK019 | **"inequívoca"** (estados de captura) [Spec §FR-009] | CORREGIR | Proponer: *"Los 4 estados son mutuamente excluyentes y cada uno tiene un indicador visual distinto en color y forma. En prueba con ≥5 personas señantes, ≥80% identifica el estado correcto sin explicación previa."* |
| CHK020 | **"iconografía clara"** [Spec §NFR-009] | CORREGIR | Absorber en el protocolo de SC-006 (ver CHK034); por sí solo no es verificable. |
| CHK021 | **"lenguaje llano"** [Spec §FR-025] | CORREGIR | Proponer un criterio objetivo de legibilidad textual, o validarlo dentro del mismo protocolo de usabilidad. |
| CHK022 | **"voz rioplatense"** [Spec §FR-012] | AMBIGUO | No hay criterio para determinar si una voz del dispositivo lo es. Proponer: seleccionar por etiqueta de locale (`es-AR`) y, si no existe, cualquier `es-*`, declarando el orden de preferencia. |
| CHK023 | **"inteligible"** (voz) [Spec §US2 Independent Test] | CORREGIR | Proponer: *"≥5 oyentes hispanohablantes transcriben correctamente ≥95% de las palabras pronunciadas en una muestra de 20 reproducciones."* |
| CHK024 | **"movimiento propio de una cámara sostenida a pulso"** [Spec §NFR-016] | CORREGIR | Sin magnitud, no es testeable. Proponer acotarlo por el resultado y no por el movimiento: *"El criterio de NFR-005 se alcanza en el entorno E3, cuya definición incluye cámara sostenida a pulso."* Con eso NFR-016 puede eliminarse por redundante. |

---

## 3. Consistencia

| ID | Ítem | Estado | Hallazgo / Reformulación propuesta |
|----|------|--------|-------------------------------------|
| CHK025 | ¿La regla de parada de FR-008 es coherente con la condición de FR-017? [Conflict, Spec §FR-008 vs §FR-017] | CORREGIR | FR-008 detiene la captura **cuando** se supera el umbral. Por construcción, entonces, ninguna captura termina "por debajo del umbral": eso solo ocurre al agotar los 3 intentos. Pero FR-017 está redactado como si hubiera una comprobación por reconocimiento ("cuando la confianza queda por debajo del umbral"). Reescribir FR-017: *"Cuando ningún intento de la captura supera el umbral, el sistema comunica que no entendió y no revela ninguna etiqueta candidata."* |
| CHK026 | ¿Qué confianza se muestra cuando no hubo reconocimiento? [Ambiguity, Spec §FR-013 vs §FR-017] | CORREGIR | FR-013 exige mostrar siempre el nivel de confianza; FR-017 prohíbe revelar la candidata. Falta decir qué se muestra en el caso "no entendí". Proponer: *"En el caso 'no entendí' se presenta la categoría 'no entendí' sin valor numérico ni etiqueta."* |
| CHK027 | ¿Hay requisitos duplicados o solapados? [Consistency] | CORREGIR | **FR-002 / NFR-006 / NFR-007** dicen tres veces, con alcances ligeramente distintos, que el video no sale ni se almacena. **FR-002** además mezcla dos requisitos (dónde se ejecuta el cómputo; qué no puede transmitirse). Separar: FR-002 = localidad del cómputo; NFR-006 = qué no cruza la frontera del dispositivo; NFR-007 = qué no se persiste. Hoy los tres se pisan y un cambio en uno deja los otros desactualizados. |
| CHK028 | ¿El alcance declarado coincide con los requisitos listados? [Consistency, §Out of Scope vs §FR-031] | CORREGIR | Out of Scope descarta el funcionamiento sin conexión, pero FR-031 sigue exigiendo que la pérdida de conectividad no interrumpa el reconocimiento. Son compatibles (una cosa es arrancar sin red, otra sobrevivir a su caída) pero el documento no lo distingue con claridad suficiente para que un tercero sepa qué debe testear. |
| CHK029 | ¿El título "en tiempo real" describe el modelo de interacción resultante? [Consistency] | AMBIGUO | El Input original dice "en tiempo real"; la interacción resultante es por turnos (apretar, señar, esperar, girar). Decidir si se conserva el término o se ajusta a "por turnos, con latencia conversacional". |
| CHK030 | ¿Algún requisito contradice la constitution? [Consistency, Constitution] | OK | Sin contradicciones directas. Los principios II, III, IV, V, VII, X, XII trazan a requisitos concretos. La presión sobre el Principio VIII se trata en CHK031. |
| CHK031 | ¿El riesgo de parada opcional está sujeto a un requisito verificable? [Gap, Constitution Principle VIII] | **CORREGIR (bloqueante)** | El riesgo está documentado en *Assumptions*, no como requisito. Los supuestos no son puertas: nadie falla un PR por incumplir un supuesto. Grabar "hasta que el clasificador esté seguro" hace que el sistema **busque** una ventana que produzca confianza alta, lo que infla la confianza aparente. Promover a requisito numerado: *"NFR-019: Antes de fijar los valores de estricto/normal/permisivo MUST medirse la tasa de falsos positivos con 1, 2 y 3 intentos sobre el conjunto de test por sujeto. Los umbrales MUST elegirse de modo que la tasa de falsos positivos con 3 intentos no supere la de 1 intento en más de [X] puntos porcentuales. La medición y los valores elegidos MUST documentarse."* |

---

## 4. Testeabilidad específica del proyecto

### 4a. Métricas del modelo

| ID | Ítem | Estado | Hallazgo / Reformulación propuesta |
|----|------|--------|-------------------------------------|
| CHK032 | ¿NFR-001 especifica dataset, sujetos held-out, seed y versiones de dependencias? [Measurability, Spec §NFR-001] | CORREGIR | Dice "split por sujeto sobre LSA64" y nada más. Falta: variante del dataset (la constitution fija **LSA64 versión cut**), **qué sujeto queda held-out** (la constitution dice sujeto 10), seed, y versiones de dependencias. Sin eso el 0.85 no es reproducible por un tercero. Reformular incorporando los cuatro. |
| CHK033 | ¿La métrica de NFR-001 caracteriza el sistema que la persona usuaria experimenta? [Conflict, Spec §NFR-001 vs §FR-008] | **CORREGIR (bloqueante)** | NFR-001 mide el **clasificador** sobre clips de LSA64 ya recortados. El sistema desplegado no es eso: incluye inicio manual, tiempo muerto inicial, hasta 3 intentos y una regla de parada. Sobre clips pre-recortados **no hay decisión de parada que tomar**, así que el 0.85 no dice nada sobre el comportamiento real. Desdoblar: **NFR-001a (clasificador)** = 0.85 sobre LSA64 cut, sujeto 10 held-out, seed y dependencias registradas — comparable con la fase exploratoria; **NFR-001b (sistema desplegado)** = accuracy extremo a extremo del procedimiento completo sobre sujetos no vistos. Ambos MUST reportarse juntos, y toda diferencia MUST atribuirse explícitamente a la regla de parada. |

### 4b. Latencia

| ID | Ítem | Estado | Hallazgo / Reformulación propuesta |
|----|------|--------|-------------------------------------|
| CHK034 | ¿El instante desde el cual se mide la latencia es observable y no circular? [Measurability, Spec §NFR-003] | **CORREGIR (bloqueante)** | NFR-003 mide "desde el momento en que la persona termina de señar". Determinar ese instante **es exactamente el problema de segmentación temporal** que la constitution declara no resuelto (Principio XII) y que FR-008 delega en el propio sistema. Medir la latencia con el detector de fin del sistema hace que el criterio dependa de lo que se quiere evaluar: si el detector se retrasa, la latencia medida **mejora**. Es circular y además premia el fallo. |
| CHK035 | ¿Existe una métrica de latencia instrumentable sin juicio humano? [Gap] | CORREGIR | Proponer desdoblar en dos: **L1 (proxy de CI, plenamente observable)** = tiempo entre la acción de inicio de captura y la presentación; no requiere saber dónde terminó la seña y sirve como test automatizable de regresión. **L2 (métrica constitucional)** = tiempo entre el último frame de la seña y la presentación, donde el último frame lo marca **una persona competente en LSA, offline y a ciegas** respecto del resultado del sistema, sobre una muestra de ≥50 capturas. Umbral 2 s aplica a L2 en ≥95% de la muestra. |
| CHK036 | ¿La medición de L2 es compatible con la prohibición de registrar video? [Conflict, Spec §NFR-017(c) vs CHK035] | CORREGIR | Anotar el último frame de la seña exige material visual, pero NFR-017(c) prohíbe al build de evaluación registrar video "bajo ninguna circunstancia". Resolver explícitamente: la grabación para anotación se hace con **un dispositivo externo, no con la aplicación**, con consentimiento del participante. Así NFR-017(c) queda intacto. Alternativa más barata: criterio cinemático independiente fijado a priori (velocidad de manos bajo umbral durante N ms) — objetivo y no circular, aunque discutible lingüísticamente. |

### 4c. Umbral de confianza

| ID | Ítem | Estado | Hallazgo / Reformulación propuesta |
|----|------|--------|-------------------------------------|
| CHK037 | ¿Los niveles estricto / normal / permisivo tienen valores numéricos definidos? [Gap, Spec §FR-025] | AMBIGUO | No existen valores en ninguna parte de la spec; *Assumptions* los difiere a la fase de plan. Es defendible (dependen de la curva de confianza medida), pero entonces FR-016 y FR-025 **no son testeables hoy** y debe quedar escrito como decisión diferida con dueño y momento, no como omisión. |
| CHK038 | ¿Existe criterio de aceptación para el comportamiento por debajo del umbral? [Acceptance Criteria, Spec §SC-005] | CORREGIR | SC-005 exige 100 reconocimientos bajo umbral con cero filtraciones de la candidata, pero **no dice cómo se generan** esos 100 casos de forma reproducible. Proponer: *"...usando el subconjunto congelado de NFR-018 ejecutado con umbral en modo estricto más gestos deliberadamente fuera de vocabulario, en proporción declarada."* |
| CHK039 | ¿Se especifica el efecto de cambiar de nivel sobre la tasa de "no entendí"? [Measurability, Spec §US7] | OK | El Independent Test de US7 define la dirección esperada del cambio sobre el mismo conjunto de señas. Verificable. |

### 4d. Robustez de entorno

| ID | Ítem | Estado | Hallazgo / Reformulación propuesta |
|----|------|--------|-------------------------------------|
| CHK040 | ¿Los tres entornos están definidos por parámetros observables? [Measurability, Spec §NFR-004] | **CORREGIR (bloqueante)** | "Interior con buena luz", "interior con luz pobre" y "exterior o en movimiento" no son condiciones reproducibles: dos personas medirían cosas distintas y la puerta de 0.70 dejaría de ser comparable entre corridas. Proponer definirlos por iluminancia sobre el rostro (lux), distancia persona–cámara (m), resolución y fps efectivos, y tipo de fondo; registrar los valores efectivos en cada sesión y descartar y repetir toda sesión fuera de rango. |
| CHK041 | ¿"Exterior o en movimiento" es una condición o dos? [Ambiguity, Spec §NFR-004] | CORREGIR | La disyunción permite cumplir el entorno más difícil eligiendo la variante más fácil. Separarlas, o fijar que E3 exige **ambas** (exterior **y** cámara en desplazamiento). |
| CHK042 | ¿Existe criterio de aprobado/desaprobado por entorno? [Acceptance Criteria, Spec §NFR-005] | OK | 0.70 por entorno, con la muestra congelada de NFR-018 y ≥10 intentos por seña (SC-003). Comparable entre corridas. |
| CHK043 | ¿El umbral 0.70 está declarado como provisional con condición de revisión? [Assumption, Spec §NFR-005] | OK | Declarado provisional, con obligación de revisarlo tras la primera medición documentando el cambio. |

### 4e. Contrato de datos de keypoints

| ID | Ítem | Estado | Hallazgo / Reformulación propuesta |
|----|------|--------|-------------------------------------|
| CHK044 | ¿Está definido el fixture de referencia contra el que se valida? [Gap, Spec §NFR-014] | **CORREGIR (bloqueante)** | NFR-014 dice "contra secuencias de referencia" sin definir cuántas, cuáles, quién las genera ni dónde se versionan. Es un test bloqueante de CI (Principio IV, Principio XIII) que hoy nadie podría implementar sin volver a preguntar. Proponer: conjunto versionado en el repositorio, cubriendo las 64 clases al menos una vez, generado por el preprocesamiento de referencia y congelado. |
| CHK045 | ¿Está definida la tolerancia numérica aceptable? [Gap, Spec §NFR-014] | **CORREGIR (bloqueante)** | No hay tolerancia declarada. Sin ella el test es inescribible: la igualdad exacta en punto flotante entre implementaciones distintas no se sostiene. |
| CHK046 | ¿El contrato distingue entre transformación y extremo a extremo? [Gap, Spec §NFR-014] | **CORREGIR (bloqueante)** | Es el punto más delicado del checklist. MediaPipe en Python y en JS **no producen los mismos landmarks a partir del mismo video**: son implementaciones distintas. Un fixture "video → 201 coordenadas" es por lo tanto imposible de cumplir para el cliente web, y el test se volvería inútil o se relajaría hasta no detectar nada. Proponer dos niveles: **Nivel 1 (transformación, bloqueante de CI)**: entrada = landmarks crudos ya dados; salida = vector de 201 coordenadas normalizado (centrado en punto medio de hombros, z sin centrar, largo fijo por linspace); tolerancia estricta, aplica a todos los productores por igual. **Nivel 2 (extremo a extremo, informativo)**: mismo video por ambos caminos; se verifica dimensión, orden y ausencia de desalineación estructural, con una cota por coordenada **medida empíricamente**, no asumida. |
| CHK047 | ¿Está enumerado qué productores deben pasar el test? [Clarity, Spec §NFR-014] | CORREGIR | Dice "cada productor de keypoints"; los *Constitutional Requirements* nombran dos (cliente de la aplicación y preprocesamiento de entrenamiento). Consolidar la lista en el propio NFR-014 para que el test no dependa de leer otra sección. |

### 4f. Vocabulario cerrado

| ID | Ítem | Estado | Hallazgo / Reformulación propuesta |
|----|------|--------|-------------------------------------|
| CHK048 | ¿Está definido el comportamiento ante una seña fuera de las 64? [Coverage, Spec §US1-3] | OK | "No entendí", sin forzar la clase más parecida. Con criterio de aceptación en SC-005. |
| CHK049 | ¿Está definido el comportamiento ante una seña del vocabulario mal ejecutada o incompleta? [Gap] | CORREGIR | FR-010 cubre "material insuficiente" (cantidad), pero no la ejecución **incorrecta** de una seña que sí está en el vocabulario. Es un caso distinto y frecuente en personas que están aprendiendo. Añadir: comportamiento esperado = mismo camino que fuera de vocabulario ("no entendí"), y declararlo explícitamente para que no se resuelva por omisión. |
| CHK050 | ¿Está definido el comportamiento ante una seña mucho más rápida o lenta que en el dataset? [Gap, Edge Case] | CORREGIR | No aparece en ningún requisito ni caso borde. Es un riesgo real y específico: el muestreo a largo fijo por linspace normaliza la duración, de modo que una seña muy lenta o muy rápida cambia el contenido temporal efectivo de la secuencia. Añadir caso borde con comportamiento esperado y, si se decide acotar, un rango de duración admisible. |

### 4g. Accesibilidad

| ID | Ítem | Estado | Hallazgo / Reformulación propuesta |
|----|------|--------|-------------------------------------|
| CHK051 | ¿El protocolo de validación de usabilidad define con quién se valida? [Gap, Spec §SC-006, §NFR-009] | **CORREGIR** | SC-006 dice "personas señantes" sin especificar que deben ser **personas sordas usuarias de LSA**, ni su nivel de fluidez, ni cómo se las convoca. Validar accesibilidad para personas sordas con participantes oyentes que sepan algo de LSA no mide lo que el requisito afirma. Especificar criterio de reclutamiento. |
| CHK052 | ¿Define cuántas personas, qué tareas y qué criterio de éxito? [Measurability, Spec §SC-006] | CORREGIR | Cuántas (≥5) y criterio (≥80%, <2 min) están; **el conjunto de tareas no**. Enumerar las tareas mínimas: iniciar sesión, lograr un reconocimiento, descartar uno incorrecto, encontrar el vocabulario, cambiar el umbral. |
| CHK053 | ¿Se controla el sesgo del evaluador? [Gap] | CORREGIR | Nada impide que quien diseñó la interfaz conduzca la prueba y puntúe el resultado. Añadir: la observación la conduce alguien que no participó del diseño, con guion fijo y sin asistir al participante. |
| CHK054 | ¿NFR-010 (nada depende solo de sonido) es verificable? [Measurability, Spec §NFR-010] | OK | Verificable por revisión exhaustiva del catálogo de estados y alertas. |

### 4h. Privacidad

| ID | Ítem | Estado | Hallazgo / Reformulación propuesta |
|----|------|--------|-------------------------------------|
| CHK055 | ¿"El video no sale del dispositivo" tiene método de comprobación? [Measurability, Spec §NFR-006, §SC-004] | OK | SC-004 define inspección del tráfico saliente, con criterio de cero peticiones. Es comprobable por un tercero. |
| CHK056 | ¿"No se almacena video" tiene método de comprobación? [Gap, Spec §NFR-007] | CORREGIR | NFR-007 es hoy una afirmación de diseño sin verificación. Proponer criterio: *"Tras una sesión de N capturas, la inspección del almacenamiento local del navegador y del sistema de archivos no revela ningún artefacto de video ni de frames."* |
| CHK057 | ¿La ausencia de instrumentación en producción es comprobable? [Measurability, Spec §SC-013] | OK | Inspección del artefacto distribuido. |
| CHK058 | ¿El consentimiento del build de evaluación tiene criterio verificable? [Clarity, Spec §NFR-017(a)] | CORREGIR | "Consentimiento explícito e informado" no dice qué se informa. Enumerar el contenido mínimo: qué se registra, qué no, dónde queda, cómo se revoca. |

---

## 5. Casos borde

| ID | Caso requerido | Estado | Hallazgo / Reformulación propuesta |
|----|----------------|--------|-------------------------------------|
| CHK059 | Dos o más personas en cuadro [Spec §Edge Cases] | CORREGIR | Presente, pero con **dos comportamientos alternativos unidos por "o"** ("indicar que no puede determinar a quién seguir, o seguir de forma estable a una sola persona"). Una disyunción no es testeable: el implementador elige y el test nunca falla. Fijar uno. |
| CHK060 | Una sola mano visible en una seña bimanual [Gap] | CORREGIR | El caso borde existente cubre a quien **seña** con una mano; no cubre una seña bimanual con una mano **ocluida o fuera de cuadro**, que es un caso distinto (la seña es válida pero la observación es parcial). Añadir con comportamiento esperado. |
| CHK061 | Persona zurda o señas espejadas [Spec §Edge Cases] | CORREGIR | Presente como "no debe degradarse", sin métrica ni verificación. Además la spec no declara si el dataset LSA64 contiene personas zurdas, de modo que la afirmación no está respaldada. Proponer criterio medible por comparación de accuracy entre subgrupos, o declarar explícitamente la limitación conocida. |
| CHK062 | Cámara sin permisos, apagada u ocupada [Spec §Edge Cases] | CORREGIR | Cubre permiso denegado y cámara ocupada. **Falta cámara ocluida** (lente tapada): produce imagen válida pero sin persona detectable, y cae en un camino distinto al de "sin permisos". Añadir. |
| CHK063 | Pérdida de conexión con el servidor de inferencia [Spec §Edge Cases] | OK | No aplica: no hay servidor de inferencia. El caso equivalente (caída de red a mitad de sesión) está cubierto. |
| CHK064 | Período en el que la persona no está señando [Spec §Edge Cases] | OK | Cubierto como tiempo muerto inicial, con la exigencia de que no forme parte de lo que se clasifica. |
| CHK065 | Seña mucho más rápida o lenta que en el dataset [Gap] | CORREGIR | Ausente. Ver CHK050. |
| CHK066 | ¿Los casos borde tienen comportamiento esperado, y no solo descripción del caso? [Acceptance Criteria] | CORREGIR | La mayoría sí. Excepciones: CHK059 (disyuntivo), CHK061 (sin métrica). |

---

## 6. Ambigüedades

| ID | Ítem | Estado | Hallazgo |
|----|------|--------|----------|
| CHK067 | ¿Quedan marcadores `[NEEDS CLARIFICATION]` sin resolver? [Ambiguity] | OK | Ninguno. Las 8 preguntas de las dos sesiones previas están resueltas y registradas en §Clarifications. |
| CHK068 | ¿Los riesgos abiertos están escalados como preguntas o solo documentados? [Assumption] | CORREGIR | Los 5 riesgos viven en *Assumptions*. Los supuestos no bloquean nada. Al menos el de parada opcional (CHK031) debe ser requisito numerado; los demás deben tener dueño y momento de resolución declarados. |
| CHK069 | Ambigüedades nuevas detectadas en esta revisión [Ambiguity] | CORREGIR | Cuatro: quién configura las preferencias (CHK008); de quién es el dispositivo y qué pasa al prestarlo (CHK009); qué significa "seña completa" (CHK010); cómo se determina que una voz es rioplatense (CHK022). |

---

## Estado de aplicación — 2026-07-25

Las 6 correcciones bloqueantes fueron aplicadas a `spec.md`, junto con las no bloqueantes que no
requerían una decisión de producto.

### Bloqueantes: cerrados

| Ítem | Cómo se resolvió |
|------|------------------|
| CHK034/035/036 | NFR-003 reescrito con **L1** (proxy automatizable desde el inicio de captura) y **L2** (métrica constitucional desde el último frame anotado a ciegas sobre grabación de un **dispositivo externo**, para no relajar NFR-017(c)). SC-002 actualizado. |
| CHK032/033 | NFR-001 desdoblado en **NFR-001a** (clasificador: LSA64 cut, sujeto 10 held-out, seed y dependencias registradas) y **NFR-001b** (sistema desplegado extremo a extremo). Prohibido reportar uno sin el otro. SC-001 actualizado. |
| CHK040/041 | NFR-004 define **E1/E2/E3** por lux, distancia, resolución, fps y tipo de fondo; E3 exige exterior **y** movimiento. Sesiones fuera de rango se descartan y repiten. SC-003 actualizado. |
| CHK044–047 | NFR-014 desdoblado en **Nivel 1** (transformación, bloqueante de CI, fixture versionado sobre las 64 clases, tolerancia 1e-6, dos productores nombrados) y **Nivel 2** (extremo a extremo, informativo, cota medida empíricamente). Nuevo SC-017. |
| CHK031 | Nuevo **NFR-019**: compensación obligatoria del umbral por número de evaluaciones, con margen de 2 puntos porcentuales. Nuevo SC-016. Salió de *Assumptions*. |
| CHK004 | FR-031 reescrito con verificación propia en el nuevo **SC-014**, y distinción explícita entre arrancar sin red (fuera de alcance) y sobrevivir a su caída (en alcance). |

### No bloqueantes: aplicados

CHK002 (nueva **US8** + **FR-032**, señal audible para el interlocutor, que opera sin ver la
pantalla) · CHK006 (el sistema deja de figurar como actor) · CHK010, CHK011, CHK012, CHK013,
CHK014, CHK015, CHK017, CHK018, CHK019, CHK021, CHK022, CHK023, CHK024 (términos no medibles
cuantificados o eliminados) · CHK025, CHK026 (coherencia entre regla de parada y umbral) · CHK027
(FR-002 / NFR-006 / NFR-007 separados) · CHK028 · CHK038 · CHK049, CHK050, CHK059, CHK060, CHK061,
CHK062, CHK065 (casos borde) · CHK051, CHK052, CHK053 (protocolo de accesibilidad en NFR-009) ·
CHK056 (verificación de NFR-007) · CHK058 (contenido del consentimiento) · CHK068 (riesgos con
dueño y momento).

### Pendientes: requieren una decisión tuya, no una reescritura

| Ítem | Decisión pendiente |
|------|--------------------|
| CHK008 | ¿Quién configura las preferencias, la persona señante o el interlocutor que sostiene el dispositivo? |
| CHK009 | ¿Hace falta un modo restringido para entregar el teléfono desbloqueado a un desconocido? |
| CHK029 | ¿Se conserva "en tiempo real" en el título, siendo que la interacción es por turnos? |
| CHK037 | Valores numéricos de estricto / normal / permisivo — diferido a plan por NFR-019, ya con requisito que lo obliga. |

## Veredicto

> **Actualización 2026-07-25**: el veredicto que sigue corresponde a la revisión original. Tras
> aplicar las correcciones (ver sección anterior), **la spec está lista para `/speckit-plan`**, con
> 4 decisiones de producto pendientes que no bloquean la planificación.

### Veredicto original (antes de las correcciones)

**La spec NO estaba lista para `/speckit-plan`.**

No por falta de cobertura —es amplia y las tres sesiones previas resolvieron bien las decisiones de
producto— sino porque **cuatro de las puertas de calidad que la propia spec define no se pueden
medir tal como están escritas**, y una quinta mide algo distinto de lo que afirma medir. Planificar
sobre eso produce un plan que parece verificable y no lo es.

De 69 ítems: **32 OK · 33 CORREGIR · 4 AMBIGUO**.

### Bloqueantes, en orden de resolución

Los tres primeros son del mismo tipo: una métrica que no se puede tomar, o que se toma sobre el
objeto equivocado.

| # | Ítem | Por qué bloquea |
|---|------|-----------------|
| 1 | **CHK034/CHK035/CHK036 — circularidad de la latencia** | El instante de inicio de la medición lo produce el mismo componente que se evalúa. Un detector de fin más lento **mejora** la latencia medida. Es la puerta del Principio IX y hoy premia el fallo. |
| 2 | **CHK033 — NFR-001 no mide el sistema desplegado** | El 0.85 se mide sobre clips ya recortados, donde no existe la decisión de parada que el sistema real sí toma. Es la puerta del Principio V y caracteriza un sistema que nadie va a usar. |
| 3 | **CHK040/CHK041 — entornos sin parámetros observables** | Sin rangos de lux, distancia y fondo, el 0.70 de NFR-005 no es comparable entre corridas ni reproducible por un tercero. |
| 4 | **CHK044/CHK045/CHK046 — contrato de keypoints sin fixture ni tolerancia** | Es test bloqueante de CI por los Principios IV y XIII, y hoy es inescribible. CHK046 además advierte que la formulación ingenua (video → 201 coords) es **imposible de cumplir** entre MediaPipe Python y JS. |
| 5 | **CHK031 — compensación de la parada opcional no es requisito** | Vive en *Assumptions*. Sin número obligatorio, el Principio VIII queda sin defensa frente a la inflación de confianza que introduce la regla de los 3 intentos. |
| 6 | **CHK004 — FR-031 huérfano** | Único requisito sin historia, escenario ni criterio de éxito, resultado de eliminar US8. O se le da verificación o se elimina. |

### No bloqueantes, recomendados antes de planificar

- **CHK002** — el interlocutor quedó como operador del sistema sin historia propia; es quien encuadra
  sin ver la pantalla, y ese problema de usabilidad no está capturado en ninguna parte.
- **CHK027** — separar FR-002 / NFR-006 / NFR-007, que hoy se pisan.
- **CHK025/CHK026** — coherencia entre la regla de parada y la redacción del umbral.
- **CHK051/CHK052/CHK053** — protocolo de accesibilidad: con quién, qué tareas, quién observa.
- **CHK049/CHK050/CHK060/CHK062** — cuatro casos borde ausentes o incompletos.
- **CHK059** — eliminar la disyunción en el caso de varias personas en cuadro.
- Dimensión 2 completa (CHK010–CHK024): 15 términos no medibles en requisitos normativos.

### Lo que sí está sólido

Privacidad (método de verificación real por inspección de tráfico y de artefacto), separación
producción/evaluación, muestra de campo congelada por sorteo con semilla, trazabilidad de las
decisiones en §Clarifications, y cobertura de historias con criterios Given/When/Then en las siete.

## Notes

- Este checklist evalúa **la redacción de los requisitos**, no la implementación (inexistente) ni el
  plan (no generado aún).
- `spec.md` no fue modificado, según lo pedido.
- Marcar con `[x]` los ítems a medida que se corrijan; los bloqueantes deberían cerrarse antes de
  `/speckit-plan`, y el resto puede resolverse durante la planificación si queda registrado.
