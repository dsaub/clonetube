<script setup lang="ts">
import { ref, onMounted } from 'vue'
import UploadModal from '@/components/UploadModal.vue'
import { listVideos, type VideoListItem } from '@/api/video'

const showModal = ref(false)
const videos = ref<VideoListItem[]>([])
const loadingVideos = ref(false)
const videosError = ref('')

function openModal() { showModal.value = true }
function closeModal() { showModal.value = false; fetchVideos() }

async function fetchVideos() {
  loadingVideos.value = true
  videosError.value = ''
  try {
    videos.value = await listVideos()
  } catch (e) {
    videosError.value = e instanceof Error ? e.message : 'Error al cargar videos'
  } finally {
    loadingVideos.value = false
  }
}

function watchVideo(id: string) {
  window.open(`/watch?id=${encodeURIComponent(id)}`, '_blank')
}

function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
  return `${(bytes / (1024 * 1024 * 1024)).toFixed(2)} GB`
}

onMounted(() => {
  fetchVideos()
})
</script>

<template>
  <main class="dev-page">
    <header class="dev-header">
      <div class="header-left">
        <span class="badge">DEV</span>
        <h1>🧪 Caja de desarrollo</h1>
      </div>
      <a href="/" class="back-link">← Volver al inicio</a>
    </header>

    <section class="hero">
      <div class="hero-content">
        <h2>Subida de videos a Clonetube</h2>
        <p>
          Prueba el flujo completo de subida de videos: el archivo se envía
          <strong>por fragmentos</strong> y Clonetube los ensambla al terminar.
        </p>
        <button type="button" class="btn-upload" @click="openModal">
          <span class="btn-icon">📤</span>
          Subir video
        </button>
      </div>
      <div class="hero-illustration">
        <div class="flow-diagram">
          <div class="flow-step">
            <span class="step-num">1</span>
            <span>Seleccionar archivo</span>
          </div>
          <div class="flow-arrow">→</div>
          <div class="flow-step">
            <span class="step-num">2</span>
            <span>Fragmentar</span>
          </div>
          <div class="flow-arrow">→</div>
          <div class="flow-step">
            <span class="step-num">3</span>
            <span>Subir a Clonetube</span>
          </div>
          <div class="flow-arrow">→</div>
          <div class="flow-step">
            <span class="step-num">4</span>
            <span>Completar</span>
          </div>
        </div>
      </div>
    </section>

    <section class="endpoints">
      <h3>Endpoints utilizados</h3>
      <div class="endpoint-list">
        <div class="endpoint-card">
          <code class="method post">POST</code>
          <div class="endpoint-info">
            <code class="path">/api/v1/video/start-multipart</code>
            <span class="desc">Reserva la subida y devuelve un identificador único</span>
          </div>
        </div>
        <div class="endpoint-card">
          <code class="method put">PUT</code>
          <div class="endpoint-info">
            <code class="path">/api/v1/video/upload-chunk</code>
            <span class="desc">Recibe cada fragmento y lo guarda en Clonetube</span>
          </div>
        </div>
        <div class="endpoint-card">
          <code class="method post">POST</code>
          <div class="endpoint-info">
            <code class="path">/api/v1/video/complete-multipart</code>
            <span class="desc">Ensambla los fragmentos y completa la subida</span>
          </div>
        </div>
      </div>
    </section>

    <section class="info">
      <h3>⚠️ Requisitos</h3>
      <ul>
        <li>El backend debe estar corriendo en <code>http://localhost:8000</code></li>
        <li>El almacenamiento de Clonetube configurado en variables de entorno</li>
      </ul>
    </section>

    <!-- Listado de videos -->
    <section class="video-list">
      <h3>🎬 Videos subidos</h3>

      <div v-if="loadingVideos" class="list-loading">Cargando…</div>
      <div v-else-if="videosError" class="list-error">{{ videosError }}</div>
      <div v-else-if="videos.length === 0" class="list-empty">
        No hay videos subidos todavía.
      </div>

      <div v-else class="video-grid">
        <div
          v-for="video in videos"
          :key="video.key"
          class="video-card"
          @click="watchVideo(video.id)"
        >
          <div class="card-thumb">
            <div class="thumb-placeholder">▶</div>
          </div>
          <div class="card-info">
            <p class="card-name">{{ video.original_filename }}</p>
            <p class="card-meta">{{ formatSize(video.size) }}</p>
          </div>
        </div>
      </div>
    </section>

    <!-- Modal -->
    <UploadModal v-if="showModal" @close="closeModal" />
  </main>
</template>

<style scoped>
.dev-page {
  max-width: 900px;
  margin: 0 auto;
  padding: 2rem 1.5rem;
  min-height: 100vh;
  background: #0f0f1a;
  color: #e0e0e0;
  font-family: 'Inter', system-ui, -apple-system, sans-serif;
}

