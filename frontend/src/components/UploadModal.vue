<script setup lang="ts">
import { computed, ref } from 'vue'
import TvModalShell from '@/components/TvModalShell.vue'
import {
  cancelMultipart,
  completeMultipart,
  startMultipart,
  updateVideoMetadataByKey,
  uploadChunk,
  type MultipartPart,
} from '@/api/video'

const emit = defineEmits<{
  close: []
  uploaded: [key: string]
}>()

// ─── Estado ────────────────────────────────────────────────────────
const selectedFile = ref<File | null>(null)
const videoTitle = ref('')
const videoDescription = ref('')
const step = ref<'idle' | 'uploading' | 'done' | 'error'>('idle')
const uploadId = ref('')
const videoKey = ref('')
const originalFilename = ref('')
const errorMsg = ref('')
const metadataWarning = ref('')
const statusMessage = ref('')
const sentBytes = ref(0)
const startedAt = ref(0)

const CHUNK_SIZE = 5 * 1024 * 1024 // 5 MB
const LOG_PREFIX = '[Clonetube][subida]'

const canStartUpload = computed(() => Boolean(selectedFile.value && videoTitle.value.trim()))

const progress = computed(() => {
  if (!selectedFile.value?.size) return 0
  return Math.min(100, Math.round((sentBytes.value / selectedFile.value.size) * 100))
})

const sentSize = computed(() => formatSize(sentBytes.value))
const totalSize = computed(() => formatSize(selectedFile.value?.size ?? 0))
const uploadSpeed = computed(() => {
  if (!startedAt.value || !sentBytes.value) return 'Calculando…'
  const elapsedSeconds = Math.max((Date.now() - startedAt.value) / 1000, 0.1)
  return `${formatSize(sentBytes.value / elapsedSeconds)}/s`
})

function getAccessToken(): string | undefined {
  return localStorage.getItem('token') ?? undefined
}

function formatSize(bytes: number): string {
  if (bytes < 1024) return `${Math.round(bytes)} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} MB`
  return `${(bytes / 1024 / 1024 / 1024).toFixed(2)} GB`
}

// El detalle técnico de la subida va a la consola del navegador; la pantalla
// solo muestra mensajes en lenguaje llano.
function debugLog(message: string, details?: Record<string, unknown>) {
  if (details) console.debug(`${LOG_PREFIX} ${message}`, details)
  else console.debug(`${LOG_PREFIX} ${message}`)
}

function errorLog(message: string, error?: unknown) {
  if (error !== undefined) console.error(`${LOG_PREFIX} ${message}`, error)
  else console.error(`${LOG_PREFIX} ${message}`)
}

function friendlyErrorMessage(error: unknown): string {
  if (error instanceof TypeError) {
    return 'Parece que se perdió la conexión. Revísala e inténtalo de nuevo.'
  }

  const raw = error instanceof Error ? error.message : ''
  if (/\((401|403)\)/.test(raw)) {
    return 'Tu sesión ha caducado. Vuelve a iniciar sesión para subir el video.'
  }
  if (/\(413\)/.test(raw)) {
    return 'El video es demasiado grande para subirlo ahora mismo.'
  }
  return 'No hemos podido subir tu video. Vuelve a intentarlo en unos minutos.'
}

function titleFromFilename(filename: string): string {
  return filename.replace(/\.[^.]+$/, '').trim() || filename
}

function onFileSelected(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (file) {
    selectedFile.value = file
    videoTitle.value = titleFromFilename(file.name)
    videoDescription.value = ''
    step.value = 'idle'
    sentBytes.value = 0
    statusMessage.value = ''
    errorMsg.value = ''
    metadataWarning.value = ''
    debugLog('Archivo seleccionado', { name: file.name, size: file.size, type: file.type })
  }
}

