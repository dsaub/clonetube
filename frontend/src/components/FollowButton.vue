<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { followUser, getFollowState, unfollowUser } from '@/api/social'
import { useUserStore } from '@/stores/user'

const props = defineProps<{ username: string }>()
const emit = defineEmits<{ change: [following: boolean] }>()

const user = useUserStore()
const following = ref(false)
const followers = ref(0)
const loading = ref(false)
const error = ref('')

const isOwnChannel = computed(() => user.username === props.username)
const label = computed(() => {
  if (loading.value) return '…'
  return following.value ? 'Siguiendo' : 'Seguir'
})
const followersLabel = computed(
  () => `${followers.value} ${followers.value === 1 ? 'seguidor' : 'seguidores'}`,
)

async function loadState() {
  if (!props.username) return

  error.value = ''
  try {
    const state = await getFollowState(props.username, user.token?.access_token)
    following.value = state.following
    followers.value = state.followers
  } catch {
    error.value = 'No se pudo consultar el seguimiento.'
  }
}

async function toggle() {
  const token = user.token?.access_token
  if (!token) {
    error.value = 'Inicia sesión para seguir a este canal.'
    return
  }
  if (loading.value) return

  loading.value = true
  error.value = ''
  try {
    const state = following.value
      ? await unfollowUser(props.username, token)
      : await followUser(props.username, token)
    following.value = state.following
    followers.value = state.followers
    emit('change', state.following)
  } catch (cause: unknown) {
    error.value = cause instanceof Error ? cause.message : 'No se pudo actualizar el seguimiento.'
  } finally {
    loading.value = false
  }
}

watch(() => props.username, loadState, { immediate: true })
</script>

<template>
  <div class="follow-control">
    <button
      v-if="!isOwnChannel"
      type="button"
      class="follow-button"
      :class="{ following }"
      :disabled="loading"
      :aria-pressed="following"
      @click="toggle"
    >
      {{ label }}
    </button>
    <span class="follower-count">{{ followersLabel }}</span>
    <p v-if="error" class="follow-error" role="alert">{{ error }}</p>
  </div>
</template>

<style scoped>
.follow-control {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.6rem;
}

.follow-button {
  padding: 0.45rem 0.9rem;
  border: 1px solid #7c75ff;
  border-radius: 999px;
  background: #554bd8;
  color: #ffffff;
  cursor: pointer;
  font: inherit;
  font-size: 0.85rem;
  font-weight: 700;
  transition: background 160ms ease, border-color 160ms ease;
}

.follow-button:hover:not(:disabled) { background: #7a72ff; }

.follow-button.following {
  border-color: #3e3b5b;
  background: #24223d;
  color: #dcd9ff;
}

.follow-button:disabled { cursor: wait; opacity: 0.6; }

.follower-count {
  color: #8f8d9f;
  font-size: 0.8rem;
}

.follow-error {
  flex-basis: 100%;
  margin: 0;
  color: #ff9aa4;
  font-size: 0.78rem;
}
</style>
