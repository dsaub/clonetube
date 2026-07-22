import { readonly, ref } from 'vue';

export type TvSignOutPhase = 'idle' | 'powering-off' | 'static' | 'recovering';

const phase = ref<TvSignOutPhase>('idle');
let timers: ReturnType<typeof setTimeout>[] = [];

function clearTimers() {
  timers.forEach((timer) => clearTimeout(timer));
  timers = [];
}

/**
 * Runs the full-screen CRT sequence. It intentionally keeps the static visible
 * for one second before revealing the application again.
 */
export function startTvSignOut(): boolean {
  if (phase.value !== 'idle') return false;

  clearTimers();
  phase.value = 'powering-off';

  timers.push(
    setTimeout(() => {
      phase.value = 'static';
    }, 1000),
    setTimeout(() => {
      phase.value = 'recovering';
    }, 2000),
    setTimeout(() => {
      phase.value = 'idle';
      clearTimers();
    }, 2860),
  );

  return true;
}

export function useTvSignOut() {
  return {
    phase: readonly(phase),
    startTvSignOut,
  };
}
