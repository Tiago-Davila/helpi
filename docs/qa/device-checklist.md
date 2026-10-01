# Evidencia de QA en dispositivo

Completar por candidato a main. Evidencia complementaria, no aprobación bloqueante.
No subir video/audio de usuarios ni conversaciones personales.

- SHA / enlace al run / APK y SHA-256:
- Persona que prueba / fecha:
- Modelo de teléfono / ABI / versión Android:
- Resultado: aprobado / fallido / pendiente:
- Incidencias reproducibles y evidencia sin datos personales:

| Caso | Resultado y observaciones |
|---|---|
| Instalación limpia e inicio sin red | |
| Cámara, micrófono y voz española offline | |
| Denegar/revocar permisos: teclado sigue disponible | |
| Sin voz offline: texto disponible y aviso visible | |
| Iniciar, pausar, volver del fondo, reanudar explícitamente | |
| Pausa > 2 minutos descarta conversación | |
| Cierre libera sensores y elimina historial | |
| Alternancia voz/escucha sin realimentación | |
| Baja confianza: no publica ni habla la predicción | |
| Señas desconocidas: registrar falsos positivos, sin asumir rechazo por umbral | |
| Iluminación variable, encuadre, usuario zurdo y diestro | |
| TalkBack, texto aumentado, orientación y botones accesibles | |
| Aviso de asistencia y limitaciones, atribución LSA64 | |
| Sesión de 15 minutos: memoria, temperatura, batería y latencia | |

Registrar valores observados de rendimiento; no afirmar umbrales de rendimiento
aprobados sin una referencia medida por dispositivo. Un fallo debe incluir pasos,
resultado esperado/real y SHA; corregir en rama de trabajo y repetir el caso.
