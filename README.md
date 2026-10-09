# Music

Reproductor **manos libres** para Android (Kotlin + Jetpack Compose).

- **Local:** música del teléfono (MediaStore + ExoPlayer).
- **Web:** si [Brave](https://play.google.com/store/apps/details?id=com.brave.browser) está instalado, la app puede buscar y reproducir en YouTube dentro de Brave. La app es el mando (play, pausa, rewind, skip). El puente Brave/YouTube entra en la Fase 3 (`docs/PLAN-MEJORAS.md`).
- Letras sincronizadas (tags, `.lrc`, lrclib.net).

No es un clon de Spotify. No extrae audio del navegador ni usa APIs no oficiales.

El módulo de la app es **`:app`**. Las carpetas `androidApp/`, `iosApp/` y `shared/` son restos de una plantilla KMP y no se compilian.

### Ejecutar

En Android Studio: configuración de ejecución **app**.

```
./gradlew :app:assembleDebug
```
