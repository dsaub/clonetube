<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Header from '@/components/Header.vue'
import FollowButton from '@/components/FollowButton.vue'
import DonationModal from '@/components/DonationModal.vue'
import {
  CHANNEL_PAGE_SIZE,
  channelPath,
  getChannel,
  listChannelVideos,
  usernameFromHandle,
  type Channel,
  type ChannelVideo,
} from '@/api/channel'
import { useUserStore } from '@/stores/user'

/** Números de página mostrados alrededor del actual. */
const PAGE_WINDOW = 5

const route = useRoute()
const router = useRouter()
const user = useUserStore()

const channel = ref<Channel | null>(null)
const videos = ref<ChannelVideo[]>([])
const page = ref(1)
const pages = ref(1)
const total = ref(0)
const loading = ref(true)
const error = ref('')
const donateOpen = ref(false)

const username = computed(() => usernameFromHandle(String(route.params.handle ?? '')))
const isOwnChannel = computed(() => Boolean(channel.value) && user.username === username.value)
const initial = computed(() => (channel.value?.fullName || username.value).charAt(0).toUpperCase())
const videoCountLabel = computed(
  () => `${total.value} ${total.value === 1 ? 'vídeo' : 'vídeos'}`,
)
const rangeLabel = computed(() => {
  if (total.value === 0) return ''
  const first = (page.value - 1) * CHANNEL_PAGE_SIZE + 1
  const last = Math.min(first + videos.value.length - 1, total.value)
  return `Mostrando ${first}–${last} de ${total.value}`
})

/** Ventana de páginas centrada en la actual, sin salirse de los extremos. */
const visiblePages = computed(() => {
  const start = Math.max(1, Math.min(page.value - Math.floor(PAGE_WINDOW / 2), pages.value - PAGE_WINDOW + 1))
  const end = Math.min(pages.value, start + PAGE_WINDOW - 1)
  return Array.from({ length: end - start + 1 }, (_, index) => start + index)
})

function requestedPage(): number {
  const raw = Number(route.query.page)
  return Number.isInteger(raw) && raw > 0 ? raw : 1
}

async function load() {
  const name = username.value
  if (!name) {
    error.value = 'No se indicó ningún canal.'
    loading.value = false
    return
  }

  loading.value = true
  error.value = ''
  const requested = requestedPage()
  const token = user.token?.access_token

  try {
    const [profile, videoPage] = await Promise.all([
      getChannel(name, token),
      listChannelVideos(name, requested, token),
    ])
    channel.value = profile
    videos.value = videoPage.videos
    page.value = videoPage.page
    pages.value = videoPage.pages
    total.value = videoPage.total
  } catch (cause: unknown) {
    channel.value = null
    videos.value = []
    error.value = cause instanceof Error ? cause.message : 'No se pudo cargar el canal.'
  } finally {
    loading.value = false
  }
}

