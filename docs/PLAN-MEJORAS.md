# Plan de mejoras — Music (manos libres + Brave/YouTube)

Documento vivo. Producto: reproductor **manos libres**, no clon de Spotify.
Audio local en el teléfono. Audio web en **Brave + YouTube** si Brave está instalado.
La app nativa es el mando: play, pausa, rewind, skip, cambiar de tema.

---

## 1. Visión de producto

La app es un volante. El usuario, con una mano o desde el bloqueo / el widget / el Bluetooth, puede:

- reproducir y pausar
- retroceder (rewind 10 s, o al inicio de la pista)
- saltar a la siguiente
- cambiar de canción (cola, búsqueda, voz más adelante)

**Fuente A — Local (principal):** archivos del teléfono vía MediaStore + ExoPlayer.

**Fuente B — Web (si la canción no está en el disco):** el teléfono detecta Brave (`com.brave.browser`, beta, nightly). Si existe, abre Brave en una página puente local que reproduce YouTube con la **IFrame Player API oficial**. La app manda buscar / play / pause / seek / next por un túnel WebSocket a `127.0.0.1`.

Si Brave no está: se ofrece instalarlo y, como respaldo, el navegador predeterminado.

Esto no es Spotify. No hay login Premium, radio editorial ni home de descubrimientos. El skip/rewind libre es del mando nativo y del player embebido que el usuario abrió en su navegador.

### Límites (no se implementan)

- Scraping de YouTube, APIs no oficiales, descargar o extraer el audio de la pestaña.
- Bypass de anuncios, DRM o restricciones de YouTube/Spotify.
- Inyectar JavaScript en `youtube.com` ni controlar la app nativa de YouTube por accesibilidad.
- Clonar pixel-perfect la UI de Spotify.

---

## 2. Estado actual (análisis)

### Lo que ya cubre

| Área | Estado |
|---|---|
| Biblioteca local (canciones, carpetas, artistas, álbumes, queridas, recientes) | Hecho |
| Ordenar, borrar, favoritos | Hecho |
| Búsqueda local plana | Hecho |
| Now Playing con paleta, blur, letras (ID3 / `.lrc` / lrclib) | Hecho |
| Mini player, FGS, lock screen, widget | Hecho |
| Shuffle / repeat / skip previous-as-rewind si >3 s | Hecho (rewind no visible como botón) |
| Cola en dominio (`queue`, `removeFromQueue`) | Hecho, **sin UI** |
| Abrir audio desde otras apps | Hecho |
| ContentObserver de MediaStore | Hecho |
| Web / Spotify | `PlaybackSource.WEB`; AAR de Spotify retirado |

### Huecos de producto y UI (del análisis)

1. Now Playing mezcla carátula, letras y controles; la bottom bar rompe el mando inmersivo.
2. El tab “Ahora” usa icono de cola; la cola no se ve ni se reordena.
3. Mini player: progreso no seekable.
4. `errorMessage` del player no se muestra.
5. Sesión no persiste (canción, posición, shuffle, cola) al matar el proceso.
6. ExoPlayer carga **un** MediaItem: hay corte entre pistas (sin gapless).
7. Búsqueda vacía, sin agrupar, sin “buscar en YouTube”.
8. No hay pantallas de álbum/artista, playlists, menú contextual, ajustes.
9. Identidad Spotify (`#1DB954`, package `com.example` vs `com.dmusic`).
10. Back del sistema manda la app al fondo y pelea con carpetas.
11. Cero tests; servicio `exported=true`; leftover KMP (`androidApp`, `iosApp`, `shared`).
12. Letras sin tap-to-seek, sin cache, sin estado de carga/error.

---

## 3. Decisiones de arquitectura

