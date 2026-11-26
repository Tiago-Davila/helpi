# Armado de frases

Convierte una oración de LSA o LSE, ya cerrada, en una frase en español usando
plantillas determinísticas. No hay modelo de lenguaje ni modelo de traducción.
Eva reconoce señas; este componente solo agrega gramática.

Código: `domain/src/main/java/com/helpi/conversation/phrase/`.
Datos: `app/src/main/assets/<lengua>/lexico_senas.json` y
`plantillas_frases.json`. La carpeta sale del `language` del manifiesto ya
validado: el léxico siempre es de la misma lengua que el modelo cargado. Una
lengua sin léxico o plantillas muestra glosas sueltas; las demás no se afectan.

## Entrada y salida

- **Entrada:** la lista ordenada de reconocimientos de la oración (índice,
  glosa y confianza). Detectar dónde termina la oración no es parte de este
  componente.
- **Salida (`PhraseResult`):** la frase, las glosas originales y las plantillas
  que se aplicaron.

## Reglas de contenido

- Las plantillas solo agregan **palabras de función**: artículos,
  preposiciones, conjunciones, las cópulas «es»/«son»/«está» y puntuación. La lista cerrada está en
  `PhraseTemplate.FUNCTION_WORDS`. Una plantilla que agrega una palabra de
  contenido se rechaza al cargar el archivo, y con ella todo el conjunto.
- «HAMBRE» → «Hambre.» es válido; «HAMBRE» → «Quiero comer.» no lo es.

## Convenciones

- **Verbos reconocidos en infinitivo.** En LSA la persona y el tiempo se marcan
  con dirección y espacio, y el modelo no los captura. No se infieren.
  Ejemplo: «La mujer: comprar la leche.» La única excepción es la primera
  persona de LSE (ver «LSE: primera persona por defecto»).
- **Dos puntos entre la persona y el verbo.** Con el verbo en infinitivo, «La
  mujer comprar la leche» se lee como un error. Los dos puntos expresan tema y
  comentario. Como son dato, se cambian en el JSON.
- **Glosa en MAYÚSCULAS = parte no construida.** Es la convención de glosado de
  las lenguas de señas. Una seña que ninguna plantilla cubrió se muestra así.
- **`¿?` = seña bajo el umbral.** Nunca se muestra la adivinanza.

## Conectores (funcionalidad 002)

- Los objetos y personas sueltos llevan artículo: «El fideo.», «La mujer.».
- Las cualidades sueltas llevan «Es»: «Es opaco.». «Hambriento» usa «Está».
- Objeto + cualidad agrega la cópula y conserva la concordancia: «La leche es
  amarga.». Para «Colores», el único objeto plural del léxico actual, se usa
  «son»: «Los colores son opacos.».
- Ayuda + verbo agrega «para»: «Ayuda para encontrar.». Si sigue un objeto,
  se une en la misma frase: «Ayuda para encontrar el fideo.».
- Se conservan las glosas del catálogo («Fideo», «Colores»), sin sustituirlas
  por «spaghetti» o «color». Los conectores llegan tanto al texto como a la voz;
  las glosas originales y la protección por baja confianza se conservan.

## Tiempo y deletreo (modelo eva-lsa94-v4)

- **Tiempo** (`Hoy`, `Ayer`, `Mañana`) encabeza la oración y se separa con coma:
  «Ayer, la mujer: comprar la leche.». Con un verbo sin persona usa dos puntos:
  «Hoy: comprar la leche.». Solo: «Hoy.».
- **Letras** (A … Z, con CH): las letras seguidas se unen en una palabra
  deletreada con mayúscula inicial, porque el deletreo se usa sobre todo para
  nombres propios: `A N A` produce «Ana.». Una letra sola queda «B.». Debajo de
  la frase se siguen mostrando las letras sueltas (`A N A`). Si una letra queda
  bajo el umbral no se deletrea nada: `A ¿? A`.

## Umbral reducido para letras y tiempo

El modelo tiene pocos ejemplos de las letras y de `Hoy`, `Ayer` y `Mañana`, y
les da poca confianza aunque las reconozca. Por eso esas categorías (`letra` y
`tiempo`) se aceptan con un umbral de **20 %** (`ReducedThreshold`), o con el
general si es menor. El resto de las señas usa el umbral general, que por
defecto es 50 %.

- Se aplica en la compuerta de aceptación (`SignAcceptancePolicy`, umbral por
  clase) y en el armado (`PhraseBuilder`), con la categoría que da el léxico.
- Sin léxico validado, todas las clases usan el umbral general.
- Sigue valiendo el piso de probabilidad cruda: umbral menos 0,15.
- Es un parche mientras se consiguen más datos de esas señas en `helpi-ml`.

## LSE: primera persona por defecto (funcionalidad 007)

El vocabulario de LSE (SWL-LSE, 299 clases) es médico: quien seña casi siempre
habla de sí. Por eso, en LSE, una oración sin persona se arma en primera persona.

- La conjugación **no la agregan las plantillas**, que siguen limitadas a
  palabras de función. La aporta el léxico con el campo opcional `formaYo`
  («Dolor» → «tengo dolor»; «Fiebre» → «tengo fiebre»; «Toser» → «toso»).
- El marcador `{i.yo}` usa `formaYo` si existe y la forma base si no:
  «ESTOMAGO DOLOR» → «Tengo dolor en el estómago.»; «ICTUS» → «Ictus.».
- Se activa con los datos: las plantillas de LSE usan `{i.yo}` y las de LSA no.
  No hay un interruptor en el código y LSA no cambia.
