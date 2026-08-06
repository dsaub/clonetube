<script setup lang="ts">
import { computed, ref } from 'vue'
import TvModalShell from '@/components/TvModalShell.vue'
import { useUserStore } from '@/stores/user'
import { donatePoints } from '@/api/points'

const props = defineProps<{ recipientUsername: string }>()
const emit = defineEmits<{ close: [] }>()

const user = useUserStore()
const points = ref(5)
const message = ref('')
const loading = ref(false)
const error = ref('')
const done = ref(false)

const canSubmit = computed(() => points.value > 0 && !loading.value && !done.value)

async function submit() {
  const token = user.token?.access_token
  if (!token || !canSubmit.value) return

  loading.value = true
  error.value = ''
  try {
    await donatePoints(token, props.recipientUsername, points.value, message.value)
    done.value = true
  } catch (cause: unknown) {
    error.value = cause instanceof Error ? cause.message : 'No se pudo realizar la donación.'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <TvModalShell :labelledby="done ? 'donation-done-title' : 'donation-title'" :max-width="'30rem'">
    <span class="signal-label">CH 06 · DONACIÓN</span>
    <template v-if="done">
      <h2 id="donation-done-title">¡Gracias!</h2>
      <p class="donation-copy">
        Tu donación ha llegado a <strong>@{{ recipientUsername }}</strong>.
      </p>
      <button type="button" class="donation-submit" @click="emit('close')">Entendido</button>
    </template>
    <template v-else>
      <h2 id="donation-title">Donar a @{{ recipientUsername }}</h2>

      <form class="donation-form" @submit.prevent="submit">
        <label for="donation-points">Puntos</label>
        <input
          id="donation-points"
          v-model.number="points"
          type="number"
          min="1"
          step="1"
        />

        <label for="donation-message">Mensaje (opcional)</label>
        <input
          id="donation-message"
          v-model="message"
          type="text"
          maxlength="200"
          placeholder="Di algo bonito…"
        />

        <p v-if="error" class="donation-error" role="alert">{{ error }}</p>

        <button class="donation-submit" type="submit" :disabled="!canSubmit">
          {{ loading ? 'Enviando…' : `Donar ${points}` }}
        </button>
      </form>
    </template>
  </TvModalShell>
</template>

<style scoped>
.signal-label {
  display: block;
  margin-bottom: 0.65rem;
  color: #8f89ff;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 0.7rem;
  font-weight: 800;
  letter-spacing: 0.12em;
}

h2 {
  margin: 0;
  color: #fff;
  font-size: 1.5rem;
  line-height: 1.2;
}

.donation-copy {
  margin: 1rem 0 1.4rem;
  color: #aaaabd;
  font-size: 0.92rem;
  line-height: 1.5;
}

.donation-form {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  margin-top: 1.2rem;
}

.donation-form label {
  margin-top: 0.4rem;
  color: #d8d8e5;
  font-size: 0.84rem;
  font-weight: 650;
}

.donation-form input {
  width: 100%;
  min-height: 2.7rem;
  padding: 0.65rem 0.8rem;
  border: 1px solid #3b3b56;
  border-radius: 0.5rem;
  outline: none;
  background: #12121e;
  color: #f1f1f6;
  font: inherit;
  transition: border-color 160ms ease, box-shadow 160ms ease;
}

.donation-form input:focus {
  border-color: #6c63ff;
  box-shadow: 0 0 0 3px rgba(108, 99, 255, 0.2);
}

.donation-error {
  margin: 0.5rem 0 0;
  color: #ff9aa4;
  font-size: 0.84rem;
}

.donation-submit {
  width: 100%;
  min-height: 2.8rem;
  margin-top: 1.2rem;
  padding: 0.7rem 1rem;
  border: 1px solid transparent;
  border-radius: 0.5rem;
  background: #6c63ff;
  color: #fff;
  cursor: pointer;
  font: inherit;
  font-weight: 750;
  transition: background-color 160ms ease, transform 160ms ease;
}

.donation-submit:hover:not(:disabled) {
  background: #7c75ff;
  transform: translateY(-1px);
}

.donation-submit:disabled {
  cursor: not-allowed;
  background: #39394d;
  color: #9898a8;
}
</style>
