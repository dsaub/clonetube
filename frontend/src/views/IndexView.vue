<script lang="ts" setup>
import { computed, onMounted, ref } from 'vue'
import Header from '@/components/Header.vue'
import UploadModal from '@/components/UploadModal.vue'
import { loadHomeVideos, type FeedVideo } from '@/api/feed'
import { useUserStore } from '@/stores/user'

const user = useUserStore()
const videos = ref<FeedVideo[]>([])
const loading = ref(true)
const error = ref('')
const uploadOpen = ref(false)
const uploadNotice = ref('')
const searchQuery = ref('')
const onlyFollowing = ref(false)

const canUpload = computed(() => user.logged_in)
const followedCount = computed(
  () => videos.value.filter((video) => video.fromFollowedAuthor).length,
)
const normalizedSearch = computed(() => normalizeSearch(searchQuery.value))
const filteredVideos = computed(() => {
  if (!normalizedSearch.value) return videos.value

  return videos.value.filter((video) => {
    const searchableText = [
      video.title,
      video.description,
      video.original_filename,
      video.author?.displayName,
      video.author?.username,
    ].filter(Boolean).join(' ')

    return normalizeSearch(searchableText).includes(normalizedSearch.value)
  })
})
const resultSummary = computed(() => {
  if (!normalizedSearch.value || loading.value || error.value) return ''
  const count = filteredVideos.value.length
  return `${count} ${count === 1 ? 'resultado' : 'resultados'} para «${searchQuery.value.trim()}»`
})

function normalizeSearch(value: string): string {
  return value
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .trim()
    .toLocaleLowerCase('es')
}

async function loadVideos() {
  loading.value = true
  error.value = ''

  try {
    videos.value = await loadHomeVideos(user.token?.access_token, {
      onlyFollowing: onlyFollowing.value,
    })
  } catch (cause: unknown) {
    error.value = cause instanceof Error ? cause.message : 'No se han podido cargar los videos.'
  } finally {
    loading.value = false
  }
}

function selectFeed(followingOnly: boolean) {
  if (onlyFollowing.value === followingOnly) return
  onlyFollowing.value = followingOnly
  void loadVideos()
}

function openUpload() {
  uploadNotice.value = ''
  if (!canUpload.value) {
    uploadNotice.value = 'Inicia sesión desde la cabecera para poder subir un video.'
    return
  }
  uploadOpen.value = true
}

function closeUpload() {
  uploadOpen.value = false
}

function onUploaded() {
  uploadNotice.value = 'El video ya está disponible en tu biblioteca.'
  void loadVideos()
}

