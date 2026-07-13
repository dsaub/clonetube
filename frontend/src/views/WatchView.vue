<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getStreamUrl } from '@/api/video'
import VideoPlayer from '@/components/VideoPlayer.vue'

const route = useRoute()

const videoKey = ref('')
const streamUrl = ref('')
const title = ref('')
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
  loading.value = true
  error.value = ''

  try {
    streamUrl.value = await getStreamUrl(key)
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
      <a href="/dev" class="back-link">← Dev</a>
      <h1 v-if="videoKey" class="video-title">
        {{ videoKey.split('/').pop() }}
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
        <a href="/dev" class="back-link">← Ir a la lista de videos</a>
      </div>

      <template v-else>
        <VideoPlayer :src="streamUrl" />
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
</style>
