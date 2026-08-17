import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import router from './router'
import { useUserStore } from '@/stores/user'
import '@/styles.css';

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
await useUserStore(pinia).initialize()
app.use(router)
app.mount('#app')
