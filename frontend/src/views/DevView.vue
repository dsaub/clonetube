<script setup lang="ts">
import { ref } from 'vue'
import UploadModal from '@/components/UploadModal.vue'

const showModal = ref(false)

function openModal() { showModal.value = true }
function closeModal() { showModal.value = false }
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
        <h2>Multipart Upload a S3</h2>
        <p>
          Prueba el flujo completo de subida de videos con <strong>multipart upload</strong>
          usando presigned URLs de S3.
        </p>
        <button class="btn-upload" @click="openModal">
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
            <span>Fragmentar &amp; firmar</span>
          </div>
          <div class="flow-arrow">→</div>
          <div class="flow-step">
            <span class="step-num">3</span>
            <span>Subir a S3</span>
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
            <span class="desc">Inicia el multipart upload y devuelve un UUID como key</span>
          </div>
        </div>
        <div class="endpoint-card">
          <code class="method get">GET</code>
          <div class="endpoint-info">
            <code class="path">/api/v1/video/sign-chunk</code>
            <span class="desc">Genera una presigned URL para cada fragmento</span>
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
        <li>Credenciales AWS configuradas en variables de entorno</li>
        <li>Bucket S3 con permisos de multipart upload</li>
      </ul>
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
  background: #6c63ff;
  color: #fff;
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
  background: #6c63ff;
  color: #fff;
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
  background: #6c63ff;
  color: #fff;
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

.method.post { background: rgba(73, 204, 144, 0.15); color: #49cc90; }
.method.get  { background: rgba(97, 175, 254, 0.15); color: #61affe; }

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
  color: #888;
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
</style>
