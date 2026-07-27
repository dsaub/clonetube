<script lang="ts" setup>
import { computed, onMounted, ref } from 'vue'
import Header from '@/components/Header.vue'
import { listVideosWithMetadata, type VideoCatalogItem } from '@/api/video'

const videos = ref<VideoCatalogItem[]>([])
const loading = ref(true)
const error = ref('')
const searchQuery = ref('')

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
    videos.value = await listVideosWithMetadata()
  } catch (cause: unknown) {
    error.value = cause instanceof Error ? cause.message : 'No se han podido cargar los videos.'
  } finally {
    loading.value = false
  }
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
      <section class="library" aria-labelledby="library-title">
        <div class="section-heading">
          <div>
            <span class="section-kicker">
              {{ normalizedSearch ? 'RESULTADOS DE BÚSQUEDA' : 'EMISIONES RECIENTES' }}
            </span>
            <h2 id="library-title">
              {{ normalizedSearch ? 'Videos encontrados' : 'Videos disponibles' }}
            </h2>
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

        <div v-else-if="videos.length === 0" class="empty-state">
          <span aria-hidden="true">□</span>
          <h3>La biblioteca está vacía</h3>
          <p>Todavía no hay videos disponibles.</p>
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
  padding: clamp(1.5rem, 4vw, 3rem) 0 5rem;
}

.section-kicker {
  color: #8882ff;
  font-size: 0.72rem;
  font-weight: 800;
  letter-spacing: 0.18em;
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

.result-summary {
  margin-top: 0.38rem;
  color: #858294;
  font-size: 0.82rem;
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

@media (prefers-reduced-motion: reduce) {
  .video-card { transition: none; }
}
</style>
