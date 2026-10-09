# Music

Reproductor **manos libres** para Android (Kotlin + Jetpack Compose).

- **Local:** música del teléfono (MediaStore + ExoPlayer).
- **Web:** si [Brave](https://play.google.com/store/apps/details?id=com.brave.browser) está instalado, la app abre un puente local (`127.0.0.1`) y reproduce YouTube con la IFrame Player API oficial. La app es el mando (play, pausa, rewind, skip). Sin Brave, hay CTA a Play Store y se usa el navegador predeterminado.
- Búsqueda unificada: una caja agrupa canciones, artistas y álbumes locales, más YouTube en Brave. Historial de búsquedas recientes. Si pones `YOUTUBE_API_KEY` en `local.properties` (Data API v3, tuya), lista hasta 5 videos; si no hay clave, se abre Brave y pegas el enlace en el puente.
- Letras sincronizadas (tags, `.lrc`, lrclib.net).

No es un clon de Spotify. No extrae audio del navegador ni usa APIs no oficiales. Deja la pestaña del puente abierta. En Brave Shields, permite YouTube en esa página si el embed no carga.

El módulo de la app es **`:app`**. Las carpetas `androidApp/`, `iosApp/` y `shared/` son restos de una plantilla KMP y no se compilian.

### Ejecutar

En Android Studio: configuración de ejecución **app**.

```
./gradlew :app:assembleDebug
```