| Decisión | Elección | Por qué |
|---|---|---|
| Identidad | Manos libres, marca propia (acento verde oscuro reutilizable, no “clon Spotify”) | El usuario lo fijó |
| Motor local | ExoPlayer / Media3, cola nativa en el player | Gapless y Android Auto futuros |
| Motor web | Brave si está instalado + YouTube IFrame API en página puente | Navegador que el usuario pidió; API oficial |
| Control web | HTTP + WebSocket en `127.0.0.1` | La app manda rewind/skip sin scrapear |
| Apertura de Brave | Custom Tabs con `package = com.brave.browser`; si falla, `ACTION_VIEW` explícito a Brave | Vuelves a la app; Brave renderiza |
| Búsqueda YouTube | YouTube Data API v3 si hay API key; si no, resultados en Brave para que el usuario elija | Oficial; sin scraping |
| Autoplay móvil | Un toque de “activar” en el puente por sesión | Android bloquea autoplay con sonido |
| Contrato | `IPlayerService` + `PlayerCoordinator` que enruta `LOCAL` / `WEB` | La UI no habla con ExoPlayer ni con el socket |
| Spotify | Fuera del plan | Sustituido por Brave/YouTube |
| Mezcla de cola | Una cola puede tener pistas LOCAL y WEB | El coordinador cambia de motor; el mando no cambia |

### Paquetes Brave a detectar (en este orden)

1. `com.brave.browser`
2. `com.brave.browser_beta`
3. `com.brave.browser_nightly`

### Contrato del túnel (página puente)

La app envía JSON:

- `search` `{ q, artist?, title? }`
- `play` / `pause` / `toggle`
- `seek` `{ positionMs }`
- `rewind` `{ deltaMs: -10000 }`
- `next` / `previous`
- `load` `{ videoId }`

El puente responde:

- `hello` `{ brave: true, ready: bool }`
- `state` `{ playing, positionMs, durationMs, title, artist, videoId, artworkUrl }`
- `needsGesture` (autoplay bloqueado: hay que tocar Play en Brave una vez)
- `error` `{ message }`

YouTube IFrame **no** se scrapea. Search usa Data API (`search.list`, `type=video`, categoría Música) o el usuario elige en la pestaña.

### Foco de audio

Si suena WEB, se pausa ExoPlayer. Si suena LOCAL, el puente recibe `pause`. Un solo `PlayerState` para mini player, notificación, widget y Now Playing. En WEB, el indicador de fuente dice “Suena en Brave”.

---

## 4. IA objetivo (manos libres)

```
[ Biblioteca ]  [ Buscar ]  [ Ajustes ]
        \          /
         Mini player (seek + rewind + play + next)
                |
         Now Playing (pantalla completa, sin tabs)
                |-- Letras (overlay / pantalla)
                |-- Cola (sheet)
```

- Now Playing: carátula grande, título, artista, seek, fila de mandos grandes: rewind 10 s · anterior · play 72 dp · siguiente · +10 s. Shuffle/repeat más pequeños. Chip de fuente: Teléfono | Brave.
- Letras: botón; tap en línea = seek (local). En WEB, letras si título/artista coinciden con lrclib.
- Cola: sheet desde icono de lista; reordenar, quitar, “reproducir después”.
- Biblioteca: chips se quedan en Fase 2; en Fase 5 pasan a home con bloques Recientes / Queridas / Álbumes.
- Búsqueda: local primero; bloque “En YouTube (Brave)” debajo.

---

## 5. Fases

Cada fase es entregable, testeable y no depende de las posteriores. Criterio de hecho = lista de aceptación.

### Fase 0 — Cimientos de producto ✅
**Objetivo:** alinear código y docs con el producto real. Cero cambio de UX visible grande.

- Actualizar skill `.grok/skills/reproductor-musica-kotlin/SKILL.md` (fuera Spotify; Brave/YouTube; manos libres).
- `PlaybackSource.SPOTIFY` → `WEB`.
- README: qué es la app, Brave, límites.
- Quitar o ignorar módulos KMP muertos en la narrativa (`androidApp`, `iosApp`, `shared` no se tocan en runtime actual; documentar o excluir del settings si siguen fuera).
- `MusicPlaybackService` `exported=false` si el intent-filter de Media3 lo permite.
- Renombrar color `SpotifyGreen` → `Accent` (mismo hex de momento, para no romper UI).

**Hecho.** Skill, README, `PlaybackSource.WEB`, `Accent`, servicio no exportado, catálogo y AAR de Spotify fuera. Carpetas KMP documentadas, no compiladas (`settings.gradle.kts` solo incluye `:app`).

**Aceptación:** el repo describe el producto que vamos a construir; el enum ya no promete Spotify.

### Fase 1 — UI del mando manos libres ✅
**Objetivo:** Now Playing y mini player usables con una mano. Sigue siendo 100 % local.

