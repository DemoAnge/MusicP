# Music

Reproductor de música **manos libres** para Android (Kotlin + Jetpack Compose). Controla la reproducción del teléfono con mandos grandes, notificación, widget, voz y modo conducción.

## Funciones

- **Biblioteca local:** canciones del teléfono (MediaStore + ExoPlayer). Inicio con recientes, queridas y álbumes; carpetas, artistas y álbumes; listas de usuario (solo IDs, sin copiar archivos); menú por canción.
- **Transporte:** anterior, play/pausa, siguiente y barra de progreso. Mini reproductor, pantalla Now Playing, widget, notificación y pantalla de bloqueo.
- **YouTube en Brave:** si [Brave](https://play.google.com/store/apps/details?id=com.brave.browser) está instalado, la app abre un puente local (`127.0.0.1`) y reproduce YouTube con la IFrame Player API. La app es el mando (play, pausa, skip). Sin Brave, hay enlace a Play Store y se usa el navegador predeterminado. Deja la pestaña del puente abierta; en Shields, permite YouTube en esa página si el embed no carga.
- **Búsqueda:** una caja agrupa canciones, artistas y álbumes locales, más YouTube en Brave. Historial de búsquedas recientes. Con `YOUTUBE_API_KEY` en `local.properties` (Data API v3, tuya) lista hasta 5 videos; si no hay clave, se abre Brave para pegar el enlace en el puente.
- **Letras sincronizadas:** tags ID3, archivos `.lrc` y lrclib.net.
- **Conducción:** tres botones grandes y pantalla encendida.
- **Ajustes:** temporizador de sueño, velocidad 0.8–1.5× en audio local.
- **Voz (opcional):** micrófono arriba, palabra de activación «música», luego play, pausa, siguiente, anterior, cola, mezclar, repetir 1, repetir todos o salir. Se apaga a los 15 s de inactividad.
- **Android Auto / AVRCP:** sesión Media3.

## Ejecutar

Módulo **`:app`**. En Android Studio: configuración de ejecución **app**.

```
./gradlew :app:assembleDebug
```