/** La página viaja en la URL para que se pueda compartir y volver atrás. */
function goToPage(target: number) {
  if (target < 1 || target > pages.value || target === page.value) return
  void router.push({ query: { ...route.query, page: String(target) } })
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

function visibilityLabel(video: ChannelVideo): string {
  return video.visibility === 'private' ? 'PRIVADO' : 'OCULTO'
}

// Recarga al cambiar de canal o de página. La arroba forma parte del formato
// canónico `/channel/@usuario`: si falta, se corrige la URL antes de pedir nada.
watch(
  () => [String(route.params.handle ?? ''), requestedPage()] as const,
  ([handle]) => {
    if (handle && !handle.startsWith('@')) {
      void router.replace({ path: channelPath(handle), query: route.query })
      return
    }
    void load()
  },
  { immediate: true },
)
</script>

<template>
  <div class="channel-page">
    <Header />

    <main class="channel-content">
      <div v-if="loading && !channel" class="state-box">
        <div class="spinner"></div>
        <p>Cargando canal…</p>
      </div>

      <div v-else-if="error" class="state-box error" role="alert">
        <h2>No se pudo abrir el canal</h2>
        <p>{{ error }}</p>
        <RouterLink to="/" class="state-action">← Volver al inicio</RouterLink>
      </div>

      <template v-else-if="channel">
        <section class="channel-hero">
          <span class="channel-avatar" aria-hidden="true">{{ initial }}</span>
          <div class="channel-identity">
            <h1>{{ channel.fullName }}</h1>
            <p class="channel-handle">
              @{{ channel.username }} · {{ videoCountLabel }}
            </p>
            <div class="hero-actions">
              <FollowButton :username="channel.username" />
              <button
                v-if="user.logged_in && !isOwnChannel"
                type="button"
                class="donate-button"
                @click="donateOpen = true"
              >
                ◆ Donar
              </button>
            </div>
          </div>
        </section>

        <section class="channel-videos" aria-labelledby="channel-videos-title">
          <div class="section-heading">
            <div>
              <span class="section-kicker">EMISIONES DEL CANAL</span>
              <h2 id="channel-videos-title">Vídeos</h2>
              <p v-if="rangeLabel" class="range-label" aria-live="polite">{{ rangeLabel }}</p>
            </div>
          </div>

          <div v-if="loading" class="video-grid" aria-label="Cargando vídeos">
            <article v-for="item in 4" :key="item" class="video-card skeleton-card">
              <div class="video-cover skeleton"></div>
              <div class="skeleton-line wide"></div>
              <div class="skeleton-line"></div>
            </article>
          </div>

          <div v-else-if="videos.length === 0" class="empty-state">
            <span aria-hidden="true">□</span>
            <h3>Este canal aún no tiene vídeos</h3>
            <p v-if="isOwnChannel">Sube tu primer vídeo desde Studio para estrenar el canal.</p>
            <p v-else>Vuelve más adelante para ver sus emisiones.</p>
            <RouterLink v-if="isOwnChannel" to="/studio" class="state-action">Ir a Studio</RouterLink>
          </div>

          <div v-else class="video-grid">
            <RouterLink
              v-for="video in videos"
              :key="video.id"
              class="video-card"
              :to="{ name: 'watch', query: { key: video.key, title: video.title } }"
              :aria-label="`Reproducir ${video.title}`"
            >
              <div class="video-cover">
                <span class="video-noise"></span>
                <span class="card-play">▶</span>
                <span v-if="video.visibility !== 'public'" class="visibility-pill">
                  {{ visibilityLabel(video) }}
                </span>
              </div>
              <div class="video-info">
                <h3>{{ video.title }}</h3>
                <p>{{ formatDate(video.createdAt) }}</p>
              </div>
            </RouterLink>
          </div>

          <nav v-if="pages > 1" class="pagination" aria-label="Paginación de vídeos">
            <button
              type="button"
              class="page-button"
              :disabled="page === 1"
              @click="goToPage(page - 1)"
            >
              ← Anterior
            </button>

            <ol class="page-list">
              <li v-for="number in visiblePages" :key="number">
                <button
                  type="button"
                  class="page-number"
                  :class="{ current: number === page }"
                  :aria-current="number === page ? 'page' : undefined"
                  :aria-label="`Página ${number}`"
                  @click="goToPage(number)"
                >
                  {{ number }}
                </button>
              </li>
            </ol>

            <button
              type="button"
              class="page-button"
              :disabled="page === pages"
              @click="goToPage(page + 1)"
            >
              Siguiente →
            </button>

            <p class="page-status" aria-live="polite">Página {{ page }} de {{ pages }}</p>
          </nav>
        </section>
      </template>
    </main>

    <DonationModal
      v-if="donateOpen"
      :recipient-username="username"
      @close="donateOpen = false"
    />
  </div>
</template>

<style scoped>
.channel-page {
  min-height: 100vh;
  background: #0f0f1a;
  color: #e0e0e0;
}

.channel-content {
  max-width: 78rem;
  margin: 0 auto;
  padding: 1.5rem clamp(1rem, 3vw, 2.5rem) 4rem;
}

.channel-hero {
  display: flex;
  align-items: center;
  gap: clamp(1rem, 3vw, 1.75rem);
  padding: clamp(1.1rem, 3vw, 1.75rem);
  border: 1px solid #2a2a4a;
  border-radius: 1rem;
  background: linear-gradient(135deg, #1c1a33, #14141f 70%);
}

.channel-avatar {
  display: grid;
  flex: 0 0 auto;
  width: 5rem;
  height: 5rem;
  place-items: center;
  border: 1px solid #514d7c;
  border-radius: 50%;
  background: #2a2848;
  color: #bdb9ff;
  font-size: 2rem;
  font-weight: 800;
}

.channel-identity {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  min-width: 0;
}

.channel-identity h1 {
  color: #f3f2fa;
  font-size: clamp(1.5rem, 4vw, 2.15rem);
  letter-spacing: -0.03em;
  line-height: 1.15;
}

.channel-handle {
  color: #8f8d9f;
  font-size: 0.86rem;
}

.hero-actions {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  margin-top: 0.35rem;
}

.donate-button {
  padding: 0.55rem 0.95rem;
  border: 1px solid #6c63ff;
  border-radius: 0.55rem;
  background: #6c63ff;
  color: #fff;
  cursor: pointer;
  font: inherit;
  font-size: 0.85rem;
  font-weight: 750;
  white-space: nowrap;
  transition: background-color 160ms ease, transform 160ms ease;
}

.donate-button:hover {
  background: #7c75ff;
  transform: translateY(-1px);
}

.section-heading {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 1rem;
  margin: 2rem 0 1.35rem;
}

.section-kicker {
  color: #8882ff;
  font-size: 0.72rem;
  font-weight: 800;
  letter-spacing: 0.18em;
}

.section-heading h2 {
  margin-top: 0.35rem;
  color: #f0eff8;
  font-size: clamp(1.35rem, 3vw, 1.85rem);
  letter-spacing: -0.03em;
}

.range-label {
  margin-top: 0.38rem;
  color: #858294;
  font-size: 0.82rem;
}

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

.visibility-pill {
  position: absolute;
  top: 0.55rem;
  left: 0.55rem;
  padding: 0.2rem 0.4rem;
  border: 1px solid #514d7c;
  border-radius: 0.3rem;
  background: rgba(7, 7, 13, 0.78);
  color: #cfcbff;
  font-size: 0.62rem;
  font-weight: 800;
  letter-spacing: 0.08em;
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

.pagination {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  margin-top: 2.5rem;
}

.page-button,
.page-number {
  padding: 0.5rem 0.8rem;
  border: 1px solid #38364e;
  border-radius: 0.5rem;
  background: #181824;
  color: #b3b1c0;
  cursor: pointer;
  font: inherit;
  font-size: 0.82rem;
  font-weight: 700;
  transition: border-color 160ms ease, background 160ms ease, color 160ms ease;
}

.page-button:hover:not(:disabled),
.page-number:hover:not(.current) { border-color: #4b4772; color: #dcd9ff; }

.page-button:disabled { cursor: not-allowed; opacity: 0.45; }

.page-list {
  display: flex;
  gap: 0.35rem;
  list-style: none;
}

.page-number.current {
  border-color: #7c75ff;
  background: #24223d;
  color: #dcd9ff;
  cursor: default;
}

.page-status {
  flex-basis: 100%;
  color: #777587;
  font-size: 0.78rem;
  text-align: center;
}

.empty-state {
  display: grid;
  min-height: 14rem;
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

.state-box {
  display: grid;
  place-items: center;
  gap: 0.85rem;
  padding: 3rem;
  border: 1px solid #2a2a4a;
  border-radius: 1rem;
  background: #1a1a2e;
  color: #aaa;
  text-align: center;
}

.state-box.error { color: #ff9aa4; }
.state-box h2 { color: #f0eff8; }

.state-action {
  margin-top: 0.35rem;
  padding: 0.55rem 0.75rem;
  border: 1px solid #4b4772;
  border-radius: 0.5rem;
  background: #24223d;
  color: #dcd9ff;
  font-size: 0.82rem;
  font-weight: 700;
}

.spinner {
  width: 36px;
  height: 36px;
  border: 3px solid rgba(108, 99, 255, 0.2);
  border-top-color: #6c63ff;
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}

@keyframes spin { to { transform: rotate(360deg); } }

.skeleton-card { pointer-events: none; }
.skeleton { background: #222131; }
.skeleton-line { width: 52%; height: 0.65rem; margin: 0.65rem 0.35rem 0; border-radius: 99px; background: #29283a; }
.skeleton-line.wide { width: 78%; margin-top: 0.85rem; }

@media (max-width: 640px) {
  .channel-hero { flex-direction: column; text-align: center; }
  .channel-identity { align-items: center; }
}

@media (prefers-reduced-motion: reduce) {
  .video-cover,
  .card-play { transition: none; }
  .spinner { animation: none; }
}
</style>