- Now Playing a pantalla completa: ocultar `NavigationBar`. Atrás cierra a Biblioteca (arreglar el `OnBackPressedCallback` que siempre hace `moveTaskToBack`).
- Botón **Rewind 10 s** visible; skip previous mantiene “al inicio si >3 s”.
- Mini player: seek táctil; rewind; no traga el tap de abrir Now Playing.
- Mostrar `errorMessage` (snackbar / banner “No se pudo reproducir X”).
- Chip/estado vacío más claro: “Elige una canción”.
- Favorito (corazón) en Now Playing.
- Letras: botón para mostrar/ocultar; no comprimen la carátula por defecto.
- Tipografía y targets táctiles ≥ 48 dp en mandos principales.

**Hecho.** Now Playing inmersivo (sin tabs); atrás / chevron cierra al origen; rewind y +10 s; mini player con seek y Replay10 (el tap de abrir no se pelea con el seek); snackbar de error; corazón; letras plegadas por defecto.

**Aceptación:** se conduce la reproducción local desde Now Playing, mini player, notificación y widget sin pelear con el Back ni con las letras.

### Fase 2 — Cola, sesión y gapless local
**Objetivo:** el reproductor se comporta como un player de verdad.

- Sheet de cola: ver, reordenar, quitar, “reproducir a continuación”.
- Persistir: pista, posición, shuffle, repeat, cola (DataStore). Restaurar al abrir.
- Pasar la cola a Media3 (`setMediaItems`) para transiciones gapless.
- `PlayerCoordinator` deja de recargar un solo `MediaItem` por skip.
- Play desde búsqueda: cola = resultados (o biblioteca filtrada), no toda la librería si no hace falta.

**Aceptación:** matas la app y reanudas; skip entre pistas locales sin silencio largo; la cola se edita en UI.

### Fase 3 — Brave + YouTube (túnel MVP)
**Objetivo:** si Brave existe, buscar y reproducir en YouTube bajo el mando nativo.

3.1 Detección: `isBraveInstalled()`. Ajustes: “Reproducir web en Brave”. Si no está, CTA a Play Store (`market://details?id=com.brave.browser`) y fallback al navegador por defecto.

3.2 Servidor local (Foreground no hace falta si Custom Tab está viva; el playback service ya existe): HTTP estático del puente + WebSocket.

3.3 Página puente (HTML/JS en `assets/web_bridge/`): YouTube IFrame API, conecta al WS, un botón grande “Activar sonido” para el gesto de autoplay.

3.4 `BrowserTunnelPlayer : IPlayerService` (o motor interno del coordinator). `Track.source = WEB`, `mediaUri` = `yt:VIDEO_ID` o `searchQuery`.

3.5 Apertura: Custom Tabs Brave → `http://127.0.0.1:<port>/`. Si Custom Tabs rechaza localhost, servir el puente desde `http://localhost` o un hostname interno documentado; nunca exponer el WS a la LAN sin auth de token de sesión.

3.6 Búsqueda: si `YOUTUBE_API_KEY` en `local.properties` → Data API, primer resultado de música, el mando reproduce. Si no hay key → el puente muestra el campo y/o abre resultados de YouTube **dentro del IFrame/search oficial**; el usuario toca una vez.

3.7 Now Playing: chip “Suena en Brave”; si el puente se desconecta, banner “Vuelve a abrir Brave”. Rewind/seek/skip via WS.

3.8 Audio focus: pause local ↔ pause puente.

**Aceptación:** con Brave instalado, una búsqueda web abre Brave, un toque activa audio, y desde la app puedes pausar, rewind 10 s y skip. Sin extraer audio. Sin YouTube Data API no oficial.

**Riesgo:** Brave Shields puede bloquear youtube.com en localhost. Mitigación: texto en el puente “permite YouTube en esta página” y probar `youtube-nocookie.com` embed.

### Fase 4 — Búsqueda unificada
**Objetivo:** una caja, dos mundos.

- Resultados locales agrupados (canciones / artistas / álbumes).
- Bloque “YouTube · Brave” con 5 candidatos (Data API) o CTA “Buscar en Brave”.
- Historial de búsquedas recientes.
- Play local vs play web elige motor; se puede “reproducir después” en la misma cola.