function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} MB`
  return `${(bytes / 1024 / 1024 / 1024).toFixed(2)} GB`
}

function formatDate(value: string): string {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return new Intl.DateTimeFormat('es-ES', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
  }).format(date)
}

onMounted(loadVideos)
</script>

<template>
  <div class="home-page">
    <Header v-model:search-query="searchQuery" />

    <main class="home-content">
      <section class="hero" aria-labelledby="hero-title">
        <div class="hero-copy">
          <span class="eyebrow">TU SEÑAL · TU CONTENIDO</span>
          <h1 id="hero-title">Comparte lo que merece ser visto.</h1>
          <p>
            Sube videos por fragmentos, continúa navegando por tu biblioteca y
            reprodúcelos directamente desde Clonetube.
          </p>
          <button type="button" class="upload-button" @click="openUpload">
            <span class="upload-button-icon" aria-hidden="true">↑</span>
            Subir video
          </button>
          <p v-if="uploadNotice" class="upload-notice" role="status">{{ uploadNotice }}</p>
        </div>

        <div class="signal-card" aria-hidden="true">
          <div class="signal-screen">
            <span class="scan-line"></span>
            <span class="play-mark">▶</span>
          </div>
          <div class="signal-meta">
            <span>CLONETUBE</span>
            <span class="live-light"></span>
          </div>
        </div>
      </section>

      <section class="library" aria-labelledby="library-title">
        <div v-if="user.logged_in" class="feed-tabs" role="tablist" aria-label="Tipo de feed">
          <button
            type="button"
            role="tab"
            class="feed-tab"
            :class="{ active: !onlyFollowing }"
            :aria-selected="!onlyFollowing"
            @click="selectFeed(false)"
          >
            Para ti
          </button>
          <button
            type="button"
            role="tab"
            class="feed-tab"
            :class="{ active: onlyFollowing }"
            :aria-selected="onlyFollowing"
            @click="selectFeed(true)"
          >
            Siguiendo
          </button>
        </div>

        <div class="section-heading">
          <div>
            <span class="section-kicker">
              {{ normalizedSearch ? 'RESULTADOS DE BÚSQUEDA' : 'EMISIONES RECIENTES' }}
            </span>
            <h2 id="library-title">
              {{ normalizedSearch ? 'Videos encontrados' : 'Videos disponibles' }}
            </h2>
            <p v-if="!normalizedSearch && followedCount > 0" class="feed-hint">
              {{ followedCount }} de tus canales seguidos encabezan tu feed.
            </p>
            <p v-if="resultSummary" id="search-summary" class="result-summary" aria-live="polite">
              {{ resultSummary }}
            </p>
          </div>
          <button type="button" class="refresh-button" :disabled="loading" @click="loadVideos">
            {{ loading ? 'Actualizando…' : 'Actualizar' }}
          </button>
        </div>

        <div v-if="loading" class="video-grid skeleton-grid" aria-label="Cargando videos">
          <article v-for="item in 4" :key="item" class="video-card skeleton-card">
            <div class="video-cover skeleton"></div>
            <div class="skeleton-line wide"></div>
            <div class="skeleton-line"></div>
          </article>
        </div>

        <div v-else-if="error" class="empty-state error-state" role="alert">
          <span aria-hidden="true">!</span>
          <h3>No llega la señal</h3>
          <p>{{ error }}</p>
          <button type="button" @click="loadVideos">Reintentar</button>
        </div>

        <div v-else-if="videos.length === 0 && onlyFollowing" class="empty-state following-empty">
          <span aria-hidden="true">☆</span>
          <h3>Todavía no sigues a nadie</h3>
          <p>Sigue a un canal desde la página de un video y sus emisiones aparecerán aquí.</p>
          <button type="button" @click="selectFeed(false)">Ver todos los videos</button>
        </div>

        <div v-else-if="videos.length === 0" class="empty-state">
          <span aria-hidden="true">□</span>
          <h3>La biblioteca está vacía</h3>
          <p>Sube el primer video para empezar a emitir.</p>
          <button type="button" @click="openUpload">Subir un video</button>
        </div>

        <div v-else-if="filteredVideos.length === 0" class="empty-state search-empty">
          <span aria-hidden="true">⌕</span>
          <h3>No hay coincidencias</h3>
          <p>No encontramos ningún video con «{{ searchQuery.trim() }}».</p>
          <button type="button" @click="searchQuery = ''">Limpiar búsqueda</button>
        </div>

        <div v-else class="video-grid">
          <RouterLink
            v-for="video in filteredVideos"
            :key="video.key"
            class="video-card"
            :to="{ name: 'watch', query: { key: video.key, title: video.title } }"
            :aria-label="`Reproducir ${video.title}`"
          >
            <div class="video-cover">
              <span class="video-noise"></span>
              <span class="card-play">▶</span>
              <span v-if="video.fromFollowedAuthor" class="following-pill">SIGUIENDO</span>
              <span class="size-pill">{{ formatSize(video.size) }}</span>
            </div>
            <div class="video-info">
              <h3>{{ video.title }}</h3>
              <p v-if="video.author" class="video-author">
                {{ video.author.displayName }} · @{{ video.author.username }}
              </p>
              <p>{{ formatDate(video.last_modified) }}</p>
            </div>
          </RouterLink>
        </div>
      </section>
    </main>

    <UploadModal
      v-if="uploadOpen"
      @close="closeUpload"
      @uploaded="onUploaded"
    />
  </div>
</template>

<style scoped>
.home-page {
  min-height: 100vh;
  background:
    radial-gradient(circle at 82% 12%, rgba(108, 99, 255, 0.1), transparent 27rem),
    #0f0f1a;
  color: #e0e0e0;
}

.home-content {
  width: min(1180px, calc(100% - 2rem));
  margin: 0 auto;
  padding: clamp(2rem, 6vw, 5.5rem) 0 5rem;
}

.hero {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(18rem, 0.62fr);
  align-items: center;
  gap: clamp(2rem, 7vw, 6rem);
  min-height: 25rem;
  margin-bottom: clamp(4rem, 9vw, 7rem);
}

.eyebrow,
.section-kicker {
  color: #8882ff;
  font-size: 0.72rem;
  font-weight: 800;
  letter-spacing: 0.18em;
}

.hero h1 {
  max-width: 13ch;
  margin: 0.85rem 0 1.15rem;
  color: #f5f4ff;
  font-size: clamp(2.6rem, 6vw, 5.2rem);
  line-height: 0.98;
  letter-spacing: -0.055em;
}

.hero-copy > p:not(.upload-notice) {
  max-width: 39rem;
  color: #9d9bad;
  font-size: clamp(1rem, 1.7vw, 1.15rem);
  line-height: 1.7;
}

.upload-button {
  display: inline-flex;
  align-items: center;
  gap: 0.65rem;
  margin-top: 1.6rem;
  padding: 0.78rem 1.1rem;
  border: 1px solid #7c75ff;
  border-radius: 0.65rem;
  background: #6c63ff;
  box-shadow: 0 12px 30px rgba(108, 99, 255, 0.22);
  color: #fff;
  cursor: pointer;
  font: inherit;
  font-weight: 760;
  transition: transform 160ms ease, background 160ms ease, box-shadow 160ms ease;
}

.upload-button:hover {
  background: #7a72ff;
  box-shadow: 0 16px 36px rgba(108, 99, 255, 0.3);
  transform: translateY(-2px);
}

.upload-button-icon {
  font-size: 1.2rem;
  line-height: 1;
}

.upload-notice {
  margin-top: 0.85rem;
  color: #aaa7bd;
  font-size: 0.85rem;
}

.signal-card {
  position: relative;
  padding: 0.85rem;
  border: 1px solid #333149;
  border-radius: 1.35rem;
  background: linear-gradient(145deg, #202033, #151522);
  box-shadow: 0 32px 70px rgba(0, 0, 0, 0.42), 0 0 45px rgba(108, 99, 255, 0.08);
  transform: rotate(2deg);
}

.signal-screen {
  position: relative;
  display: grid;
  aspect-ratio: 16 / 10;
  place-items: center;
  overflow: hidden;
  border: 1px solid #454163;
  border-radius: 0.8rem;
  background:
    repeating-linear-gradient(0deg, rgba(255,255,255,0.025) 0 1px, transparent 1px 4px),
    radial-gradient(circle at center, #282652, #11111e 72%);
}

.signal-screen::before {
  position: absolute;
  inset: 0;
  background: linear-gradient(115deg, transparent 35%, rgba(255,255,255,0.07), transparent 62%);
  content: '';
}

.scan-line {
  position: absolute;
  right: 0;
  left: 0;
  height: 2px;
  background: rgba(111, 229, 255, 0.38);
  box-shadow: 0 0 12px rgba(111, 229, 255, 0.65);
  animation: scan 3.2s linear infinite;
}

.play-mark {
  display: grid;
  width: 4.5rem;
  height: 4.5rem;
  place-items: center;
  border: 1px solid rgba(255,255,255,0.22);
  border-radius: 50%;
  background: rgba(12, 12, 24, 0.58);
  color: #fff;
  font-size: 1.4rem;
  text-indent: 0.18rem;
  backdrop-filter: blur(4px);
}

.signal-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.75rem 0.45rem 0.1rem;
  color: #757387;
  font-size: 0.65rem;
  font-weight: 800;
  letter-spacing: 0.2em;
}

.live-light {
  width: 0.48rem;
  height: 0.48rem;
  border-radius: 50%;
  background: #69e8a3;
  box-shadow: 0 0 12px rgba(105, 232, 163, 0.75);
}

.section-heading {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 1rem;
  margin-bottom: 1.35rem;
}

.section-heading h2 {
  margin-top: 0.35rem;
  color: #f0eff8;
  font-size: clamp(1.6rem, 4vw, 2.35rem);
  letter-spacing: -0.035em;
}

.result-summary,
.feed-hint {
  margin-top: 0.38rem;
  color: #858294;
  font-size: 0.82rem;
}

.feed-tabs {
  display: flex;
  gap: 0.4rem;
  margin-bottom: 1.1rem;
}

.feed-tab {
  padding: 0.45rem 0.95rem;
  border: 1px solid #38364e;
  border-radius: 999px;
  background: #181824;
  color: #b3b1c0;
  cursor: pointer;
  font: inherit;
  font-size: 0.82rem;
  font-weight: 700;
  transition: border-color 160ms ease, background 160ms ease, color 160ms ease;
}

.feed-tab:hover { border-color: #4b4772; }

.feed-tab.active {
  border-color: #7c75ff;
  background: #24223d;
  color: #dcd9ff;
}

.refresh-button {
  padding: 0.5rem 0.75rem;
  border: 1px solid #38364e;
  border-radius: 0.5rem;
  background: #181824;
  color: #b3b1c0;
  cursor: pointer;
  font: inherit;
  font-size: 0.8rem;
}

.refresh-button:disabled { cursor: wait; opacity: 0.55; }

.video-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(15rem, 1fr));
  gap: 1.25rem;
}

.video-card {
  display: block;
  min-width: 0;
  padding: 0.55rem;
  border: 1px solid transparent;
  border-radius: 0.9rem;
  background: rgba(25, 25, 39, 0.72);
  color: inherit;
  transition: border-color 180ms ease, background 180ms ease, transform 180ms ease;
}

.video-card:hover {
  border-color: #3e3b5b;
  background: #1c1c2b;
  transform: translateY(-3px);
}

.video-cover {
  position: relative;
  display: grid;
  aspect-ratio: 16 / 9;
  place-items: center;
  overflow: hidden;
  border-radius: 0.58rem;
  background: linear-gradient(135deg, #26244b, #12121e 68%);
}

.video-noise {
  position: absolute;
  inset: 0;
  opacity: 0.34;
  background: repeating-linear-gradient(0deg, transparent 0 3px, rgba(255,255,255,0.035) 3px 4px);
}

.card-play {
  position: relative;
  display: grid;
  width: 3rem;
  height: 3rem;
  place-items: center;
  border-radius: 50%;
  background: rgba(108, 99, 255, 0.88);
  color: #fff;
  text-indent: 0.12rem;
  transition: transform 180ms ease;
}

.video-card:hover .card-play { transform: scale(1.1); }

.following-pill {
  position: absolute;
  top: 0.55rem;
  left: 0.55rem;
  padding: 0.2rem 0.4rem;
  border: 1px solid #7c75ff;
  border-radius: 0.3rem;
  background: rgba(108, 99, 255, 0.85);
  color: #fff;
  font-size: 0.62rem;
  font-weight: 800;
  letter-spacing: 0.08em;
}

.size-pill {
  position: absolute;
  right: 0.55rem;
  bottom: 0.55rem;
  padding: 0.2rem 0.38rem;
  border-radius: 0.3rem;
  background: rgba(7, 7, 13, 0.78);
  color: #d8d7e3;
  font-size: 0.67rem;
  font-weight: 700;
}

.video-info { padding: 0.75rem 0.35rem 0.45rem; }

.video-info h3 {
  overflow: hidden;
  margin: 0;
  color: #e8e7f0;
  font-size: 0.94rem;
  font-weight: 680;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.video-info p {
  margin-top: 0.32rem;
  color: #777587;
  font-size: 0.75rem;
}

.video-info .video-author {
  overflow: hidden;
  color: #aaa7bb;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.empty-state {
  display: grid;
  min-height: 16rem;
  place-items: center;
  align-content: center;
  gap: 0.55rem;
  padding: 2rem;
  border: 1px dashed #343248;
  border-radius: 1rem;
  background: rgba(23, 23, 35, 0.58);
  color: #8f8d9f;
  text-align: center;
}

.empty-state > span { color: #6863a5; font-size: 2rem; }
.empty-state h3 { color: #d8d7e2; }
.empty-state p { font-size: 0.9rem; }

.empty-state button {
  margin-top: 0.55rem;
  padding: 0.55rem 0.75rem;
  border: 1px solid #4b4772;
  border-radius: 0.5rem;
  background: #24223d;
  color: #dcd9ff;
  cursor: pointer;
  font: inherit;
  font-size: 0.82rem;
  font-weight: 700;
}

.error-state > span { color: #ff7a86; }

.skeleton-card { pointer-events: none; }
.skeleton { background: #222131; }
.skeleton-line { width: 52%; height: 0.65rem; margin: 0.65rem 0.35rem 0; border-radius: 99px; background: #29283a; }
.skeleton-line.wide { width: 78%; margin-top: 0.85rem; }

@keyframes scan {
  from { top: -2px; opacity: 0; }
  8%, 92% { opacity: 1; }
  to { top: 100%; opacity: 0; }
}

@media (max-width: 760px) {
  .hero { grid-template-columns: 1fr; min-height: auto; }
  .hero h1 { max-width: 15ch; }
  .signal-card { width: min(100%, 28rem); margin: 0 auto; transform: none; }
}

@media (prefers-reduced-motion: reduce) {
  .scan-line { animation: none; top: 50%; }
  .video-card,
  .upload-button { transition: none; }
}
</style>
