<script setup lang="ts">
import { computed } from 'vue';
import { useTvSignOut } from '@/composables/useTvSignOut';

const { phase } = useTvSignOut();
const isVisible = computed(() => phase.value !== 'idle');
</script>

<template>
  <Teleport to="body">
    <div
      v-if="isVisible"
      class="tv-signout-overlay"
      :class="`is-${phase}`"
      aria-live="polite"
      aria-label="Cerrando sesión"
      role="status"
    >
      <span class="tv-signout-static" aria-hidden="true"></span>
      <span class="tv-signout-scanlines" aria-hidden="true"></span>
      <span class="tv-signout-beam" aria-hidden="true"></span>
      <span class="tv-signout-copy">{{ phase === 'static' ? 'SIN SEÑAL' : '' }}</span>
    </div>
  </Teleport>
</template>

<style scoped>
.tv-signout-overlay {
  position: fixed;
  z-index: 9999;
  inset: 0;
  display: grid;
  overflow: hidden;
  place-items: center;
  background: #000;
  isolation: isolate;
  cursor: wait;
}

.tv-signout-static,
.tv-signout-scanlines,
.tv-signout-beam {
  position: absolute;
  pointer-events: none;
}

.tv-signout-static {
  z-index: 1;
  inset: -20%;
  background:
    repeating-radial-gradient(circle at 14% 21%, rgba(255, 255, 255, 0.95) 0 1px, transparent 1px 4px),
    repeating-linear-gradient(112deg, #f4f4f4 0 1px, #080808 1px 3px, #8f8f8f 3px 4px);
  background-size: 9px 11px, 13px 9px;
  opacity: 0;
}

.tv-signout-scanlines {
  z-index: 3;
  inset: 0;
  background: repeating-linear-gradient(
    to bottom,
    rgba(255, 255, 255, 0.06) 0,
    rgba(255, 255, 255, 0.06) 1px,
    rgba(0, 0, 0, 0.18) 2px,
    rgba(0, 0, 0, 0.18) 4px
  );
  opacity: 0;
}

.tv-signout-beam {
  z-index: 4;
  width: 100vw;
  height: 100vh;
  background: #f8fbff;
  box-shadow: 0 0 20px 8px #fff, 0 0 70px 20px rgba(142, 188, 255, 0.8);
  transform-origin: center;
}

.tv-signout-copy {
  position: relative;
  z-index: 5;
  color: rgba(240, 240, 240, 0.78);
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: clamp(0.7rem, 1.2vw, 1rem);
  font-weight: 800;
  letter-spacing: 0.38em;
  text-indent: 0.38em;
  opacity: 0;
}

.is-powering-off {
  animation: blackout 1000ms ease-in both;
}

.is-powering-off .tv-signout-beam {
  animation: tv-power-off 1000ms cubic-bezier(0.7, 0, 0.9, 0.4) both;
}

.is-static .tv-signout-static {
  opacity: 1;
  animation: static-noise 120ms steps(2, end) infinite;
}

.is-static .tv-signout-scanlines {
  opacity: 0.7;
  animation: scanlines 5s linear infinite;
}

.is-static .tv-signout-beam {
  width: 0.34rem;
  height: 0.34rem;
  border-radius: 50%;
  opacity: 0;
}

.is-static .tv-signout-copy {
  opacity: 1;
  animation: signal-flicker 240ms steps(2, end) infinite;
}

.is-recovering {
  background: transparent;
  animation: reveal-site 860ms ease-out both;
}

.is-recovering .tv-signout-static {
  opacity: 1;
  animation: signal-lock 860ms cubic-bezier(0.18, 0.72, 0.2, 1) both;
}

.is-recovering .tv-signout-scanlines {
  opacity: 0.7;
  animation: signal-lock 860ms cubic-bezier(0.18, 0.72, 0.2, 1) both;
}

.is-recovering .tv-signout-beam {
  width: 100vw;
  height: 0.16rem;
  opacity: 0.9;
  animation: tv-power-on 860ms cubic-bezier(0.18, 0.72, 0.2, 1) both;
}

.is-recovering .tv-signout-copy {
  animation: copy-out 260ms ease both;
}

@keyframes blackout {
  0% { background: transparent; }
  16%, 100% { background: #000; }
}

@keyframes tv-power-off {
  0% { opacity: 0.08; transform: scale(1); }
  30% { opacity: 0.62; transform: scaleY(0.035) scaleX(1); }
  72% { opacity: 1; transform: scaleY(0.008) scaleX(0.26); }
  100% { opacity: 0; transform: scale(0.003); }
}

@keyframes static-noise {
  0% { background-position: 0 0, 0 0; transform: translate3d(-1%, -1%, 0); }
  50% { background-position: 19px -23px, -14px 17px; transform: translate3d(1%, 1%, 0); }
  100% { background-position: -27px 11px, 21px -15px; transform: translate3d(-1%, 1%, 0); }
}

@keyframes scanlines {
  to { background-position-y: 14px; }
}

@keyframes signal-flicker {
  0%, 100% { opacity: 0.6; transform: translateX(1px); }
  50% { opacity: 0.92; transform: translateX(-1px); }
}

@keyframes signal-lock {
  0% { clip-path: inset(0); filter: brightness(1.8) contrast(1.4); opacity: 1; }
  34% { clip-path: inset(45% 0); filter: brightness(3); opacity: 0.94; }
  62% { clip-path: inset(49% 0); filter: brightness(4); opacity: 0.7; }
  100% { clip-path: inset(49.8% 50%); filter: brightness(3); opacity: 0; }
}

@keyframes tv-power-on {
  0% { opacity: 1; transform: scaleX(0.01); }
  42% { opacity: 0.85; transform: scaleX(1); }
  100% { opacity: 0; transform: scaleY(8); }
}

@keyframes reveal-site {
  0%, 55% { opacity: 1; }
  100% { opacity: 0; }
}

@keyframes copy-out {
  to { opacity: 0; transform: scaleX(1.3); }
}

@media (prefers-reduced-motion: reduce) {
  .tv-signout-overlay,
  .tv-signout-static,
  .tv-signout-scanlines,
  .tv-signout-beam,
  .tv-signout-copy {
    animation: none !important;
  }

  .is-powering-off,
  .is-static {
    background: #000;
  }

  .is-static .tv-signout-static {
    opacity: 0.35;
  }

  .is-recovering {
    opacity: 0;
  }
}
</style>
