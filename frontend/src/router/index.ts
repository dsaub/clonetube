import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/dev',
      name: 'dev',
      component: () => import('@/views/DevView.vue'),
    },
    {
      path: '/watch',
      name: 'watch',
      component: () => import('@/views/WatchView.vue'),
    },
  ],
})

export default router
