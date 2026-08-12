<script setup lang="ts">
import { computed, nextTick, ref, shallowRef, onMounted, onUnmounted, watch } from 'vue'
import type Hls from 'hls.js'
import type { Level } from 'hls.js'

const props = defineProps<{
  src: string
  poster?: string
  hlsSrc?: string
  token?: string
}>()

type QualityPreference = 'auto' | 'original' | `${number}p`
type PlaybackEngine = 'hls.js' | 'native-hls' | 'original'

const QUALITY_STORAGE_KEY = 'clonetube-quality'

// ─── Refs ─────────────────────────────────────────────────────────
const videoRef = ref<HTMLVideoElement | null>(null)
const containerRef = ref<HTMLDivElement | null>(null)
const progressRef = ref<HTMLDivElement | null>(null)
const volumeSliderRef = ref<HTMLInputElement | null>(null)

const playing = ref(false)
const currentTime = ref(0)
const duration = ref(0)
const buffered = ref(0)
const volume = ref(1)
const muted = ref(false)
const playbackRate = ref(1)
const loading = ref(true)
const error = ref(false)
const fullscreen = ref(false)
const showControls = ref(true)
const controlsTimer = ref<ReturnType<typeof setTimeout> | null>(null)
const hls = shallowRef<Hls | null>(null)
const playbackEngine = ref<PlaybackEngine>('original')
const levels = ref<Array<{ index: number; height: number }>>([])
const activeLevelHeight = ref<number | null>(null)
const qualityPreference = ref<QualityPreference>(readQualityPreference())
const qualityMenuOpen = ref(false)
let sourceGeneration = 0

const hasQualitySelector = computed(() => !!props.hlsSrc)
const qualityLabel = computed(() => {
  if (qualityPreference.value === 'auto') {
    return activeLevelHeight.value ? `Auto (${activeLevelHeight.value}p)` : 'Auto'
  }
  if (qualityPreference.value === 'original') return 'Original'
  return qualityPreference.value
})

function readQualityPreference(): QualityPreference {
  const stored = localStorage.getItem(QUALITY_STORAGE_KEY)
  return stored === 'auto' || stored === 'original' || /^\d+p$/.test(stored ?? '')
    ? stored as QualityPreference
    : 'auto'
}

function persistQuality(preference: QualityPreference) {
  qualityPreference.value = preference
  localStorage.setItem(QUALITY_STORAGE_KEY, preference)
}

function destroyHls() {
  hls.value?.destroy()
  hls.value = null
}

function resetPlayerState() {
  loading.value = true
  error.value = false
  currentTime.value = 0
  duration.value = 0
  buffered.value = 0
  levels.value = []
  activeLevelHeight.value = null
  qualityMenuOpen.value = false
}

function restorePlayback(position: number, wasPlaying: boolean) {
  const video = videoRef.value
  if (!video) return

  const restore = () => {
    video.currentTime = position
    if (wasPlaying) void video.play()
  }
  if (video.readyState >= HTMLMediaElement.HAVE_METADATA) restore()
  else video.addEventListener('loadedmetadata', restore, { once: true })
}

function useOriginal(position = 0, wasPlaying = false) {
  const video = videoRef.value
  if (!video) return

  destroyHls()
  playbackEngine.value = 'original'
  activeLevelHeight.value = null
  video.src = props.src
  video.load()
  restorePlayback(position, wasPlaying)
}

function useNativeHls(position = 0, wasPlaying = false) {
  const video = videoRef.value
  if (!video || !props.hlsSrc) return

  destroyHls()
  playbackEngine.value = 'native-hls'
  levels.value = []
  activeLevelHeight.value = null
  video.src = props.hlsSrc
  video.load()
  restorePlayback(position, wasPlaying)
}

