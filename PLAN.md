# Plan: Selector de calidad + Auto (HLS con ABR real) en el reproductor

## Decisiones tomadas

- **HLS real con hls.js**: Auto = ABR de verdad (adaptación por ancho de banda medido). El worker pasará a generar **rendiciones HLS** en lugar de MP4 progresivos sueltos.
- **"Original"** se mantiene como opción máxima: es el MP4 fuente servido progresivamente, como hoy (no forma parte del HLS; el player conmuta entre motor HLS y reproducción progresiva).

## Arquitectura resultante

```
worker: videos/<uuid>.mp4 ──► videos/<uuid>/hls/720p/playlist.m3u8 + seg_00001.ts...
                              videos/<uuid>/hls/480p/playlist.m3u8 + ...
                              videos/<uuid>/hls/master.m3u8   (se sube el ÚLTIMO)
backend: GET /api/v1/video/playback?id= ──► { originalUrl, hlsMasterUrl | null }
         GET /api/v1/video/stream/<key...>  (path-based: playlists y segmentos por proxy)
frontend: VideoPlayer = hls.js (Auto + niveles) | HLS nativo Safari | MP4 progresivo
```

---

## Fase 1 — Worker (`worker/transcoder.py`)

1. **Rendiciones HLS por perfil** (mismos `PROFILES`): ffmpeg con `-f hls -hls_time 4 -hls_playlist_type vod -hls_segment_filename .../seg_%05d.ts`, mismo escalado/fps/codecs actuales. Validación de salida con ffprobe igual que ahora.
2. **Subida del árbol** `{stem}/hls/{perfil}/` con content types correctos: `application/vnd.apple.mpegurl` (.m3u8), `video/mp2t` (.ts).
3. **`master.m3u8`** generado tras las rendiciones con `#EXT-X-STREAM-INF:BANDWIDTH=…,RESOLUTION=WxH` por variante (bandwidth calculado de bytes reales de segmentos ÷ duración; resolución del probe de salida). Se sube **el último** ⇒ su presencia marca trabajo completo. Metadatos S3: `clonetube-profile: hls-ts-v1` (bump de `TRANSCODE_VERSION`), `source-etag`, `transcode-job-id`.
4. **Idempotencia simplificada**: `_is_complete` pasa a comprobar el HEAD del `master.m3u8` (etag + versión) en vez de cada MP4.
5. **Tests**: extender `worker/tests/test_transcoder.py` (comando ffmpeg HLS, generación del master, layout de keys, idempotencia).

## Fase 2 — Backend

1. **Endpoint path-based de streaming**: `GET /api/v1/video/stream/{*key}` (PathPattern de Spring). Imprescindible: las URIs relativas de los playlists (`seg_00001.ts`) deben resolver bien por el proxy; con `?key=` se perderían. Se mantiene el `?key=` legacy para el original/Android.
   - Resolución del vídeo canónico: match exacto por `filename`; si la key contiene `/hls/`, se deriva la fuente (`{stem}.mp4`) y el resto se valida contra **whitelist estricta**: `hls/master.m3u8`, `hls/(1080p|720p|480p|360p|120p)/playlist.m3u8`, `hls/(…)/seg_\d+\.ts` (+ legacy `{perfil}.mp4`). Cualquier otra cosa → 404. Mismo `requireVideoAccess` de siempre sobre el vídeo canónico; `Range` ya existente sirve para segmentos.
2. **`GET /api/v1/video/playback?id=`** → `{ original: {url}, hls: {masterUrl} | null }`. Disponibilidad = HEAD a `{stem}/hls/master.m3u8`. URLs: CDN directo si público + `S3_PUBLIC_ENDPOINT_URL`; si no, rutas proxy `/stream/…`. Para privados el master va por proxy y el JWT viajará en cabecera `Authorization` (hls.js sí puede enviar cabeceras, a diferencia de `<video>`).
3. **`deleteVideo`**: borrar la key fuente + **todo el prefijo `{stem}/`** (list + delete por lotes) — cubre variantes MP4 legacy y árboles HLS.
4. **Tests**: unit tests de `VideoService` con `S3Client` mockeado (whitelist, 404 en keys arbitrarias, disponibilidad HLS, borrado por prefijo) + `mvn -B test` verde.

## Fase 3 — Frontend

1. **Dependencia**: `hls.js` (import dinámico para no inflar el bundle inicial).
2. **`api/video.ts`**: `getPlaybackInfo(id, token)` → `{ originalUrl, hlsMasterUrl | null }`.
3. **`VideoPlayer.vue`**:
   - Props nuevas: `hlsSrc?: string`, `token?: string` (además de `src`/`poster`).
   - Motor: hls.js si hay `hlsSrc` y MSE disponible → HLS nativo Safari (`canPlayType`) → MP4 progresivo (fallback actual).
   - **Menú de calidad** (botón ⚙ en `controls-right`, estilo oscuro existente): `Auto` + niveles de `hls.levels` (`{height}p`, descendente) + `Original`.
     - Auto → `hls.currentLevel = -1`; etiqueta `Auto (720p)` actualizada con `LEVEL_SWITCHED`.
     - Manual → `hls.currentLevel = i` (cambio inmediato).
     - Original → destruir hls, `video.src = src`, restaurando `currentTime` y estado de reproducción; al volver a una calidad/Auto se re-inicia hls con `startPosition`.
   - **Persistencia** en `localStorage` (`clonetube-quality`: `auto` | `original` | `720p`…), aplicada al parsear el manifiesto; si la calidad guardada no existe en ese vídeo → Auto.
   - `xhrSetup`: añade `Authorization: Bearer <token>` si hay token (vídeos privados).
   - Safari iOS (HLS nativo sin API de niveles): menú solo Auto/Original.
   - Limpieza: `hls.destroy()` al desmontar y al cambiar de vídeo.
4. **`WatchView.vue`**: usa `getPlaybackInfo` y pasa las nuevas props.
5. **Tests Vitest**: mockear `hls.js` (jsdom no tiene MSE): render del menú, selección manual/Auto, persistencia, cambio a Original preservando tiempo, selector oculto cuando no hay HLS. `pnpm vitest run` + `type-check` verdes.

## Fase 4 — Migración, docs y verificación

- **Vídeos existentes** sin HLS: `hls: null` ⇒ se reproduce el Original como hasta ahora (sin regresión). Opcional: reencolar el catálogo con un script que reutilice el patrón de `VideoTranscodeRetryService` (el worker omite los que ya tienen master).
- **Android**: sin cambios (sigue con el progresivo vía `?key=`).
- Actualizar **AGENTS.md** (worker HLS, endpoint playback, selector de calidad).
- Verificación: `mvn -B test`, `pnpm vitest run`, tests del worker; y prueba manual subiendo un vídeo y comprobando Auto/cambio manual/Original con throttling de red.
- Nota de entorno dev: para probar transcodificación en local hace falta la cola SQS + worker activos (hoy el stack dev no los incluye); se puede validar con LocalStack/elasticmq o en entorno prod-like.

## Esfuerzo estimado

| Fase | Alcance | Esfuerzo |
|---|---|---|
| 1. Worker HLS | ffmpeg HLS + master + subida + tests | Medio |
| 2. Backend | stream path-based + playback + delete + tests | Medio |
| 3. Frontend | hls.js + menú + persistencia + tests | Medio-alto |
| 4. Docs/migración | AGENTS.md + verificación | Bajo |
