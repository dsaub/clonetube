<script setup lang="ts">
import TvSignOutOverlay from '@/components/TvSignOutOverlay.vue';
</script>

<template>
  <RouterView />
  <TvSignOutOverlay />
</template>

<style>
*,
*::before,
*::after {
  box-sizing: border-box;
  margin: 0;
  padding: 0;
}

body {
  background: #0f0f1a;
  color: #e0e0e0;
  font-family: 'Inter', system-ui, -apple-system, sans-serif;
  -webkit-font-smoothing: antialiased;
}

a {
  color: #6c63ff;
  text-decoration: none;
}
</style>
