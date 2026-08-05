<script lang="ts" setup>
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import Header from '@/components/Header.vue'
import axios from 'axios'

const route = useRoute()
const status = ref<'loading' | 'success' | 'error'>('loading')
const errorMessage = ref('')

async function verify() {
  status.value = 'loading'
  errorMessage.value = ''
  try {
    const response = await axios.get<{ status: string }>(
      `/api/v1/auth/verify/${encodeURIComponent(String(route.params.code))}`,
    )
    status.value = response.data.status === 'success' ? 'success' : 'error'
  } catch (error: unknown) {
    status.value = 'error'
    errorMessage.value = axios.isAxiosError(error) && error.response?.status === 404
      ? 'El código de verificación no es válido o ya ha sido utilizado.'
      : 'No se ha podido verificar la cuenta. Inténtalo de nuevo.'
  }
}

onMounted(verify)
</script>

<template>
  <div class="verify-page">
    <Header />

    <main class="verify-main">
      <section class="verify-card" aria-live="polite">
        <template v-if="status === 'loading'">
          <span class="verify-icon" aria-hidden="true">⌛</span>
          <span class="signal-label">CH 04 · VERIFICANDO</span>
          <h1>Verificando tu cuenta…</h1>
        </template>

        <template v-else-if="status === 'success'">
          <span class="verify-icon success" aria-hidden="true">✓</span>
          <span class="signal-label">CH 04 · VERIFICADO</span>
          <h1>Cuenta verificada</h1>
          <p>Tu cuenta ya está activa. Ya puedes iniciar sesión.</p>
          <RouterLink to="/" class="verify-action">Ir al inicio</RouterLink>
        </template>

        <template v-else>
          <span class="verify-icon error" aria-hidden="true">!</span>
          <span class="signal-label">CH 04 · ERROR</span>
          <h1>No se pudo verificar</h1>
          <p>{{ errorMessage }}</p>
          <button type="button" class="verify-action" @click="verify">Reintentar</button>
        </template>
      </section>
    </main>
  </div>
</template>

<style scoped>
.verify-page {
  min-height: 100vh;
  background: #0f0f1a;
  color: #e0e0e0;
}

.verify-main {
  display: grid;
  min-height: calc(100vh - 4.5rem);
  place-items: center;
  padding: 2rem;
}

.verify-card {
  display: grid;
  width: min(26rem, 100%);
  place-items: center;
  gap: 0.6rem;
  padding: 2.5rem;
  border: 1px solid #343248;
  border-radius: 1rem;
  background: rgba(23, 23, 35, 0.58);
  text-align: center;
}

.verify-icon {
  display: grid;
  width: 3.5rem;
  height: 3.5rem;
  margin-bottom: 0.4rem;
  place-items: center;
  border-radius: 50%;
  background: #24223d;
  color: #8f89ff;
  font-size: 1.5rem;
}

.verify-icon.success {
  background: rgba(102, 223, 155, 0.12);
  color: #66df9b;
}

.verify-icon.error {
  background: rgba(239, 102, 116, 0.12);
  color: #ff7b88;
}

.signal-label {
  color: #8f89ff;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 0.7rem;
  font-weight: 800;
  letter-spacing: 0.12em;
}

.verify-card h1 {
  margin: 0;
  color: #f0eff8;
  font-size: 1.4rem;
}

.verify-card p {
  margin: 0;
  color: #aaaabd;
  font-size: 0.9rem;
  line-height: 1.5;
}

.verify-action {
  margin-top: 0.8rem;
  padding: 0.55rem 0.9rem;
  border: 1px solid #4b4772;
  border-radius: 0.5rem;
  background: #24223d;
  color: #dcd9ff;
  cursor: pointer;
  font: inherit;
  font-size: 0.85rem;
  font-weight: 700;
  text-decoration: none;
}

.verify-action:hover {
  background: #2d2a4a;
  color: #fff;
}
</style>