async function useHlsJs(position = 0, wasPlaying = false, generation = sourceGeneration) {
  const video = videoRef.value
  if (!video || !props.hlsSrc) return

  const { default: HlsClass } = await import('hls.js')
  if (generation !== sourceGeneration || !videoRef.value || !props.hlsSrc) return

  destroyHls()
  playbackEngine.value = 'hls.js'
  const instance = new HlsClass({
    startPosition: position,
    xhrSetup: (xhr) => {
      if (props.token) xhr.setRequestHeader('Authorization', `Bearer ${props.token}`)
    },
  })
  hls.value = instance
  instance.on(HlsClass.Events.MANIFEST_PARSED, (_event, data) => {
    const availableLevels = data.levels
      .map((level: Level, index: number) => ({ index, height: level.height }))
      .filter((level) => level.height > 0)
      .sort((a, b) => b.height - a.height)
    levels.value = availableLevels

    if (qualityPreference.value === 'original') {
      useOriginal(position, wasPlaying)
      return
    }

    const savedHeight = Number.parseInt(qualityPreference.value, 10)
    const savedLevel = availableLevels.find((level) => level.height === savedHeight)
    if (qualityPreference.value !== 'auto' && !savedLevel) persistQuality('auto')
    instance.currentLevel = savedLevel?.index ?? -1
    if (wasPlaying) void video.play()
  })
  instance.on(HlsClass.Events.LEVEL_SWITCHED, (_event, data) => {
    activeLevelHeight.value = instance.levels[data.level]?.height ?? null
  })
  instance.on(HlsClass.Events.ERROR, (_event, data) => {
    if (data.fatal) useOriginal(video.currentTime, !video.paused)
  })
  instance.loadSource(props.hlsSrc)
  instance.attachMedia(video)
}

async function initializeSource(position = 0, wasPlaying = false) {
  const video = videoRef.value
  if (!video) return
  const generation = ++sourceGeneration

  destroyHls()
  levels.value = []
  activeLevelHeight.value = null
  if (!props.hlsSrc || qualityPreference.value === 'original') {
    useOriginal(position, wasPlaying)
    return
  }

  const { default: HlsClass } = await import('hls.js')
  if (generation !== sourceGeneration) return
  if (HlsClass.isSupported()) {
    await useHlsJs(position, wasPlaying, generation)
  } else if (video.canPlayType('application/vnd.apple.mpegurl')) {
    useNativeHls(position, wasPlaying)
  } else {
    useOriginal(position, wasPlaying)
  }
}

function selectQuality(preference: QualityPreference) {
  const video = videoRef.value
  if (!video) return
  const position = video.currentTime
  const wasPlaying = !video.paused
  persistQuality(preference)
  qualityMenuOpen.value = false

  if (preference === 'original') {
    useOriginal(position, wasPlaying)
    return
  }
  if (playbackEngine.value !== 'hls.js') {
    void initializeSource(position, wasPlaying)
    return
  }

  const height = Number.parseInt(preference, 10)
  const level = levels.value.find((candidate) => candidate.height === height)
  hls.value!.currentLevel = preference === 'auto' ? -1 : level?.index ?? -1
}

// ─── Formateo ─────────────────────────────────────────────────────
function formatTime(seconds: number): string {
  if (!isFinite(seconds) || seconds < 0) return '0:00'
  const m = Math.floor(seconds / 60)
  const s = Math.floor(seconds % 60)
  return `${m}:${s.toString().padStart(2, '0')}`
}

// ─── Controles ────────────────────────────────────────────────────
function togglePlay() {
  const video = videoRef.value
  if (!video) return
  if (video.paused) {
    video.play()
  } else {
    video.pause()
  }
}

function onPlay() { playing.value = true; loading.value = false }
function onPause() { playing.value = false }
function onTimeUpdate() {
  const video = videoRef.value
  if (!video) return
  currentTime.value = video.currentTime
  duration.value = video.duration
}
function onLoadedMetadata() { loading.value = false }
function onWaiting() { loading.value = true }
function onCanPlay() { loading.value = false }
function onError() { error.value = true; loading.value = false }

function onProgress() {
  const video = videoRef.value
  if (!video || video.buffered.length === 0) return
  buffered.value = video.buffered.end(video.buffered.length - 1) / video.duration * 100
}

