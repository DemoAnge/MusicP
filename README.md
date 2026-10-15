# Music

Reproductor de música **manos libres** para Android (Kotlin + Jetpack Compose). Controla la reproducción del teléfono con mandos grandes, notificación, widget, voz y modo conducción.

## Funciones

- **Biblioteca local:** canciones del teléfono (MediaStore + ExoPlayer). Inicio con recientes, queridas y álbumes; carpetas, artistas y álbumes; listas de usuario (solo IDs, sin copiar archivos: reordenar, renombrar, reproducir siguiente o añadir a cola en bloque). Carátulas embebidas en listas. En Ajustes: carpetas ocultas por nombre, y mostrar u ocultar podcasts/audiolibros (mime, carpeta o duración).
- **Transporte:** anterior, play/pausa, siguiente y barra de progreso. Mini reproductor, pantalla Now Playing, widget, notificación y pantalla de bloqueo.
- **YouTube en Brave:** si [Brave](https://play.google.com/store/apps/details?id=com.brave.browser) está instalado, la app abre un puente local (`127.0.0.1`) y reproduce YouTube con la IFrame Player API oficial. La app es el mando (play, pausa, skip). El poll se reconecta solo; si la pestaña murió, «Toca para reabrir puente». Mini player, widget y notificación muestran «Suena en Brave». Cola mixta: al pasar a web el local pausa; skip cruza fuentes. Checklist en Ajustes (pestaña abierta, Shields, activar sonido). Sin Brave: Play Store o el navegador predeterminado.
- **Búsqueda:** una caja agrupa canciones, artistas y álbumes locales, más YouTube en Brave. Historial de búsquedas recientes. Con `YOUTUBE_API_KEY` en `local.properties` (Data API v3, tuya) lista hasta 5 videos; si no hay clave, se abre Brave para pegar el enlace en el puente.
- **Letras sincronizadas:** tags ID3, archivos `.lrc` y lrclib.net.
- **Conducción:** tres botones grandes, letras opcionales, skip deslizando y pantalla encendida.
- **Ajustes:** temporizador de sueño; velocidad 0.8–1.5× por tipo (música vs podcast/audiolibro); normalización ReplayGain; fundido corto opcional (en lugar de gapless); ecualizador de 3 presets (off / graves / voz / brillo).
- **Voz (opcional):** micrófono arriba, palabra de activación «música», luego «pon [canción/artista/álbum]», «quiero esta», «temporizador 15», «más despacio» / «a 1.2», play, pausa, siguiente, anterior, cola, mezclar, repetir o salir. Confirmación hablada corta. Se apaga a los 15 s de inactividad.
- **Widget:** barra o 2×2, con rewind de 10 s.
- **Android Auto / AVRCP:** sesión Media3.

## Ejecutar

Módulo **`:app`**. En Android Studio: configuración de ejecución **app**. Las carpetas de la plantilla KMP (`androidApp`, `iosApp`, `shared`) no forman parte del producto.

```
./gradlew :app:assembleDebug
./gradlew :app:test
```
