<script setup lang="ts">
import { ref, watch } from 'vue'
import Header from '@/components/Header.vue'
import UploadModal from '@/components/UploadModal.vue'
import {
  deleteStudioVideo,
  listStudioVideos,
  updateStudioVideo,
  type StudioVideoItem,
  type VideoVisibility,
} from '@/api/video'
import { useUserStore } from '@/stores/user'

interface VideoDraft extends StudioVideoItem {
  allowedUsersText: string
}

const user = useUserStore()
const videos = ref<VideoDraft[]>([])
const loading = ref(false)
const error = ref('')
const notice = ref('')
const busyId = ref('')
const uploadOpen = ref(false)

const visibilityLabels: Record<VideoVisibility, string> = {
  public: 'Público',
  unlisted: 'Oculto',
  private: 'Privado',
}

async function loadVideos(token: string) {
  loading.value = true
  error.value = ''
  try {
    videos.value = (await listStudioVideos(token))
      .sort((a, b) => Date.parse(b.last_modified) - Date.parse(a.last_modified))
      .map((video) => ({ ...video, allowedUsersText: video.allowed_users.join(', ') }))
  } catch (cause: unknown) {
    error.value = cause instanceof Error ? cause.message : 'No se han podido cargar tus vídeos.'
  } finally {
    loading.value = false
  }
}

async function save(video: VideoDraft) {
  const token = user.token?.access_token
  if (!token || !video.title.trim()) return
  busyId.value = video.id
  error.value = ''
  notice.value = ''
  try {
    const updated = await updateStudioVideo({
      ...video,
      allowed_users: video.allowedUsersText.split(',').map((name) => name.trim()).filter(Boolean),
    }, token)
    Object.assign(video, updated, { allowedUsersText: updated.allowed_users.join(', ') })
    notice.value = `“${updated.title}” se ha guardado.`
  } catch (cause: unknown) {
    error.value = cause instanceof Error ? cause.message : 'No se ha podido guardar el vídeo.'
  } finally {
    busyId.value = ''
  }
}

async function remove(video: VideoDraft) {
  const token = user.token?.access_token
  if (!token || !window.confirm(`¿Eliminar “${video.title}” definitivamente?`)) return
  busyId.value = video.id
  error.value = ''
  try {
    await deleteStudioVideo(video.id, token)
    videos.value = videos.value.filter(({ id }) => id !== video.id)
    notice.value = 'Vídeo eliminado.'
  } catch (cause: unknown) {
    error.value = cause instanceof Error ? cause.message : 'No se ha podido eliminar el vídeo.'
  } finally {
    busyId.value = ''
  }
}

async function copyLink(video: VideoDraft) {
  const url = new URL('/watch', window.location.origin)
  url.searchParams.set('id', video.id)
  try {
    await navigator.clipboard.writeText(url.toString())
    notice.value = 'Enlace copiado.'
  } catch {
    error.value = 'No se ha podido copiar el enlace.'
  }
}

function onUploaded() {
  uploadOpen.value = false
  notice.value = 'El vídeo ya está disponible en tu canal.'
  const token = user.token?.access_token
  if (token) void loadVideos(token)
}

function formatDate(value: string): string {
  return new Intl.DateTimeFormat('es-ES', { day: '2-digit', month: 'short', year: 'numeric' })
    .format(new Date(value))
}

watch(() => user.token?.access_token, (token) => {
  if (token) void loadVideos(token)
  else videos.value = []
}, { immediate: true })
</script>