function seek(e: MouseEvent) {
  const bar = progressRef.value
  const video = videoRef.value
  if (!bar || !video) return
  const rect = bar.getBoundingClientRect()
  const ratio = (e.clientX - rect.left) / rect.width
  video.currentTime = ratio * video.duration
}

function setVolume(e: Event) {
  const target = e.target as HTMLInputElement
  volume.value = parseFloat(target.value)
  if (videoRef.value) {
    videoRef.value.volume = volume.value
    muted.value = volume.value === 0
  }
}

function toggleMute() {
  if (!videoRef.value) return
  muted.value = !muted.value
  videoRef.value.muted = muted.value
  if (muted.value) {
    volume.value = 0
  } else {
    videoRef.value.volume = volume.value || 1
    volume.value = videoRef.value.volume
  }
}

function setPlaybackRate(rate: number) {
  playbackRate.value = rate
  if (videoRef.value) videoRef.value.playbackRate = rate
}

function toggleFullscreen() {
  const el = containerRef.value
  if (!el) return
  if (!document.fullscreenElement) {
    el.requestFullscreen()
    fullscreen.value = true
  } else {
    document.exitFullscreen()
    fullscreen.value = false
  }
}

// ─── Mostrar/ocultar controles ────────────────────────────────────
function scheduleHideControls() {
  if (controlsTimer.value) clearTimeout(controlsTimer.value)
  showControls.value = true
  if (playing.value) {
    controlsTimer.value = setTimeout(() => {
      showControls.value = false
    }, 3000)
  }
}

function onMouseMove() { scheduleHideControls() }
function onMouseLeave() {
  if (playing.value) {
    showControls.value = false
  }
}

// ─── Teclado ──────────────────────────────────────────────────────
function onKeydown(e: KeyboardEvent) {
  const video = videoRef.value
  if (!video) return

  switch (e.code) {
    case 'Space':
      e.preventDefault()
      togglePlay()
      break
    case 'ArrowLeft':
      video.currentTime = Math.max(0, video.currentTime - 5)
      break
    case 'ArrowRight':
      video.currentTime = Math.min(video.duration, video.currentTime + 5)
      break
    case 'ArrowUp':
      e.preventDefault()
      volume.value = Math.min(1, volume.value + 0.1)
      video.volume = volume.value
      muted.value = volume.value === 0
      break
    case 'ArrowDown':
      e.preventDefault()
      volume.value = Math.max(0, volume.value - 0.1)
      video.volume = volume.value
      muted.value = volume.value === 0
      break
    case 'KeyF':
      toggleFullscreen()
      break
    case 'KeyM':
      toggleMute()
      break
  }
}

// ─── Fullscreen change ────────────────────────────────────────────
function onFullscreenChange() {
  fullscreen.value = !!document.fullscreenElement
}

watch(() => [props.src, props.hlsSrc] as const, async () => {
  sourceGeneration++
  destroyHls()
  resetPlayerState()
  await nextTick()
  void initializeSource()
})

onMounted(() => {
  document.addEventListener('fullscreenchange', onFullscreenChange)
  void initializeSource()
})

onUnmounted(() => {
  document.removeEventListener('fullscreenchange', onFullscreenChange)
  if (controlsTimer.value) clearTimeout(controlsTimer.value)
  sourceGeneration++
  destroyHls()
})
</script>