async function startUpload() {
  const file = selectedFile.value
  if (!file) return

  step.value = 'uploading'
  sentBytes.value = 0
  startedAt.value = Date.now()
  errorMsg.value = ''
  metadataWarning.value = ''
  statusMessage.value = 'Preparando tu video…'
  const token = getAccessToken()

  try {
    // ── 1. Reservar la subida en Clonetube ───────────────────────
    debugLog('Preparando la subida…', { filename: file.name })
    const startData = await startMultipart(file.name, token)
    uploadId.value = startData.uploadId
    videoKey.value = startData.key
    originalFilename.value = startData.original_filename
    debugLog('Subida preparada en Clonetube', {
      uploadId: startData.uploadId,
      key: startData.key,
    })

    // ── 2. Dividir en fragmentos y subir ─────────────────────────
    const totalChunks = Math.ceil(file.size / CHUNK_SIZE)
    debugLog(`Archivo: ${formatSize(file.size)} — ${totalChunks} fragmento(s)`, {
      size: file.size,
      chunkSize: CHUNK_SIZE,
      totalChunks,
    })
    statusMessage.value = 'Subiendo tu video…'

    const parts: MultipartPart[] = []

    for (let i = 0; i < totalChunks; i++) {
      const chunkNumber = i + 1
      const start = i * CHUNK_SIZE
      const end = Math.min(start + CHUNK_SIZE, file.size)
      const chunk = new Blob([file.slice(start, end)])

      debugLog(`Fragmento ${chunkNumber}/${totalChunks}: emitiendo datos…`)
      const part = await uploadChunk(videoKey.value, uploadId.value, chunkNumber, chunk, token)
      parts.push(part)
      sentBytes.value = end
      debugLog(`Fragmento ${chunkNumber}/${totalChunks}: recibido`, { eTag: part.ETag })
    }

    // ── 3. Ensamblar el vídeo ────────────────────────────────────
    statusMessage.value = 'Casi listo, estamos guardando tu video…'
    debugLog('Finalizando la subida…')
    const completeData = await completeMultipart(videoKey.value, uploadId.value, parts, token)
    debugLog('Carga completada con éxito', { key: completeData.key })

    if (token) {
      try {
        debugLog('Guardando título y descripción…')
        await updateVideoMetadataByKey(
          completeData.key,
          videoTitle.value.trim(),
          videoDescription.value.trim(),
          token,
        )
        debugLog('Metadatos del video guardados')
      } catch (metadataError: unknown) {
        metadataWarning.value =
          'Tu video se subió, pero no pudimos guardar el título y la descripción. Puedes editarlos desde Clonetube Studio.'
        errorLog('No se pudieron guardar los metadatos del video', metadataError)
      }
    } else {
      metadataWarning.value =
        'Tu video se subió, pero necesitas iniciar sesión para guardar el título y la descripción.'
      debugLog('Sin sesión activa: no se guardan los metadatos del video')
    }

    statusMessage.value = ''
    step.value = 'done'
    emit('uploaded', completeData.key)
  } catch (err: unknown) {
    errorMsg.value = friendlyErrorMessage(err)
    errorLog('La subida ha fallado', err)
    statusMessage.value = ''
    step.value = 'error'

    if (uploadId.value && videoKey.value) {
      try {
        await cancelMultipart(videoKey.value, uploadId.value, token)
        debugLog('Carga incompleta cancelada en el servidor')
      } catch (cancelError: unknown) {
        errorLog('No se pudo limpiar la carga incompleta automáticamente', cancelError)
      }
    }
  }
}

function reset() {
  selectedFile.value = null
  videoTitle.value = ''
  videoDescription.value = ''
  step.value = 'idle'
  sentBytes.value = 0
  startedAt.value = 0
  uploadId.value = ''
  videoKey.value = ''
  originalFilename.value = ''
  errorMsg.value = ''
  metadataWarning.value = ''
  statusMessage.value = ''
}
</script>