<template>
  <div class="studio-page">
    <Header />
    <div class="studio-layout">
      <aside class="studio-sidebar" aria-label="Navegación de Studio">
        <RouterLink to="/" class="back-link">← Clonetube</RouterLink>
        <div class="channel-mark" aria-hidden="true">{{ user.username?.charAt(0).toUpperCase() || 'C' }}</div>
        <strong>{{ user.username || 'Tu canal' }}</strong>
        <span>Tu canal</span>
        <div class="active-item">▦ Contenido</div>
      </aside>

      <main class="studio-content">
        <div class="studio-heading">
          <div>
            <span>CLONETUBE STUDIO</span>
            <h1>Contenido del canal</h1>
          </div>
          <button
            v-if="user.logged_in"
            type="button"
            class="upload-link"
            @click="uploadOpen = true"
          >
            <span aria-hidden="true">↑</span> Subir vídeo
          </button>
        </div>

        <div v-if="!user.logged_in" class="state-card">
          <h2>Inicia sesión para abrir Studio</h2>
          <p>Puedes hacerlo desde la cabecera.</p>
        </div>
        <div v-else-if="loading" class="state-card">Cargando tus vídeos…</div>
        <div v-else-if="error && videos.length === 0" class="state-card error" role="alert">
          {{ error }}
        </div>
        <div v-else-if="videos.length === 0" class="state-card">
          <h2>Aún no has subido vídeos</h2>
          <p>Cuando publiques el primero aparecerá aquí.</p>
          <button type="button" class="upload-link" @click="uploadOpen = true">
            <span aria-hidden="true">↑</span> Subir vídeo
          </button>
        </div>

        <template v-else>
          <div class="messages" aria-live="polite">
            <p v-if="error" class="message error">{{ error }}</p>
            <p v-else-if="notice" class="message success">{{ notice }}</p>
          </div>

          <section class="video-list" aria-label="Tus vídeos">
            <article v-for="video in videos" :key="video.id" class="video-row">
              <div class="video-summary">
                <RouterLink
                  class="thumbnail"
                  :to="{ name: 'watch', query: { id: video.id } }"
                  :aria-label="`Ver ${video.title}`"
                ><span>▶</span></RouterLink>
                <div>
                  <small>{{ video.original_filename }}</small>
                  <p>{{ formatDate(video.last_modified) }}</p>
                  <button type="button" class="copy-button" @click="copyLink(video)">Copiar enlace</button>
                </div>
              </div>

              <div class="fields">
                <label>
                  Título
                  <input v-model.trim="video.title" maxlength="200" required />
                </label>
                <label>
                  Descripción
                  <textarea v-model="video.description" maxlength="5000" rows="2"></textarea>
                </label>
              </div>

              <div class="access-fields">
                <label>
                  Visibilidad
                  <select v-model="video.visibility">
                    <option v-for="(label, value) in visibilityLabels" :key="value" :value="value">
                      {{ label }}
                    </option>
                  </select>
                </label>
                <label v-if="video.visibility === 'private'">
                  Usuarios permitidos
                  <input
                    v-model="video.allowedUsersText"
                    placeholder="ana, pedro"
                  />
                  <small>Nombres de usuario separados por comas.</small>
                </label>
                <p v-else class="visibility-help">
                  {{ video.visibility === 'public'
                    ? 'Aparece en el listado de vídeos.'
                    : 'Solo se encuentra mediante su enlace.' }}
                </p>
              </div>

              <div class="row-actions">
                <button
                  type="button"
                  class="save-button"
                  :disabled="busyId === video.id || !video.title.trim()"
                  @click="save(video)"
                >{{ busyId === video.id ? 'Guardando…' : 'Guardar' }}</button>
                <button
                  type="button"
                  class="delete-button"
                  :disabled="busyId === video.id"
                  @click="remove(video)"
                >Eliminar</button>
              </div>
            </article>
          </section>
        </template>
      </main>
    </div>

    <UploadModal
      v-if="uploadOpen"
      @close="uploadOpen = false"
      @uploaded="onUploaded"
    />
  </div>
</template>