<template>
  <div
    ref="containerRef"
    class="video-player"
    :class="{ 'is-fullscreen': fullscreen, 'is-loading': loading }"
    @mousemove="onMouseMove"
    @mouseleave="onMouseLeave"
    tabindex="0"
    @keydown="onKeydown"
  >
    <!-- Video nativo -->
    <video
      ref="videoRef"
      class="player-video"
      :poster="poster"
      preload="metadata"
      crossorigin="anonymous"
      playsinline
      @play="onPlay"
      @pause="onPause"
      @timeupdate="onTimeUpdate"
      @loadedmetadata="onLoadedMetadata"
      @waiting="onWaiting"
      @canplay="onCanPlay"
      @progress="onProgress"
      @error="onError"
      @click="togglePlay"
      @dblclick="toggleFullscreen"
    />

    <!-- Spinner de carga -->
    <div v-if="loading" class="loading-spinner">
      <div class="spinner"></div>
    </div>

    <!-- Mensaje de error -->
    <div v-if="error" class="error-overlay">
      <span>Error al cargar el video</span>
    </div>

    <!-- Play grande centrado -->
    <button
      v-if="!playing && !loading && !error"
      class="big-play"
      @click="togglePlay"
      aria-label="Reproducir"
    >
      ▶
    </button>

    <!-- Controles inferiores -->
    <div class="controls" :class="{ 'controls-hidden': !showControls && playing }">
      <!-- Barra de progreso -->
      <div
        ref="progressRef"
        class="progress-bar"
        @click="seek"
      >
        <div class="progress-buffered" :style="{ width: buffered + '%' }"></div>
        <div class="progress-filled" :style="{ width: (duration ? (currentTime / duration) * 100 : 0) + '%' }"></div>
        <div
          class="progress-thumb"
          :style="{ left: (duration ? (currentTime / duration) * 100 : 0) + '%' }"
        ></div>
      </div>

      <!-- Botones de control -->
      <div class="controls-row">
        <div class="controls-left">
          <button class="ctrl-btn" @click="togglePlay" :aria-label="playing ? 'Pausar' : 'Reproducir'">
            {{ playing ? '⏸' : '▶' }}
          </button>

          <button class="ctrl-btn" @click="toggleMute" :aria-label="muted ? 'Activar sonido' : 'Silenciar'">
            {{ muted || volume === 0 ? '🔇' : volume < 0.5 ? '🔉' : '🔊' }}
          </button>

          <input
            ref="volumeSliderRef"
            type="range"
            class="volume-slider"
            min="0"
            max="1"
            step="0.05"
            :value="volume"
            @input="setVolume"
          />

          <span class="time-display">
            {{ formatTime(currentTime) }} / {{ formatTime(duration) }}
          </span>
        </div>

        <div class="controls-right">
          <div v-if="hasQualitySelector" class="quality-control">
            <button
              class="ctrl-btn quality-btn"
              type="button"
              aria-label="Calidad de video"
              :aria-expanded="qualityMenuOpen"
              @click="qualityMenuOpen = !qualityMenuOpen"
            >
              <span aria-hidden="true">⚙</span>
              <span>{{ qualityLabel }}</span>
            </button>

            <div v-if="qualityMenuOpen" class="quality-menu" role="menu" aria-label="Calidad de video">
              <button
                class="quality-option"
                :class="{ active: qualityPreference === 'auto' }"
                role="menuitemradio"
                :aria-checked="qualityPreference === 'auto'"
                @click="selectQuality('auto')"
              >Auto</button>
              <button
                v-for="level in levels"
                :key="level.index"
                class="quality-option"
                :class="{ active: qualityPreference === `${level.height}p` }"
                role="menuitemradio"
                :aria-checked="qualityPreference === `${level.height}p`"
                @click="selectQuality(`${level.height}p`)"
              >{{ level.height }}p</button>
              <button
                class="quality-option"
                :class="{ active: qualityPreference === 'original' }"
                role="menuitemradio"
                :aria-checked="qualityPreference === 'original'"
                @click="selectQuality('original')"
              >Original</button>
            </div>
          </div>

          <button
            class="ctrl-btn speed-btn"
            @click="setPlaybackRate(playbackRate === 1 ? 1.5 : playbackRate === 1.5 ? 2 : 1)"
          >
            {{ playbackRate }}x
          </button>

          <button class="ctrl-btn" @click="toggleFullscreen" aria-label="Pantalla completa">
            {{ fullscreen ? '↙' : '⛶' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.video-player {
  position: relative;
  width: 100%;
  max-width: 100%;
  background: #000;
  border-radius: 12px;
  overflow: hidden;
  outline: none;
  cursor: default;
  aspect-ratio: 16 / 9;
}

.video-player.is-fullscreen {
  border-radius: 0;
  aspect-ratio: auto;
  height: 100vh;
  width: 100vw;
}

.player-video {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: contain;
}

/* ─── Spinner ────────────────────────────────────── */
.loading-spinner {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  pointer-events: none;
}

.spinner {
  width: 48px;
  height: 48px;
  border: 4px solid rgba(255, 255, 255, 0.2);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin { to { transform: rotate(360deg); } }

/* ─── Error ──────────────────────────────────────── */
.error-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.7);
  color: #ff6b6b;
  font-size: 1.1rem;
  font-weight: 500;
}

/* ─── Big play ───────────────────────────────────── */
.big-play {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.3);
  border: none;
  color: #fff;
  font-size: 4rem;
  cursor: pointer;
  opacity: 0;
  transition: opacity 0.2s;
}