<template>
  <TvModalShell
    labelledby="upload-modal-title"
    max-width="40rem"
    max-height="90vh"
    padding="0"
    :can-close="step !== 'uploading'"
    @close="emit('close')"
  >
    <div class="modal">
      <header class="modal-header">
        <h2 id="upload-modal-title">📤 Subir video</h2>
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
          <div class="metadata-fields">
            <div class="field">
              <label for="upload-video-title">Título</label>
              <input
                id="upload-video-title"
                v-model="videoTitle"
                type="text"
                maxlength="120"
                placeholder="Elige primero un archivo"
                :disabled="!selectedFile"
                required
              />
              <small>{{ videoTitle.length }}/120</small>
            </div>
            <div class="field">
              <label for="upload-video-description">Descripción</label>
              <textarea
                id="upload-video-description"
                v-model="videoDescription"
                maxlength="2000"
                rows="4"
                placeholder="Cuenta de qué trata el video (opcional)"
                :disabled="!selectedFile"
              ></textarea>
              <small>{{ videoDescription.length }}/2000</small>
            </div>
          </div>
          <button
            class="btn primary"
            :disabled="!canStartUpload"
            @click="startUpload"
          >
            🚀 Subir a Clonetube
          </button>
        </div>

        <!-- Barra de progreso -->
        <div v-if="step === 'uploading'" class="uploading">
          <div class="progress-bar">
            <div class="progress-fill" :style="{ width: progress + '%' }"></div>
            <span class="progress-text">{{ progress }}%</span>
          </div>
          <p class="file-label">
            Subiendo <strong>{{ videoTitle || originalFilename }}</strong>
          </p>
          <div class="data-transmission" aria-hidden="true">
            <div class="transmission-node source-node">
              <span class="node-icon">▣</span>
              <small>Archivo</small>
            </div>
            <div class="data-channel">
              <span v-for="packet in 6" :key="packet" class="data-packet"></span>
              <span class="channel-line"></span>
            </div>
            <div class="transmission-node cloud-node">
              <span class="node-icon">☁</span>
              <small>Clonetube</small>
            </div>
          </div>
          <p class="status-message" aria-live="polite">{{ statusMessage }}</p>
          <div class="transfer-stats">
            <span>{{ sentSize }} de {{ totalSize }}</span>
            <span>{{ uploadSpeed }}</span>
          </div>
          <p class="upload-hint">No cierres esta ventana hasta que termine.</p>
        </div>

        <!-- Éxito -->
        <div v-if="step === 'done'" class="result success">
          <div class="result-icon">✅</div>
          <h3>¡Video subido con éxito!</h3>
          <p v-if="metadataWarning" class="metadata-warning" role="status">
            {{ metadataWarning }}
          </p>
          <div class="result-details">
            <p><strong>Título:</strong> {{ videoTitle }}</p>
            <p v-if="videoDescription"><strong>Descripción:</strong> {{ videoDescription }}</p>
            <p><strong>Archivo:</strong> {{ originalFilename }}</p>
          </div>
          <button class="btn primary" @click="reset">Subir otro</button>
        </div>

        <!-- Error -->
        <div v-if="step === 'error'" class="result error">
          <div class="result-icon">❌</div>
          <h3>No se pudo subir el video</h3>
          <p class="error-msg">{{ errorMsg }}</p>
          <button class="btn" @click="reset">Intentar de nuevo</button>
        </div>
      </div>
    </div>
  </TvModalShell>
</template>

<style scoped>
.modal {
  width: 100%;
  max-height: 90vh;
  display: flex;
  flex-direction: column;
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
  padding-right: 2.5rem;
  font-size: 1.25rem;
  font-weight: 600;
}

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

.metadata-fields {
  display: grid;
  gap: 1rem;
  margin-top: 1.15rem;
}

.field {
  display: grid;
  gap: 0.42rem;
}

.field label {
  color: #d8d7e4;
  font-size: 0.84rem;
  font-weight: 700;
}

.field input,
.field textarea {
  width: 100%;
  padding: 0.72rem 0.8rem;
  border: 1px solid #3a3a5a;
  border-radius: 9px;
  outline: none;
  background: #11111d;
  color: #efeff6;
  font: inherit;
  line-height: 1.45;
  resize: vertical;
}

.field input:focus,
.field textarea:focus {
  border-color: #6c63ff;
  box-shadow: 0 0 0 3px rgba(108, 99, 255, 0.16);
}

.field input:disabled,
.field textarea:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.field small {
  color: #6f6d81;
  font-size: 0.7rem;
  text-align: right;
}

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

