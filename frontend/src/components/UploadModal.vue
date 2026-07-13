<script setup lang="ts">
import { ref } from 'vue'

const emit = defineEmits<{ close: [] }>()

// ─── Estado ────────────────────────────────────────────────────────
const selectedFile = ref<File | null>(null)
const step = ref<'idle' | 'uploading' | 'done' | 'error'>('idle')
const progress = ref(0)
const uploadId = ref('')
const videoKey = ref('')
const originalFilename = ref('')
const errorMsg = ref('')
const resultLocation = ref('')
const logs = ref<string[]>([])

const CHUNK_SIZE = 5 * 1024 * 1024 // 5 MB

// ─── API base ──────────────────────────────────────────────────────
const API = '/api/v1/video'

function log(msg: string) {
  logs.value.push(`[${new Date().toLocaleTimeString()}] ${msg}`)
}

function onFileSelected(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (file) {
    selectedFile.value = file
    step.value = 'idle'
    progress.value = 0
    logs.value = []
    errorMsg.value = ''
    resultLocation.value = ''
  }
}

async function startUpload() {
  const file = selectedFile.value
  if (!file) return

  step.value = 'uploading'
  progress.value = 0
  errorMsg.value = ''

  try {
    // ── 1. Iniciar multipart upload ──────────────────────────────
    log('Iniciando multipart upload…')
    const params = new URLSearchParams({ original_filename: file.name })
    const startRes = await fetch(`${API}/start-multipart?${params}`, { method: 'POST' })
    if (!startRes.ok) throw new Error(`Error al iniciar: ${await startRes.text()}`)
    const startData = await startRes.json()
    uploadId.value = startData.uploadId
    videoKey.value = startData.key
    originalFilename.value = startData.original_filename
    log(`Upload iniciado — ID: ${uploadId.value.slice(0, 12)}…`)
    log(`Key S3: ${videoKey.value}`)

    // ── 2. Dividir en fragmentos y subir ─────────────────────────
    const totalChunks = Math.ceil(file.size / CHUNK_SIZE)
    log(`Archivo: ${(file.size / 1024 / 1024).toFixed(2)} MB — ${totalChunks} fragmento(s)`)

    const parts: { PartNumber: number; ETag: string }[] = []

    for (let i = 0; i < totalChunks; i++) {
      const chunkNumber = i + 1
      const start = i * CHUNK_SIZE
      const end = Math.min(start + CHUNK_SIZE, file.size)
      const chunk = new Blob([file.slice(start, end)])

      // ── 2a. Obtener URL prefirmada ─────────────────
      log(`Fragmento ${chunkNumber}/${totalChunks}: obteniendo URL…`)
      const signParams = new URLSearchParams({
        filename: videoKey.value,
        upload_id: uploadId.value,
        chunk_number: String(chunkNumber),
      })
      const signRes = await fetch(`${API}/sign-chunk?${signParams}`)
      if (!signRes.ok) throw new Error(`Error al firmar fragmento ${chunkNumber}: ${await signRes.text()}`)
      const { url: presignedUrl } = await signRes.json()

      // ── 2b. Subir fragmento a S3 ───────────────────
      log(`Fragmento ${chunkNumber}/${totalChunks}: subiendo…`)
      const uploadRes = await fetch(presignedUrl, {
        method: 'PUT',
        body: chunk,
      })
      if (!uploadRes.ok) throw new Error(`Error al subir fragmento ${chunkNumber}: ${await uploadRes.text()}`)

      const etag = uploadRes.headers.get('ETag') ?? ''
      parts.push({ PartNumber: chunkNumber, ETag: etag })
      progress.value = Math.round((chunkNumber / totalChunks) * 100)
      log(`Fragmento ${chunkNumber}/${totalChunks}: OK (ETag: ${etag.slice(0, 12)}…)`)
    }

    // ── 3. Completar multipart upload ────────────────────────────
    log('Completando multipart upload…')
    const completeRes = await fetch(`${API}/complete-multipart`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        filename: videoKey.value,
        uploadId: uploadId.value,
        parts,
      }),
    })
    if (!completeRes.ok) throw new Error(`Error al completar: ${await completeRes.text()}`)
    const completeData = await completeRes.json()
    resultLocation.value = completeData.location ?? '—'
    log('¡Carga completada con éxito!')
    log(`Location: ${resultLocation.value}`)
    step.value = 'done'
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : 'Error desconocido'
    errorMsg.value = msg
    log(`❌ Error: ${msg}`)
    step.value = 'error'
  }
}