<style scoped>
.studio-page { min-height: 100vh; background: #0f0f1a; color: #e0e0e0; }
.studio-layout { display: grid; grid-template-columns: 14rem minmax(0, 1fr); min-height: calc(100vh - 4rem); }
.studio-sidebar { display: flex; flex-direction: column; align-items: center; gap: .45rem; padding: 1.5rem 1rem; border-right: 1px solid #2a2a4a; background: #141421; }
.back-link { align-self: flex-start; margin-bottom: 1.25rem; color: #9d99ff; font-size: .85rem; }
.channel-mark { display: grid; width: 4rem; height: 4rem; place-items: center; border: 1px solid #4e4980; border-radius: 50%; background: #29264a; color: #c2beff; font-size: 1.5rem; font-weight: 800; }
.studio-sidebar > span { color: #7f7d8d; font-size: .75rem; }
.active-item { align-self: stretch; margin-top: 1.5rem; padding: .8rem 1rem; border-left: 3px solid #6c63ff; background: rgba(108, 99, 255, .12); color: #c8c5ff; font-weight: 700; }
.studio-content { min-width: 0; padding: clamp(1.4rem, 4vw, 3rem); }
.studio-heading { display: flex; align-items: end; justify-content: space-between; gap: 1rem; margin-bottom: 2rem; }
.studio-heading span { color: #8882ff; font-size: .68rem; font-weight: 800; letter-spacing: .16em; }
.studio-heading h1 { margin-top: .35rem; color: #f5f4ff; font-size: clamp(1.7rem, 3vw, 2.4rem); }
.upload-link, .save-button { border: 1px solid #6c63ff; border-radius: .55rem; background: #6c63ff; color: white; font-weight: 750; }
.upload-link { display: inline-flex; align-items: center; gap: .45rem; padding: .7rem 1rem; cursor: pointer; font: inherit; font-weight: 750; white-space: nowrap; }
.upload-link:hover { background: #7a72ff; }
.state-card .upload-link { margin-top: 1.25rem; }
.messages { min-height: 2.3rem; }
.message { margin-bottom: .8rem; padding: .65rem .8rem; border-radius: .5rem; font-size: .85rem; }
.message.error, .state-card.error { border: 1px solid #6b303c; background: #26171d; color: #ff9da8; }
.message.success { border: 1px solid #315e4a; background: #14251e; color: #8de2b6; }
.state-card { padding: 3rem; border: 1px solid #2a2a4a; border-radius: .75rem; background: #181827; text-align: center; color: #9d9bad; }
.state-card h2 { margin-bottom: .5rem; color: #eee; }
.video-list { overflow: hidden; border: 1px solid #2a2a4a; border-radius: .75rem; background: #171725; }
.video-row { display: grid; grid-template-columns: minmax(12rem, 1fr) minmax(16rem, 1.35fr) minmax(13rem, 1fr) auto; gap: 1rem; align-items: start; padding: 1rem; border-bottom: 1px solid #2a2a4a; }
.video-row:last-child { border-bottom: 0; }
.video-summary { display: flex; gap: .8rem; min-width: 0; }
.thumbnail { display: grid; flex: 0 0 7rem; aspect-ratio: 16 / 9; place-items: center; border: 1px solid #39365d; border-radius: .4rem; background: radial-gradient(circle, #333064, #171728 70%); color: #fff; }
.video-summary small { display: block; overflow: hidden; color: #d9d7e4; text-overflow: ellipsis; white-space: nowrap; }
.video-summary p, .visibility-help { margin-top: .4rem; color: #777587; font-size: .75rem; }
.copy-button { margin-top: .55rem; border: 0; background: none; color: #9994ff; cursor: pointer; font: inherit; font-size: .75rem; }
.fields, .access-fields { display: grid; gap: .7rem; }
label { display: grid; gap: .3rem; color: #9f9dac; font-size: .72rem; font-weight: 700; }
input, textarea, select { width: 100%; border: 1px solid #383654; border-radius: .4rem; background: #10101b; color: #e8e7ef; font: inherit; font-size: .85rem; padding: .55rem .65rem; }
textarea { resize: vertical; }
input:focus, textarea:focus, select:focus { border-color: #7770ff; outline: 2px solid rgba(108, 99, 255, .15); }
label small { color: #777587; font-weight: 400; }
.row-actions { display: flex; flex-direction: column; gap: .5rem; }
.row-actions button { min-width: 5.5rem; padding: .58rem .75rem; cursor: pointer; font: inherit; font-size: .78rem; }
.row-actions button:disabled { cursor: wait; opacity: .5; }
.delete-button { border: 1px solid #5e3540; border-radius: .55rem; background: transparent; color: #ff929f; }
.delete-button:hover { background: rgba(255, 90, 108, .1); }
@media (max-width: 1050px) {
  .studio-layout { grid-template-columns: 1fr; }
  .studio-sidebar { display: none; }
  .video-row { grid-template-columns: 1fr 1.4fr; }
  .row-actions { flex-direction: row; }
}
@media (max-width: 650px) {
  .video-row { grid-template-columns: 1fr; }
  .studio-heading { align-items: flex-start; flex-direction: column; }
}
</style>
