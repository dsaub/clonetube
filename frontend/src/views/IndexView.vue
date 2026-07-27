<script lang="ts" setup>
import { computed, onMounted, ref } from 'vue'
import Header from '@/components/Header.vue'
import { loadHomeVideos, type FeedVideo } from '@/api/feed'
import { useUserStore } from '@/stores/user'

const user = useUserStore()
const videos = ref<FeedVideo[]>([])
const loading = ref(true)
const error = ref('')
const searchQuery = ref('')
const onlyFollowing = ref(false)

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

    <div class="app-shell">
      <nav class="app-sidebar" aria-label="Navegación principal">
        <button
          type="button"
          class="side-item"
          :class="{ active: !onlyFollowing }"
          @click="selectFeed(false)"
        >
          <span class="side-icon" aria-hidden="true">⌂</span>
          Inicio
        </button>
        <button
          v-if="user.logged_in"
          type="button"
          class="side-item"
          :class="{ active: onlyFollowing }"
          @click="selectFeed(true)"
        >
          <span class="side-icon" aria-hidden="true">☆</span>
          Siguiendo
        </button>

        <hr class="side-divider" />

        <RouterLink v-if="user.logged_in" to="/studio" class="side-item">
          <span class="side-icon" aria-hidden="true">▦</span>
          Studio
        </RouterLink>
        <p v-else class="side-hint">
          Inicia sesión desde la cabecera para seguir canales y subir vídeos.
        </p>
      </nav>

      <main class="home-content" aria-labelledby="library-title">
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
          <p>Sube el primer video desde Studio para empezar a emitir.</p>
          <RouterLink v-if="user.logged_in" to="/studio" class="empty-action">
            Ir a Studio
          </RouterLink>
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
      </main>
    </div>
  </div>
</template>

<style scoped>
.home-page {
  min-height: 100vh;
  background: #0f0f1a;
  color: #e0e0e0;
}

.app-shell {
  display: grid;
  grid-template-columns: 15rem minmax(0, 1fr);
  align-items: start;
}

.app-sidebar {
  position: sticky;
  top: 0;
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
  padding: 1rem 0.75rem;
}

.side-item {
  display: flex;
  align-items: center;
  gap: 0.9rem;
  width: 100%;
  padding: 0.6rem 0.85rem;
  border: 0;
  border-radius: 0.6rem;
  background: transparent;
  color: #cfcddd;
  cursor: pointer;
  font: inherit;
  font-size: 0.9rem;
  font-weight: 650;
  text-align: left;
  transition: background 160ms ease, color 160ms ease;
}

.side-item:hover { background: #1e1e2e; color: #fff; }

.side-item.active {
  background: #26243d;
  color: #dcd9ff;
  font-weight: 750;
}

.side-icon {
  flex: 0 0 1.25rem;
  color: #8882ff;
  font-size: 1.05rem;
  text-align: center;
}

.side-divider {
  margin: 0.85rem 0.35rem;
  border: 0;
  border-top: 1px solid #26253a;
}

.side-hint {
  padding: 0 0.85rem;
  color: #777587;
  font-size: 0.78rem;
  line-height: 1.55;
}

.home-content {
  min-width: 0;
  padding: 1rem clamp(1rem, 3vw, 2.5rem) 4rem;
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

.result-summary,
.feed-hint {
  margin-top: 0.38rem;
  color: #858294;
  font-size: 0.82rem;
}

.feed-tabs {
  position: sticky;
  top: 0;
  z-index: 2;
  display: flex;
  gap: 0.5rem;
  overflow-x: auto;
  margin-bottom: 1.35rem;
  padding: 0.35rem 0 0.85rem;
  background: linear-gradient(#0f0f1a 72%, rgba(15, 15, 26, 0));
  scrollbar-width: none;
}

.feed-tabs::-webkit-scrollbar { display: none; }

.feed-tab {
  flex: 0 0 auto;
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
  grid-template-columns: repeat(auto-fill, minmax(17rem, 1fr));
  gap: 2.25rem 1rem;
}

.video-card {
  display: block;
  min-width: 0;
  color: inherit;
}

.video-cover {
  position: relative;
  display: grid;
  aspect-ratio: 16 / 9;
  place-items: center;
  overflow: hidden;
  border-radius: 0.75rem;
  background: linear-gradient(135deg, #26244b, #12121e 68%);
  transition: border-radius 180ms ease;
}

.video-card:hover .video-cover { border-radius: 0.25rem; }

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

.video-info { padding: 0.8rem 0.15rem 0; }

.video-info h3 {
  display: -webkit-box;
  overflow: hidden;
  margin: 0;
  color: #e8e7f0;
  font-size: 0.98rem;
  font-weight: 680;
  line-height: 1.35;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
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

.empty-state button,
.empty-action {
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

@media (max-width: 1050px) {
  .app-shell { grid-template-columns: 1fr; }
  .app-sidebar {
    position: static;
    flex-direction: row;
    align-items: center;
    overflow-x: auto;
    border-bottom: 1px solid #26253a;
  }
  .side-item { width: auto; white-space: nowrap; }
  .side-divider,
  .side-hint { display: none; }
}

@media (prefers-reduced-motion: reduce) {
  .video-cover { transition: none; }
}
</style>
