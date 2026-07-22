<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getAccessibleVideoMetadataByKey, getStreamUrl, type VideoMetadata } from '@/api/video'
import VideoPlayer from '@/components/VideoPlayer.vue'

const route = useRoute()

const videoKey = ref('')
const streamUrl = ref('')
const title = ref('')
const metadata = ref<VideoMetadata | null>(null)
const loading = ref(true)
const error = ref('')

function getKeyFromRoute(): string {
  return (route.query.key as string) || ''
}

async function loadVideo() {
  const key = getKeyFromRoute()
  if (!key) {
    error.value = 'No se especificó ningún video (parámetro ?key=)'
    loading.value = false
    return
  }

  videoKey.value = key
  title.value = (route.query.title as string) || key.split('/').pop() || 'Video'
  metadata.value = null
  loading.value = true
  error.value = ''

  try {
    const token = localStorage.getItem('token') || undefined
    const [url, details] = await Promise.all([
      getStreamUrl(key, token),
      getAccessibleVideoMetadataByKey(key, token).catch(() => null),
    ])
    streamUrl.value = url
    metadata.value = details
    if (details?.title) title.value = details.title
    loading.value = false
  } catch (e) {
    error.value = e instanceof Error ? e.message : 'Error desconocido'
    loading.value = false
  }
}

watch(() => route.query.key, () => {
  loadVideo()
})

onMounted(() => {
  loadVideo()
})
</script>

<template>
  <div class="watch-page">
    <!-- Header -->
    <header class="watch-header">
      <RouterLink to="/" class="back-link">← Inicio</RouterLink>
      <h1 v-if="videoKey" class="video-title">
        {{ title }}
      </h1>
    </header>

    <!-- Player -->
    <main class="player-container">
      <div v-if="loading" class="state-box">
        <div class="spinner"></div>
        <p>Cargando video…</p>
      </div>

      <div v-else-if="error" class="state-box error">
        <p>{{ error }}</p>
        <RouterLink to="/" class="back-link">← Ir a la lista de videos</RouterLink>
      </div>

      <template v-else>
        <VideoPlayer :src="streamUrl" />
        <section class="video-details" aria-labelledby="watch-video-title">
          <div class="video-heading">
            <div>
              <span class="video-label">AHORA EN EMISIÓN</span>
              <h2 id="watch-video-title">{{ title }}</h2>
            </div>
            <div v-if="metadata?.author" class="author-chip">
              <span class="author-avatar" aria-hidden="true">
                {{ metadata.author.displayName.charAt(0).toUpperCase() }}
              </span>
              <span>
                <strong>{{ metadata.author.displayName }}</strong>
                <small>@{{ metadata.author.username }}</small>
              </span>
            </div>
          </div>
          <p v-if="metadata?.description" class="video-description">
            {{ metadata.description }}
          </p>
        </section>
      </template>
    </main>
  </div>
</template>

<style scoped>
.watch-page {
  min-height: 100vh;
  background: #0f0f1a;
  color: #e0e0e0;
  font-family: 'Inter', system-ui, -apple-system, sans-serif;
}

.watch-header {
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: 1rem 1.5rem;
  background: #1a1a2e;
  border-bottom: 1px solid #2a2a4a;
}

.back-link {
  color: #6c63ff;
  text-decoration: none;
  font-size: 0.9rem;
}

.back-link:hover { color: #8a84ff; }

.video-title {
  font-size: 1rem;
  font-weight: 500;
  color: #ccc;
  margin: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.player-container {
  max-width: 960px;
  margin: 1.5rem auto;
  padding: 0 1rem;
}

.video-details {
  margin-top: 1rem;
  padding: clamp(1rem, 3vw, 1.4rem);
  border: 1px solid #2a2a4a;
  border-radius: 12px;
  background: #181827;
}

.video-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1.25rem;
}

.video-label {
  color: #8882ff;
  font-size: 0.66rem;
  font-weight: 800;
  letter-spacing: 0.14em;
}

.video-heading h2 {
  margin-top: 0.35rem;
  color: #f3f2fa;
  font-size: clamp(1.25rem, 3vw, 1.75rem);
  line-height: 1.2;
}

.author-chip {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 0.65rem;
  color: #dfdeea;
}

.author-avatar {
  display: grid;
  width: 2.5rem;
  height: 2.5rem;
  place-items: center;
  border: 1px solid #514d7c;
  border-radius: 50%;
  background: #2a2848;
  color: #bdb9ff;
  font-weight: 800;
}

.author-chip strong,
.author-chip small {
  display: block;
}

.author-chip strong { font-size: 0.88rem; }
.author-chip small { margin-top: 0.12rem; color: #817f91; font-size: 0.72rem; }

.video-description {
  margin-top: 1rem;
  padding-top: 1rem;
  border-top: 1px solid #29283d;
  color: #aaa8b8;
  font-size: 0.92rem;
  line-height: 1.7;
  white-space: pre-wrap;
}

.state-box {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 1rem;
  padding: 3rem;
  background: #1a1a2e;
  border-radius: 12px;
  border: 1px solid #2a2a4a;
  color: #aaa;
}

.state-box.error { color: #ff6b6b; }

.spinner {
  width: 36px;
  height: 36px;
  border: 3px solid rgba(108, 99, 255, 0.2);
  border-top-color: #6c63ff;
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}

@keyframes spin { to { transform: rotate(360deg); } }

@media (max-width: 600px) {
  .video-heading { align-items: flex-start; flex-direction: column; }
}
</style>