.video-player:hover .big-play { opacity: 1; }

/* ─── Controles ──────────────────────────────────── */
.controls {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  background: linear-gradient(transparent, rgba(0, 0, 0, 0.8));
  padding: 2rem 1rem 0.75rem;
  opacity: 1;
  transition: opacity 0.3s;
}

.controls-hidden { opacity: 0; pointer-events: none; }

/* Progreso */
.progress-bar {
  position: relative;
  width: 100%;
  height: 5px;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 3px;
  cursor: pointer;
  margin-bottom: 0.5rem;
  transition: height 0.15s;
}

.progress-bar:hover { height: 8px; }

.progress-buffered {
  position: absolute;
  top: 0;
  left: 0;
  height: 100%;
  background: rgba(255, 255, 255, 0.3);
  border-radius: 3px;
}

.progress-filled {
  position: absolute;
  top: 0;
  left: 0;
  height: 100%;
  background: #6c63ff;
  border-radius: 3px;
}

.progress-thumb {
  position: absolute;
  top: 50%;
  width: 13px;
  height: 13px;
  background: #6c63ff;
  border-radius: 50%;
  transform: translate(-50%, -50%);
  opacity: 0;
  transition: opacity 0.15s;
}

.progress-bar:hover .progress-thumb { opacity: 1; }

/* Fila de controles */
.controls-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
}

.controls-left,
.controls-right {
  display: flex;
  align-items: center;
  gap: 0.4rem;
}

.ctrl-btn {
  background: none;
  border: none;
  color: #fff;
  font-size: 1.1rem;
  padding: 0.3rem 0.4rem;
  cursor: pointer;
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.15s;
}

.ctrl-btn:hover { background: rgba(255, 255, 255, 0.15); }

.speed-btn {
  font-size: 0.8rem;
  font-weight: 600;
  min-width: 2.5rem;
}

.quality-control { position: relative; }

.quality-btn {
  gap: 0.35rem;
  font-size: 0.78rem;
  font-weight: 600;
  white-space: nowrap;
}

.quality-menu {
  position: absolute;
  right: 0;
  bottom: calc(100% + 0.6rem);
  display: grid;
  min-width: 9rem;
  padding: 0.35rem;
  border: 1px solid #3a3a58;
  border-radius: 8px;
  background: rgba(24, 24, 39, 0.98);
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.45);
}

.quality-option {
  border: 0;
  border-radius: 5px;
  padding: 0.5rem 0.65rem;
  background: transparent;
  color: #ddd;
  font: inherit;
  text-align: left;
  cursor: pointer;
}

.quality-option:hover,
.quality-option.active {
  background: #302d55;
  color: #fff;
}

.quality-option.active::before {
  content: '✓';
  display: inline-block;
  width: 1.15rem;
  color: #9d97ff;
}

.volume-slider {
  width: 70px;
  height: 4px;
  accent-color: #6c63ff;
  cursor: pointer;
}

.time-display {
  font-size: 0.82rem;
  color: #ccc;
  white-space: nowrap;
  margin-left: 0.25rem;
  font-variant-numeric: tabular-nums;
}
</style>