function reset() {
  selectedFile.value = null
  step.value = 'idle'
  progress.value = 0
  uploadId.value = ''
  videoKey.value = ''
  originalFilename.value = ''
  errorMsg.value = ''
  resultLocation.value = ''
  logs.value = []
}
</script>

<template>
  <div class="modal-overlay" @click.self="emit('close')">
    <div class="modal">
      <header class="modal-header">
        <h2>📤 Subir video</h2>
        <button class="close-btn" @click="emit('close')" aria-label="Cerrar">&times;</button>
      </header>

      <div class="modal-body">
        <!-- Selección de archivo -->
        <div v-if="step === 'idle'" class="file-selector">
          <label class="drop-zone" :class="{ 'has-file': selectedFile }">
            <input type="file" accept="video/*" @change="onFileSelected" />
            <template v-if="selectedFile">
              <span class="file-icon">🎬</span>
              <span class="file-name">{{ selectedFile.name }}</span>
              <span class="file-size">({{ (selectedFile.size / 1024 / 1024).toFixed(2) }} MB)</span>
            </template>
            <template v-else>
              <span class="drop-icon">📁</span>
              <span>Haz clic para seleccionar un video</span>
              <span class="drop-hint">o arrastra y suelta aquí</span>
            </template>
          </label>
          <button
            class="btn primary"
            :disabled="!selectedFile"
            @click="startUpload"
          >
            🚀 Subir a S3
          </button>
        </div>

        <!-- Barra de progreso -->
        <div v-if="step === 'uploading'" class="uploading">
          <div class="progress-bar">
            <div class="progress-fill" :style="{ width: progress + '%' }"></div>
            <span class="progress-text">{{ progress }}%</span>
          </div>
          <p class="file-label">
            Subiendo <strong>{{ originalFilename }}</strong>
          </p>
          <div class="logs">
            <p v-for="(line, i) in logs" :key="i" class="log-line">{{ line }}</p>
          </div>
        </div>

        <!-- Éxito -->
        <div v-if="step === 'done'" class="result success">
          <div class="result-icon">✅</div>
          <h3>¡Video subido con éxito!</h3>
          <div class="result-details">
            <p><strong>Nombre original:</strong> {{ originalFilename }}</p>
            <p><strong>Key S3:</strong> <code>{{ videoKey }}</code></p>
            <p><strong>Location:</strong> <code class="loc">{{ resultLocation }}</code></p>
          </div>
          <div class="logs">
            <p v-for="(line, i) in logs" :key="i" class="log-line">{{ line }}</p>
          </div>
          <button class="btn primary" @click="reset">Subir otro</button>
        </div>

        <!-- Error -->
        <div v-if="step === 'error'" class="result error">
          <div class="result-icon">❌</div>
          <h3>Error en la subida</h3>
          <p class="error-msg">{{ errorMsg }}</p>
          <div class="logs">
            <p v-for="(line, i) in logs" :key="i" class="log-line">{{ line }}</p>
          </div>
          <button class="btn" @click="reset">Intentar de nuevo</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* ─── Overlay ───────────────────────────────────── */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  backdrop-filter: blur(4px);
}

.modal {
  background: #1a1a2e;
  border-radius: 16px;
  width: min(640px, 94vw);
  max-height: 90vh;
  display: flex;
  flex-direction: column;
  box-shadow: 0 24px 48px rgba(0, 0, 0, 0.5);
  color: #e0e0e0;
}