**Aceptación:** “Bohemian Rhapsody” encuentra el archivo si existe; si no, lanza YouTube en Brave sin salir del modelo mental de la app.

### Fase 5 — Biblioteca y organización
**Objetivo:** dejar de ser solo una lista con chips.

- Home con filas Recientes, Queridas, Álbumes (grid).
- Pantalla de álbum/artista (header + play + aleatorio).
- Playlists de usuario (Room o DataStore + JSON; Room si hay relaciones).
- Menú contextual: agregar a cola, a playlist, ir al álbum, info (ruta, tamaño, formato), buscar en YouTube.
- Pull-to-refresh además del ContentObserver.
- Ajustes: Brave, ignore folders, tema, “reabrir puente”, notificaciones.

**Aceptación:** un álbum se abre como disco, no como otra lista idéntica.

### Fase 6 — Manos libres avanzado
**Objetivo:** conducir / cocina / Bluetooth.

- Modo conducción: contrastes altos, 3 botones XXL, sin chips.
- Sleep timer (15/30/45/60 / fin de pista).
- Velocidad 0.8–1.5× en local (podcasts).
- Voz (reconocedor on-device): “pon X”, “atrás”, “siguiente”, “pausa”. Misma tubería que Fase 4.
- Android Auto / AVRCP: la cola de Media3 ya alimenta la sesión.

**Aceptación:** se usa la app sin mirar la lista, con auricular o volante.

### Fase 7 — Calidad, marca, letras
**Objetivo:** poder publicar.

- Tests: LRC parser, shuffle/repeat, skip <3 s, delete+cola, detección Brave (robolectric), coordinator LOCAL/WEB.
- Letras: cache, tap-to-seek, offset, estados vacío/cargando/error; también en pistas WEB por título/artista.
- Marca: nombre, acento, icono; unificar `applicationId` / package cuando toque Play Store.
- Release: no firmar con debug; minify; política de datos (lrclib + YouTube Data API).
- Accesibilidad: anunciar línea de letra activa; content descriptions de rewind.
- Landscape / tablet: carátula | lista.

**Aceptación:** suite mínima verde; APK release firmable; textos legales listos.

---

## 6. Orden de PRs (mapeo a fases)

| PR | Título | Depende de |
|---|---|---|
| PR0 | Cimientos: source WEB, skill, README, Accent, service export | — |
| PR1 | Now Playing manos libres + rewind + errores + letras plegables | PR0 |
| PR2 | Mini player seek + Back correcto | PR1 |
| PR3 | Cola UI + persistencia de sesión | PR1 |
| PR4 | Cola en Media3 / gapless | PR3 |
| PR5 | Detección Brave + Ajustes + CTA instalación | PR0 |
| PR6 | Puente local HTTP/WS + IFrame YouTube + BrowserTunnelPlayer | PR5, PR4 (coordinator estable) |
| PR7 | Búsqueda unificada local + YouTube | PR6 |
| PR8 | Biblioteca home / álbum / playlists | PR3 |
| PR9 | Modo conducción + sleep timer + voz | PR7 |
| PR10 | Tests, letras, marca, release | paralelo desde PR4 |

PR5–PR6 son el arranque Brave/YouTube. PR1–PR2 se pueden hacer **en paralelo** porque mejoran la UI ya usable.

---

## 7. Arranque recomendado (siguiente trabajo)

Orden práctico para “empezar ya”:

1. **PR0 + PR1 + PR2** — se ve y se siente manos libres en local (impacto inmediato de UI).
2. **PR3 + PR4** — cola y gapless (el motor deja de ser frágil).
3. **PR5 + PR6** — Brave existe → YouTube bajo el mismo mando.
4. El resto según uso real.

No mezclar Spotify ni WebView como motor. Brave es el navegador; YouTube IFrame es el player web; Compose es el mando.

---

## 8. Criterios de éxito globales

- Con una mano: pausa, rewind, skip, en app, bloqueo y widget.
- Si la canción está en el teléfono, suena en el teléfono.
- Si no está y Brave está instalado, suena YouTube en Brave, mandado desde la app.
- Un toque en Brave por sesión para desbloquear autoplay; después, mando nativo.
- Cero extracción de audio, cero APIs ocultas, cero clon de Spotify.
