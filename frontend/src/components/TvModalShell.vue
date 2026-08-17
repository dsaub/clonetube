<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'

const props = withDefaults(defineProps<{
  labelledby: string
  canClose?: boolean
  maxWidth?: string
  maxHeight?: string
  padding?: string
  switching?: boolean
}>(), {
  canClose: true,
  maxWidth: '28rem',
  maxHeight: 'none',
  padding: 'clamp(1.6rem, 4vw, 2.15rem)',
  switching: false,
})

const emit = defineEmits<{
  close: []
  closing: []
}>()

const closing = ref(false)
const panel = ref<HTMLElement | null>(null)
const panelStyle = computed(() => ({
  '--tv-modal-width': props.maxWidth,
  '--tv-modal-max-height': props.maxHeight,
  '--tv-modal-padding': props.padding,
}))

let closeTimer: ReturnType<typeof setTimeout> | undefined
let previousBodyOverflow = ''
let previouslyFocused: HTMLElement | null = null

const focusableSelector = [
  'button:not([disabled])',
  '[href]',
  'input:not([disabled])',
  'select:not([disabled])',
  'textarea:not([disabled])',
  '[tabindex]:not([tabindex="-1"])',
].join(',')

function requestClose() {
  if (!props.canClose || closing.value) return

  closing.value = true
  emit('closing')
  const reduceMotion = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false
  closeTimer = setTimeout(() => emit('close'), reduceMotion ? 0 : 580)
}

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    requestClose()
    return
  }
  if (event.key !== 'Tab' || !panel.value) return

  const focusable = Array.from(panel.value.querySelectorAll<HTMLElement>(focusableSelector))
  if (focusable.length === 0) {
    event.preventDefault()
    panel.value.focus()
    return
  }

  const first = focusable[0]!
  const last = focusable.at(-1)!
  const activeElement = document.activeElement
  const focusFirst = !event.shiftKey && (activeElement === last || !panel.value.contains(activeElement))
  if (focusFirst) {
    event.preventDefault()
    first.focus()
  } else if (event.shiftKey && (activeElement === first || activeElement === panel.value)) {
    event.preventDefault()
    last.focus()
  }
}

onMounted(() => {
  previouslyFocused = document.activeElement instanceof HTMLElement ? document.activeElement : null
  previousBodyOverflow = document.body.style.overflow
  document.body.style.overflow = 'hidden'
  document.addEventListener('keydown', onKeydown)
  void nextTick(() => {
    if (panel.value && !panel.value.contains(document.activeElement)) {
      panel.value.focus({ preventScroll: true })
    }
  })
})

onBeforeUnmount(() => {
  clearTimeout(closeTimer)
  document.removeEventListener('keydown', onKeydown)
  document.body.style.overflow = previousBodyOverflow
  previouslyFocused?.focus({ preventScroll: true })
})

defineExpose({ close: requestClose })
</script>

<template>
  <Teleport to="body">
    <div
      class="tv-modal-backdrop"
      :class="{ 'is-closing': closing }"
      @mousedown.self="requestClose"
    >
      <section
        ref="panel"
        class="tv-modal-panel"
        :class="{ 'is-closing': closing, 'is-switching': switching }"
        :style="panelStyle"
        role="dialog"
        aria-modal="true"
        :aria-labelledby="labelledby"
        tabindex="-1"
      >
        <div class="crt-effects" aria-hidden="true">
          <span class="crt-static"></span>
        </div>

        <button
          class="tv-modal-close"
          type="button"
          :disabled="!canClose || closing"
          :title="canClose ? undefined : 'Espera a que termine la operación'"
          aria-label="Cerrar"
          @click="requestClose"
        >
          <span aria-hidden="true">×</span>
        </button>

        <div class="tv-modal-content">
          <slot />
        </div>
      </section>
    </div>
  </Teleport>
</template>

<style scoped>
.tv-modal-backdrop {
  position: fixed;
  z-index: 1000;
  inset: 0;
  display: grid;
  overflow-y: auto;
  place-items: center;
  padding: 2rem 1rem;
  background: rgba(5, 5, 12, 0.76);
  backdrop-filter: blur(8px);
  animation: backdrop-in 220ms ease both;
}

.tv-modal-backdrop.is-closing {
  animation: backdrop-out 580ms ease both;
}

.tv-modal-panel {
  position: relative;
  width: min(100%, var(--tv-modal-width));
  max-height: var(--tv-modal-max-height);
  padding: var(--tv-modal-padding);
  overflow: hidden;
  border: 1px solid #44415c;
  border-radius: 1rem;
  background: #1b1b29;
  box-shadow: 0 26px 80px rgba(0, 0, 0, 0.55), 0 0 30px rgba(108, 99, 255, 0.1);
  transform-origin: center;
  animation: tv-turn-on 720ms cubic-bezier(0.2, 0.75, 0.25, 1) both;
}

.tv-modal-panel.is-closing {
  pointer-events: none;
  animation: tv-turn-off 560ms cubic-bezier(0.7, 0, 0.9, 0.4) both;
}