/* ─── Emisión de datos ─────────────────────────── */
.data-transmission {
  display: grid;
  grid-template-columns: 3.5rem minmax(8rem, 1fr) 3.5rem;
  align-items: center;
  gap: 0.75rem;
  margin: 1.25rem 0 0.75rem;
  padding: 1rem;
  border: 1px solid rgba(108, 99, 255, 0.28);
  border-radius: 12px;
  background: radial-gradient(circle at center, rgba(72, 198, 239, 0.08), transparent 65%);
  overflow: hidden;
}

.transmission-node {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.2rem;
  color: #a9a6c4;
}

.node-icon {
  display: grid;
  width: 2.5rem;
  height: 2.5rem;
  place-items: center;
  border: 1px solid #4a4770;
  border-radius: 50%;
  background: #24233b;
  color: #b9b5ff;
  font-size: 1.25rem;
}

.cloud-node .node-icon {
  border-color: rgba(72, 198, 239, 0.55);
  color: #7ddcf5;
  box-shadow: 0 0 18px rgba(72, 198, 239, 0.16);
  animation: cloud-pulse 1.4s ease-in-out infinite;
}

.transmission-node small {
  font-size: 0.68rem;
  font-weight: 650;
  letter-spacing: 0.05em;
  text-transform: uppercase;
}

.data-channel {
  position: relative;
  height: 2rem;
}

.channel-line {
  position: absolute;
  top: 50%;
  right: 0;
  left: 0;
  height: 1px;
  background: linear-gradient(90deg, rgba(108, 99, 255, 0.2), #6c63ff, #48c6ef);
}

.data-packet {
  position: absolute;
  z-index: 1;
  top: calc(50% - 0.22rem);
  left: -0.45rem;
  width: 0.45rem;
  height: 0.45rem;
  border-radius: 2px;
  background: #8f88ff;
  box-shadow: 0 0 9px rgba(108, 99, 255, 0.9);
  animation: emit-packet 1.8s linear infinite;
}

.data-packet:nth-child(2) { animation-delay: -0.3s; }
.data-packet:nth-child(3) { animation-delay: -0.6s; }
.data-packet:nth-child(4) { animation-delay: -0.9s; }
.data-packet:nth-child(5) { animation-delay: -1.2s; }
.data-packet:nth-child(6) { animation-delay: -1.5s; }

.transfer-stats {
  display: flex;
  justify-content: space-between;
  gap: 0.75rem;
  color: #85839c;
  font-size: 0.72rem;
  font-variant-numeric: tabular-nums;
}

@keyframes emit-packet {
  0% { left: -0.45rem; opacity: 0; transform: scale(0.65); }
  12% { opacity: 1; }
  84% { opacity: 1; }
  100% { left: 100%; opacity: 0; transform: scale(1); }
}

@keyframes cloud-pulse {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(1.08); }
}

/* ─── Estado de la subida ───────────────────────── */
.status-message {
  margin: 0.9rem 0 0.35rem;
  color: #d8d7e4;
  font-size: 0.95rem;
  font-weight: 600;
  text-align: center;
  min-height: 1.35rem;
}

.upload-hint {
  margin: 0.9rem 0 0;
  color: #6f6d81;
  font-size: 0.78rem;
  text-align: center;
}

/* ─── Resultado ─────────────────────────────────── */
.result { text-align: center; }
.result-icon { font-size: 3rem; margin-bottom: 0.5rem; }
.result h3 { margin: 0 0 0.75rem; }
.result-details { text-align: left; background: #0f0f1a; border-radius: 10px; padding: 0.75rem 1rem; margin-bottom: 0.75rem; }
.result-details p { margin: 0.35rem 0; font-size: 0.9rem; }
.error-msg { color: #ff6b6b; font-weight: 500; }
.metadata-warning { margin-bottom: 0.75rem; color: #ffd080; font-size: 0.86rem; line-height: 1.5; }

@media (max-width: 520px) {
  .transfer-stats { flex-direction: column; align-items: center; gap: 0.2rem; }
}

@media (prefers-reduced-motion: reduce) {
  .data-packet,
  .cloud-node .node-icon { animation: none; }

  .data-packet { display: none; }
}
</style>
