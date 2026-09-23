# Helpi para Android

Aplicación de conversación accesible que funciona localmente en el dispositivo:

- reconoce 64 señas aisladas de Lengua de Señas Argentina con CameraX,
  MediaPipe Holistic y el modelo LiteRT incluido en `app/src/main/assets/lsa`;
- muestra la confianza de cada reconocimiento y permite configurar un umbral
  entre 50 % y 99 % (90 % por defecto), sin publicar la clase ganadora cuando
  queda por debajo del umbral;
- convierte texto escrito en voz con el motor TTS de Android;
- transcribe español desde el micrófono con Vosk y un modelo incluido en la app;
- mantiene la conversación sólo en memoria y no necesita una conexión de red.

## Ejecución

La primera pantalla muestra Helpi y **Iniciar conversación**. Al iniciar,
la cámara y los mensajes comparten una sola pantalla. Los botones junto a
la cámara permiten silenciar el micrófono, apagar la cámara o abrir el
teclado sin perder el historial. El engranaje abre el umbral de confianza
y las instrucciones. Una pulsación larga sobre un mensaje propio permite
repetirlo en voz alta o corregirlo; la cruz finaliza y borra la conversación
después de confirmar.

Abrí el proyecto con Android Studio, concedé los permisos de cámara y
micrófono y ejecutá la configuración `app` en Android 8.0 (API 26) o posterior.
La primera preparación copia el modelo de Vosk al almacenamiento privado y
puede tardar unos segundos.

Desde una terminal con `JAVA_HOME` y el Android SDK configurados:

```powershell
.\gradlew.bat :domain:test testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
adb shell am instrument -w -r `
  -e class com.helpi.conversation.lsa.ModelReferenceTest `
  com.helpi.conversation.test/androidx.test.runner.AndroidJUnitRunner
```

Para ejecutar también las pruebas del flujo de interfaz, omití el argumento
`-e class ...` del comando de instrumentación. El modelo se comprueba con
secuencias de referencia; las pruebas de Compose verifican navegación,
teclado, configuración, controles y cierre sin depender de una persona
delante de la cámara.

El APK de desarrollo queda en
`app/build/outputs/apk/debug/app-debug.apk`.

## Arquitectura de reconocimiento

```text
CameraX → MediaPipe HolisticLandmarker → 168 coordenadas por cuadro
        → ventana de 40 cuadros → modelo modelo_lsa.tflite
        → clase + confianza → umbral → catálogo → texto/TTS
```

El contrato exacto de coordenadas y muestreo está centralizado en
`KeypointContract.kt`. El modelo y `catalogo_senas.json` forman una unidad y
deben actualizarse juntos. Consultá `CLAUDE.md` para las restricciones del
pipeline y las limitaciones conocidas.

## Alcance y licencias

Helpi es una ayuda experimental; no reemplaza a una persona intérprete y no
debe utilizarse para emergencias. El modelo LSA se deriva de LSA64 (LIDI,
Universidad Nacional de La Plata) bajo CC BY-NC-SA 4.0 y sólo admite uso no
comercial. Las dependencias y modelos de terceros se detallan en
`THIRD_PARTY_NOTICES.md`.