/* ─── Header ────────────────────────────────────── */
.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 1.25rem 1.5rem;
  border-bottom: 1px solid #2a2a4a;
}

.modal-header h2 {
  margin: 0;
  font-size: 1.25rem;
  font-weight: 600;
}

.close-btn {
  background: none;
  border: none;
  color: #888;
  font-size: 1.5rem;
  cursor: pointer;
  padding: 0.25rem 0.5rem;
  border-radius: 8px;
  transition: background 0.2s, color 0.2s;
}

.close-btn:hover { background: #2a2a4a; color: #fff; }

/* ─── Body ──────────────────────────────────────── */
.modal-body { padding: 1.5rem; overflow-y: auto; }

/* ─── Drop zone ─────────────────────────────────── */
.drop-zone {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.5rem;
  padding: 2.5rem 1.5rem;
  border: 2px dashed #3a3a5a;
  border-radius: 12px;
  cursor: pointer;
  transition: border-color 0.2s, background 0.2s;
  text-align: center;
}

.drop-zone:hover { border-color: #6c63ff; background: rgba(108, 99, 255, 0.06); }
.drop-zone.has-file { border-color: #6c63ff; border-style: solid; background: rgba(108, 99, 255, 0.08); }

.drop-zone input[type="file"] { display: none; }

.drop-icon, .file-icon { font-size: 2.5rem; }
.file-name { font-weight: 600; font-size: 1.05rem; }
.file-size { color: #888; font-size: 0.9rem; }
.drop-hint { color: #666; font-size: 0.85rem; }

/* ─── Botones ───────────────────────────────────── */
.btn {
  display: block;
  width: 100%;
  margin-top: 1rem;
  padding: 0.75rem 1.5rem;
  border: none;
  border-radius: 10px;
  font-size: 1rem;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.2s, transform 0.1s;
  background: #2a2a4a;
  color: #e0e0e0;
}

.btn:active { transform: scale(0.98); }
.btn.primary { background: #6c63ff; color: #fff; }
.btn.primary:hover { background: #5a52e0; }
.btn.primary:disabled { background: #3a3a5a; color: #666; cursor: not-allowed; }

/* ─── Progreso ──────────────────────────────────── */
.progress-bar {
  position: relative;
  width: 100%;
  height: 28px;
  background: #2a2a4a;
  border-radius: 14px;
  overflow: hidden;
  margin-bottom: 0.75rem;
}

.progress-fill {
  height: 100%;
  background: linear-gradient(90deg, #6c63ff, #48c6ef);
  border-radius: 14px;
  transition: width 0.3s ease;
}

.progress-text {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 0.8rem;
  font-weight: 700;
  color: #fff;
}

.file-label { text-align: center; color: #aaa; margin-bottom: 0.5rem; }

/* ─── Logs ──────────────────────────────────────── */
.logs {
  background: #0f0f1a;
  border-radius: 10px;
  padding: 0.75rem;
  max-height: 200px;
  overflow-y: auto;
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 0.78rem;
  line-height: 1.5;
  margin: 0.75rem 0;
}

.log-line { margin: 0; color: #8a8aaa; }
.log-line:first-child { color: #c0c0e0; }

/* ─── Resultado ─────────────────────────────────── */
.result { text-align: center; }
.result-icon { font-size: 3rem; margin-bottom: 0.5rem; }
.result h3 { margin: 0 0 0.75rem; }
.result-details { text-align: left; background: #0f0f1a; border-radius: 10px; padding: 0.75rem 1rem; margin-bottom: 0.75rem; }
.result-details p { margin: 0.35rem 0; font-size: 0.9rem; }
.result-details code {
  background: #2a2a4a;
  padding: 0.1rem 0.4rem;
  border-radius: 4px;
  font-size: 0.82rem;
  word-break: break-all;
}
.result-details .loc { font-size: 0.75rem; }
.error-msg { color: #ff6b6b; font-weight: 500; }
</style>
