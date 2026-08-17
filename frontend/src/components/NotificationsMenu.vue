<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useUserStore } from '@/stores/user'
import {
  listNotifications,
  markNotificationRead,
  unreadNotificationsCount,
  type NotificationItem,
} from '@/api/points'

const user = useUserStore()
const open = ref(false)
const unread = ref(0)
const notifications = ref<NotificationItem[]>([])
const loading = ref(false)
const error = ref('')
let reloadTimer: ReturnType<typeof setTimeout> | undefined

async function refresh() {
  const token = user.token?.access_token
  if (!token) return

  try {
    unread.value = await unreadNotificationsCount(token)
    if (open.value) {
      notifications.value = await listNotifications(token)
    }
  } catch {
    error.value = 'No se pudieron cargar las notificaciones.'
  }
}

async function toggle() {
  open.value = !open.value
  if (open.value) {
    error.value = ''
    loading.value = true
    try {
      notifications.value = await listNotifications(user.token?.access_token ?? '')
    } catch {
      error.value = 'No se pudieron cargar las notificaciones.'
    } finally {
      loading.value = false
    }
  }
}

async function read(notification: NotificationItem) {
  const token = user.token?.access_token
  if (!token || notification.isRead) return

  notification.isRead = true
  unread.value = Math.max(0, unread.value - 1)
  try {
    await markNotificationRead(token, notification.id)
  } catch {
    notification.isRead = false
    unread.value += 1
  }
}

onMounted(() => {
  void refresh()
  reloadTimer = setInterval(() => void refresh(), 60000)
})

onBeforeUnmount(() => {
  clearInterval(reloadTimer)
})
</script>

<template>
  <div class="notifications">
    <button
      type="button"
      class="notifications-button"
      :aria-expanded="open"
      aria-label="Notificaciones"
      @click="toggle"
    >
      <span aria-hidden="true">◉</span>
      <span v-if="unread > 0" class="notifications-badge" aria-live="polite">{{ unread }}</span>
    </button>

    <div v-if="open" class="notifications-panel">
      <span class="notifications-kicker">AVISOS</span>
      <p v-if="error" class="notifications-error" role="alert">{{ error }}</p>
      <p v-else-if="loading" class="notifications-empty">Cargando…</p>
      <p v-else-if="notifications.length === 0" class="notifications-empty">
        No tienes avisos pendientes.
      </p>
      <ul v-else class="notifications-list">
        <li v-for="notification in notifications" :key="notification.id">
          <button
            type="button"
            class="notification-item"
            :class="{ unread: !notification.isRead }"
            @click="read(notification)"
          >
            <strong>{{ notification.title }}</strong>
            <span>{{ notification.body }}</span>
          </button>
        </li>
      </ul>
    </div>
  </div>
</template>

<style scoped>
.notifications {
  position: relative;
}

.notifications-button {
  position: relative;
  display: grid;
  width: 2.3rem;
  height: 2.3rem;
  place-items: center;
  border: 1px solid #3c3a58;
  border-radius: 0.5rem;
  background: #171725;
  color: #b9b5ff;
  cursor: pointer;
  font: inherit;
  transition: border-color 160ms ease, background-color 160ms ease;
}

.notifications-button:hover {
  border-color: #6c63ff;
  background: #24223d;
}

.notifications-badge {
  position: absolute;
  top: -0.35rem;
  right: -0.35rem;
  display: grid;
  min-width: 1.15rem;
  height: 1.15rem;
  padding: 0 0.25rem;
  place-items: center;
  border-radius: 99px;
  background: #c83f50;
  color: #ffffff;
  font-size: 0.68rem;
  font-weight: 800;
}

.notifications-panel {
  position: absolute;
  z-index: 30;
  top: calc(100% + 0.6rem);
  right: 0;
  width: min(22rem, 86vw);
  padding: 1rem;
  border: 1px solid #44415c;
  border-radius: 0.8rem;
  background: #1b1b29;
  box-shadow: 0 18px 50px rgba(0, 0, 0, 0.5);
}

.notifications-kicker {
  display: block;
  margin-bottom: 0.5rem;
  color: #8f89ff;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 0.65rem;
  font-weight: 800;
  letter-spacing: 0.16em;
}

.notifications-list {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
  max-height: 20rem;
  margin: 0;
  padding: 0;
  overflow-y: auto;
  list-style: none;
}

.notification-item {
  display: flex;
  width: 100%;
  flex-direction: column;
  gap: 0.25rem;
  padding: 0.6rem 0.7rem;
  border: 1px solid #34324a;
  border-radius: 0.5rem;
  background: #181824;
  color: #b3b1c0;
  cursor: pointer;
  font: inherit;
  text-align: left;
  transition: border-color 160ms ease;
}

.notification-item:hover {
  border-color: #514d7c;
}

.notification-item.unread {
  border-color: #6c63ff;
  background: #24223d;
}

.notification-item strong {
  color: #e8e7f0;
  font-size: 0.86rem;
}

.notification-item span {
  font-size: 0.8rem;
}

.notifications-empty {
  margin: 0.4rem 0 0;
  color: #8f8d9f;
  font-size: 0.84rem;
}

.notifications-error {
  margin: 0.4rem 0 0;
  color: #ff9aa4;
  font-size: 0.84rem;
}
</style>
