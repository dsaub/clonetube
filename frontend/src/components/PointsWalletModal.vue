<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import TvModalShell from '@/components/TvModalShell.vue'
import { usePointsStore } from '@/stores/points'
import { useUserStore } from '@/stores/user'

const emit = defineEmits<{ close: [] }>()

const points = usePointsStore()
const user = useUserStore()
const loading = ref(false)
const error = ref('')
const message = ref('')

const balanceLabel = computed(() => points.balance.toLocaleString('es-ES'))

onMounted(() => {
  void points.refresh()
})

async function buy(amount: number) {
  const token = user.token?.access_token
  if (!token || loading.value) return

  loading.value = true
  error.value = ''
  message.value = ''
  try {
    const response = await fetch('/api/v1/points/purchase', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
      body: JSON.stringify({ amount_eur: amount }),
    })
    if (!response.ok) throw new Error('No se pudo iniciar la compra')
    const data = await response.json() as { orderId: number; checkoutUrl: string }
    if (data.checkoutUrl) {
      window.location.assign(data.checkoutUrl)
    } else {
      await points.refresh()
      message.value = 'Compra completada.'
    }
  } catch (cause: unknown) {
    error.value = cause instanceof Error ? cause.message : 'No se pudo completar la compra.'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <TvModalShell
    labelledby="wallet-title"
    :max-width="'30rem'"
    :can-close="!loading"
    @close="emit('close')"
  >
    <span class="signal-label">CH 05 · MONEDERO</span>
    <h2 id="wallet-title">Mis puntos</h2>
    <p class="balance">
      <span aria-hidden="true">◆</span> {{ balanceLabel }}
    </p>

    <p class="wallet-hint">
      Usa tus puntos para donar a otros canales o canjearlos cuando quieras.
    </p>

    <div class="wallet-actions">
      <button type="button" :disabled="loading" @click="buy(5)">Comprar 5</button>
      <button type="button" :disabled="loading" @click="buy(10)">Comprar 10</button>
      <button type="button" :disabled="loading" @click="buy(20)">Comprar 20</button>
    </div>

    <p v-if="error" class="wallet-error" role="alert">{{ error }}</p>
    <p v-if="message" class="wallet-message" role="status">{{ message }}</p>
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
  font-size: 1.6rem;
  line-height: 1.2;
}

.balance {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin: 1.2rem 0;
  font-size: 2rem;
  font-weight: 800;
  color: #e0dfff;
}

.balance span {
  color: #b9b5ff;
}

.wallet-hint {
  margin: 0 0 1.4rem;
  color: #aaaabd;
  font-size: 0.9rem;
  line-height: 1.5;
}

.wallet-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.6rem;
}

.wallet-actions button {
  flex: 1 1 auto;
  min-height: 2.6rem;
  padding: 0.6rem 1rem;
  border: 1px solid #6c63ff;
  border-radius: 0.5rem;
  background: #554bd8;
  color: #ffffff;
  cursor: pointer;
  font: inherit;
  font-weight: 750;
  transition: background-color 160ms ease, transform 160ms ease;
}

.wallet-actions button:hover:not(:disabled) {
  background: #7c75ff;
  transform: translateY(-1px);
}

.wallet-actions button:disabled {
  cursor: wait;
  opacity: 0.6;
}

.wallet-error {
  margin: 1rem 0 0;
  color: #ff9aa4;
  font-size: 0.85rem;
}

.wallet-message {
  margin: 1rem 0 0;
  color: #66df9b;
  font-size: 0.85rem;
}
</style>