/* ─── Header ────────────────────────────────────── */
.dev-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 2.5rem;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.badge {
  background: #554bd8;
  color: #ffffff;
  font-size: 0.7rem;
  font-weight: 700;
  padding: 0.2rem 0.6rem;
  border-radius: 6px;
  letter-spacing: 0.05em;
}

.dev-header h1 {
  margin: 0;
  font-size: 1.4rem;
  font-weight: 600;
}

.back-link {
  color: #6c63ff;
  text-decoration: none;
  font-size: 0.9rem;
  transition: color 0.2s;
}

.back-link:hover { color: #8a84ff; }

/* ─── Hero ──────────────────────────────────────── */
.hero {
  display: flex;
  align-items: center;
  gap: 2rem;
  background: linear-gradient(135deg, #1a1a2e 0%, #16213e 100%);
  border-radius: 16px;
  padding: 2rem;
  margin-bottom: 2rem;
  border: 1px solid #2a2a4a;
}

.hero-content { flex: 1; }

.hero-content h2 {
  margin: 0 0 0.75rem;
  font-size: 1.6rem;
  background: linear-gradient(135deg, #6c63ff, #48c6ef);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.hero-content p {
  color: #aaa;
  line-height: 1.6;
  margin: 0 0 1.5rem;
}

.btn-upload {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.85rem 2rem;
  background: #554bd8;
  color: #ffffff;
  border: none;
  border-radius: 12px;
  font-size: 1.05rem;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.2s, transform 0.1s;
}

.btn-upload:hover { background: #5a52e0; }
.btn-upload:active { transform: scale(0.97); }

.btn-icon { font-size: 1.2rem; }

/* ─── Diagrama de flujo ─────────────────────────── */
.hero-illustration { flex-shrink: 0; }

.flow-diagram {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  background: #0f0f1a;
  padding: 1rem 1.25rem;
  border-radius: 12px;
  border: 1px solid #2a2a4a;
}

.flow-step {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.25rem;
  font-size: 0.7rem;
  color: #aaa;
  text-align: center;
}

.step-num {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: #554bd8;
  color: #ffffff;
  font-weight: 700;
  font-size: 0.8rem;
}

.flow-arrow {
  color: #6c63ff;
  font-size: 1.2rem;
  font-weight: 700;
}

/* ─── Endpoints ─────────────────────────────────── */
.endpoints { margin-bottom: 2rem; }

.endpoints h3,
.info h3 {
  font-size: 1rem;
  font-weight: 600;
  margin: 0 0 0.75rem;
  color: #ccc;
}

.endpoint-list {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.endpoint-card {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  background: #1a1a2e;
  padding: 0.75rem 1rem;
  border-radius: 10px;
  border: 1px solid #2a2a4a;
}

.method {
  font-size: 0.7rem;
  font-weight: 700;
  padding: 0.2rem 0.5rem;
  border-radius: 4px;
  text-transform: uppercase;
  flex-shrink: 0;
}

.method.post { background: #17382b; color: #73e5b0; }
.method.get  { background: #19334d; color: #8bc8ff; }
.method.put  { background: #44301a; color: #ffc36f; }

.endpoint-info {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
}

.path {
  font-size: 0.82rem;
  color: #e0e0e0;
}

.desc {
  font-size: 0.78rem;
  color: #aaa8b7;
}

/* ─── Info ──────────────────────────────────────── */
.info ul {
  margin: 0;
  padding-left: 1.25rem;
}

.info li {
  font-size: 0.9rem;
  color: #aaa;
  line-height: 1.7;
}

.info code {
  background: #2a2a4a;
  padding: 0.1rem 0.4rem;
  border-radius: 4px;
  font-size: 0.82rem;
}

/* ─── Video list ────────────────────────────────── */
.video-list { margin-bottom: 2rem; }

.video-list h3 {
  font-size: 1rem;
  font-weight: 600;
  margin: 0 0 0.75rem;
  color: #ccc;
}

.list-loading,
.list-empty,
.list-error {
  padding: 2rem;
  text-align: center;
  background: #1a1a2e;
  border-radius: 10px;
  border: 1px solid #2a2a4a;
  color: #aaa8b7;
  font-size: 0.9rem;
}

.list-error { color: #ff6b6b; }

.video-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 1rem;
}

.video-card {
  background: #1a1a2e;
  border-radius: 10px;
  border: 1px solid #2a2a4a;
  overflow: hidden;
  cursor: pointer;
  transition: border-color 0.2s, transform 0.15s;
}

.video-card:hover {
  border-color: #6c63ff;
  transform: translateY(-2px);
}

.card-thumb {
  aspect-ratio: 16 / 9;
  background: #0f0f1a;
  display: flex;
  align-items: center;
  justify-content: center;
}

.thumb-placeholder {
  font-size: 2rem;
  color: #3a3a5a;
}

.card-info {
  padding: 0.6rem 0.75rem;
}

.card-name {
  font-size: 0.85rem;
  font-weight: 500;
  color: #e0e0e0;
  margin: 0 0 0.2rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-meta {
  font-size: 0.75rem;
  color: #aaa8b7;
  margin: 0;
}
</style>
