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
    {
      path: '/studio',
      name: 'studio',
      component: () => import('@/views/StudioView.vue'),
    },
    {
      // El nombre viaja con arroba: /channel/@usuario
      path: '/channel/:handle',
      name: 'channel',
      component: () => import('@/views/ChannelView.vue'),
    },
    {
      path: '/',
      name: 'index',
      component: () => import('@/views/IndexView.vue'),
    },
  ],
})

export default router