.tv-modal-panel.is-closing::after {
  position: absolute;
  z-index: 6;
  inset: 0;
  background: linear-gradient(
    to bottom,
    transparent 48%,
    rgba(255, 255, 255, 0.98) 50%,
    transparent 52%
  );
  content: '';
  pointer-events: none;
  animation: power-off-flash 560ms ease-in both;
}

.tv-modal-content {
  position: relative;
  z-index: 1;
  height: 100%;
}

.tv-modal-close {
  position: absolute;
  z-index: 7;
  top: 0.8rem;
  right: 0.8rem;
  display: grid;
  width: 2rem;
  height: 2rem;
  padding: 0;
  place-items: center;
  border: 1px solid #46445d;
  border-radius: 50%;
  background: #12121d;
  color: #b9b7c8;
  cursor: pointer;
  font: inherit;
  font-size: 1.45rem;
  line-height: 1;
  transition: border-color 160ms ease, background-color 160ms ease, color 160ms ease, transform 160ms ease;
}

.tv-modal-close:hover:not(:disabled) {
  border-color: #ff7481;
  background: #351d27;
  color: #ff8994;
  transform: rotate(5deg);
}

.tv-modal-close:disabled {
  cursor: wait;
  opacity: 0.35;
}

.crt-effects,
.crt-effects::before,
.crt-effects::after,
.crt-static {
  position: absolute;
  z-index: 3;
  inset: 0;
  border-radius: inherit;
  pointer-events: none;
}

.crt-effects::before {
  background: repeating-linear-gradient(
    to bottom,
    rgba(255, 255, 255, 0.035) 0,
    rgba(255, 255, 255, 0.035) 1px,
    rgba(0, 0, 0, 0.11) 2px,
    rgba(0, 0, 0, 0.11) 4px
  );
  content: '';
  opacity: 0.65;
  animation: scanline-drift 8s linear infinite;
}

.crt-effects::after {
  background: radial-gradient(ellipse at center, transparent 48%, rgba(0, 0, 0, 0.36) 100%);
  box-shadow: inset 0 0 26px rgba(111, 103, 255, 0.09);
  content: '';
}

.crt-static {
  z-index: 4;
  background:
    repeating-radial-gradient(circle at 17% 32%, #fff 0 1px, transparent 1px 4px),
    repeating-linear-gradient(117deg, #fff 0 1px, #000 1px 3px, #777 3px 4px);
  background-size: 7px 9px, 11px 8px;
  mix-blend-mode: screen;
  opacity: 0;
}

.tv-modal-panel.is-switching .crt-static {
  animation: static-burst 460ms steps(3, end) both;
}

@keyframes tv-turn-on {
  0% {
    opacity: 0;
    filter: brightness(5) blur(2px);
    transform: scaleX(0.015) scaleY(0.004);
  }
  28% {
    opacity: 1;
    filter: brightness(3.5) blur(1px);
    transform: scaleX(1) scaleY(0.007);
  }
  56% {
    filter: brightness(1.8);
    transform: scaleX(1) scaleY(0.04);
  }
  80% {
    filter: brightness(1.08);
    transform: scaleX(1) scaleY(1.025);
  }
  100% {
    opacity: 1;
    filter: brightness(1) blur(0);
    transform: scale(1);
  }
}

@keyframes tv-turn-off {
  0% {
    opacity: 1;
    filter: brightness(1);
    transform: scale(1);
  }
  42% {
    opacity: 1;
    filter: brightness(4) blur(1px);
    transform: scaleX(1) scaleY(0.012);
  }
  72% {
    opacity: 1;
    filter: brightness(8) blur(2px);
    transform: scaleX(0.14) scaleY(0.007);
  }
  100% {
    opacity: 0;
    filter: brightness(10) blur(4px);
    transform: scaleX(0) scaleY(0.003);
  }
}

@keyframes power-off-flash {
  0%, 20% { opacity: 0; }
  42% { opacity: 1; }
  75% { opacity: 0.8; }
  100% { opacity: 0; }
}

@keyframes static-burst {
  0% { opacity: 0; background-position: 0 0, 0 0; }
  12% { opacity: 0.85; background-position: 13px -8px, -21px 9px; }
  45% { opacity: 0.72; background-position: -18px 17px, 11px -16px; }
  76% { opacity: 0.88; background-position: 8px -22px, -7px 18px; }
  100% { opacity: 0; background-position: -12px 4px, 22px -9px; }
}

@keyframes scanline-drift {
  to { background-position-y: 12px; }
}

@keyframes backdrop-in {
  from { opacity: 0; }
  to { opacity: 1; }
}

@keyframes backdrop-out {
  0%, 60% { opacity: 1; }
  100% { opacity: 0; }
}

@media (prefers-reduced-motion: reduce) {
  .tv-modal-backdrop,
  .tv-modal-backdrop.is-closing,
  .tv-modal-panel,
  .tv-modal-panel.is-closing,
  .tv-modal-panel.is-switching .crt-static,
  .crt-effects::before {
    animation: none;
  }
}
</style>