- Con «usted» o «él o ella» no se conjuga: «Usted: ayudar.».
- Los adjetivos con género para quien seña no tienen `formaYo` o usan una
  forma neutra («Nervioso» → «tengo nervios»), porque el género de quien seña
  no es observable.
- Las variantes de una glosa comparten forma («Diarrea (variante 2)» →
  «diarrea») y las clases compuestas se reescriben para leerse en español, sin
  conjugar («Comer sin» → «sin comer»).
- Categorías propias: `cuerpo` (partes del cuerpo) y `sintoma` (malestares y
  estados).
- El léxico no tiene validación lingüística: la interfaz muestra
  «LSE · frases experimentales».

## Señas señaladas: pronombres y zonas (funcionalidad 007)

Los pronombres no son clases del modelo: en LSA y LSE se hacen señalando con el
índice. `PointingDetector` (`vision/`) los detecta por geometría sobre los mismos
cuadros del segmento, después de la inferencia y sin tocar el vector de 168.

| Gesto | Seña sintética | Índice | Categoría |
|---|---|---|---|
| Índice al pecho | `YO` | -1 | `persona` |
| Índice hacia la cámara | `USTED` | -2 | `persona` |
| Índice a un costado | `EL_ELLA` | -3 | `persona` |
| Índice tocando la cabeza | `CABEZA` | -10 | `cuerpo` |
| Índice tocando la garganta | `GARGANTA` | -11 | `cuerpo` |
| Índice tocando el abdomen | `ABDOMEN` | -12 | `cuerpo` |

- Las define `SyntheticSigns`, comunes a todas las lenguas. `Lexicon.entry`
  resuelve los índices negativos y las plantillas pueden nombrarlas como glosa
  literal (`glosa:YO`).
- Señala la mano que tenga el índice extendido, izquierda o derecha; con las
  dos, no se clasifica. El índice debe sostenerse quieto (`PointingConfig`).
- **Orden de decisión** por segmento (`SentenceRules.segmentSign`): si el
  modelo acepta la seña, gana el modelo; si no, entra la seña señalada; si
  tampoco, queda el hueco `¿?`. Un señalamiento ambiguo es `¿?`.
- Una zona sin su referencia (cara para cabeza y garganta, caderas para
  abdomen) no se evalúa.
- Los umbrales son valores iniciales: falta calibrarlos con segmentos reales.

## Confirmación (funcionalidad 007)

Una oración que contiene interpretación no se publica sola: queda como frase
sugerida en el diálogo «¿Usar esta frase?», con el texto editable y las señas
reconocidas debajo. Confirmar la publica y la ofrece a voz; descartar no
publica ni vocaliza.

- Se confirma **siempre en LSE** y, en cualquier lengua, **si la oración tiene
  una seña señalada**. Una oración de LSA sin señalamiento se publica como antes.
- Las señas señaladas aparecen entre las glosas (`YO CORRER`); la primera
  persona por defecto no, porque nadie la señó.

## Algoritmo

1. Si alguna seña quedó por debajo del umbral, no se aplica ninguna plantilla:
   `MUJER ¿? LECHE`.
2. Si el armado está deshabilitado por incompatibilidad de versiones, o si un
   índice no corresponde a su glosa en el léxico, se devuelven las glosas
   sueltas.
3. Si no, las letras seguidas se unen en una sola seña deletreada y se recorre
   la oración de izquierda a derecha. En cada posición se
   elige la plantilla **más larga**. Si hay empate, la que tiene **más glosas
   literales** (`glosa:Donde`). Si sigue el empate, **la primera del archivo**.
4. Una posición sin plantilla deja la seña suelta y el recorrido avanza una
   posición. Nunca se fuerza una plantilla.
5. Los tramos se unen con un espacio: `MUJER SORDO CORRER` produce «La mujer
   sorda. CORRER».

## Formato de las plantillas

```json
{ "id": "objeto-cualidad", "patron": ["objeto", "cualidad"], "salida": "{0.art} {0} es {1.concuerda:0}." }
```

- `patron`: lista de categorías (`persona`, `verbo`, `objeto`, `cualidad`,
  `lugar`, `expresion`, `conector`, `tiempo`, `letra`, `cuerpo`, `sintoma`) o
  de glosas literales (`glosa:<Glosa>`), incluidas las sintéticas (`glosa:YO`).
- `{i}`: forma de la seña `i`.
- `{i.art}`: artículo definido que concuerda con `i`. Usa el artículo
  especial del léxico cuando existe, como en «el agua». Queda vacío si la
  palabra no tiene género, como «Argentina».
- `{i.concuerda:j}`: forma de `i` en el género y número de `j`.
- `{i.yo}`: forma en primera persona de `i` (`formaYo`), o su forma base.
- El renderizador colapsa los espacios repetidos y pone la mayúscula inicial.

## Versiones

Modelo, catálogo, léxico y plantillas forman una unidad. `PhraseCompatibility`
verifica tres cosas:

- que el léxico declare el `modelVersion` y el `catalogVersion` cargados;
- que las plantillas declaren el `lexicoVersion` del léxico;
- que la glosa de cada índice del léxico coincida con la del catálogo, y que
  las glosas literales de las plantillas existan.

Si algo falla, el armado de frases se deshabilita y se muestran las glosas
sueltas. Un desfasaje de índices no tira ningún error: traduce mal.

Al actualizar el modelo o el catálogo desde helpi-ml hay que revisar el léxico
y subir su versión. Si cambia el léxico, también se actualiza `lexicoVersion`
en las plantillas.
