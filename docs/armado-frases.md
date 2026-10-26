# Armado de frases

Convierte una oración de LSA, ya cerrada, en una frase en español usando
plantillas determinísticas. No hay modelo de lenguaje ni modelo de traducción.
Eva reconoce señas; este componente solo agrega gramática.

Código: `domain/src/main/java/com/helpi/conversation/phrase/`.
Datos: `app/src/main/assets/lsa/lexico_senas.json` y `plantillas_frases.json`.

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

- **Verbos reconocidos en infinitivo, siempre.** En LSA la persona y el tiempo se marcan
  con dirección y espacio, y el modelo no los captura. No se infieren.
  Ejemplo: «La mujer: comprar la leche.»
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
  `lugar`, `expresion`, `conector`, `tiempo`, `letra`) o de glosas literales
  (`glosa:<Glosa>`).
- `{i}`: forma de la seña `i`.
- `{i.art}`: artículo definido que concuerda con `i`. Usa el artículo
  especial del léxico cuando existe, como en «el agua». Queda vacío si la
  palabra no tiene género, como «Argentina».
- `{i.concuerda:j}`: forma de `i` en el género y número de `j`.
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
