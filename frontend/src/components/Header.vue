<script setup lang="ts">
import { useUserStore } from '@/stores/user';
import { startTvSignOut } from '@/composables/useTvSignOut';
import TvModalShell from '@/components/TvModalShell.vue';
import axios from 'axios';
import { computed, nextTick, onBeforeUnmount, ref } from 'vue';

type AuthMode = 'login' | 'register';

const user = useUserStore();
const searchQuery = defineModel<string>('searchQuery', { default: '' });
const searchFocused = ref(false);
const authMode = ref<AuthMode>('login');
const authVisible = ref(false);
const closing = ref(false);
const switching = ref(false);
const loading = ref(false);
const authError = ref('');
const registeredEmail = ref('');
const registeredUsername = ref('');

const loginUsername = ref('');
const loginPassword = ref('');

const registerUsername = ref('');
const registerPassword = ref('');
const verifyPassword = ref('');
const fullName = ref('');
const email = ref('');

const loginUsernameInput = ref<HTMLInputElement | null>(null);
const registerUsernameInput = ref<HTMLInputElement | null>(null);
const authModal = ref<InstanceType<typeof TvModalShell> | null>(null);

let switchModeTimer: ReturnType<typeof setTimeout> | undefined;
let switchEndTimer: ReturnType<typeof setTimeout> | undefined;

const loginActive = computed(
  () => Boolean(loginUsername.value && loginPassword.value) && !loading.value,
);
const passwordMatches = computed(
  () => !verifyPassword.value || registerPassword.value === verifyPassword.value,
);
const emailIsValid = computed(() => !email.value || /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.value));
const registerActive = computed(
  () =>
    Boolean(
      registerUsername.value &&
      registerPassword.value &&
      verifyPassword.value &&
      fullName.value &&
      email.value,
    ) &&
    passwordMatches.value &&
    emailIsValid.value &&
    !loading.value,
);

function focusCurrentForm() {
  void nextTick(() => {
    const input = authMode.value === 'login'
      ? loginUsernameInput.value
      : registerUsernameInput.value;
    input?.focus({ preventScroll: true });
  });
}

function openAuth(mode: AuthMode) {
  if (authVisible.value) {
    switchAuthMode(mode);
    return;
  }

  authMode.value = mode;
  authError.value = '';
  registeredEmail.value = '';
  registeredUsername.value = '';
  closing.value = false;
  authVisible.value = true;
  focusCurrentForm();
}

function closeAuth() {
  if (!authVisible.value || closing.value) return;

  authModal.value?.close();
}

function markClosing() {
  closing.value = true;
  loading.value = false;
}

function finishClose() {
  authVisible.value = false;
  closing.value = false;
  authError.value = '';
}

function switchAuthMode(mode: AuthMode) {
  if (mode === authMode.value || switching.value || closing.value || loading.value) return;

  switching.value = true;
  authError.value = '';
  switchModeTimer = setTimeout(() => {
    authMode.value = mode;
    focusCurrentForm();
  }, 170);
  switchEndTimer = setTimeout(() => {
    switching.value = false;
  }, 460);
}

function clearError() {
  authError.value = '';
}

function signOut() {
  if (!startTvSignOut()) return;
  user.logout();
}

async function submitLogin() {
  if (!loginActive.value) return;

  loading.value = true;
  authError.value = '';
  try {
    await user.login(loginUsername.value, loginPassword.value);
    closeAuth();
  } catch (error: unknown) {
    authError.value = axios.isAxiosError(error) && error.response?.status === 401
      ? 'El usuario o la contraseña no son correctos.'
      : 'No se ha podido iniciar sesión. Inténtalo de nuevo.';
  } finally {
    loading.value = false;
  }
}

async function submitRegister() {
  if (!registerActive.value) return;

  loading.value = true;
  authError.value = '';
  try {
    const result = await user.register({
      username: registerUsername.value,
      password: registerPassword.value,
      full_name: fullName.value,
      email: email.value,
    });
    if (result.status === 'pending_verification') {
      registeredEmail.value = email.value;
      registeredUsername.value = registerUsername.value;
    }
  } catch (error: unknown) {
    authError.value = axios.isAxiosError(error) && error.response?.status === 409
      ? 'Ese usuario o correo electrónico ya está registrado.'
      : 'No se ha podido crear la cuenta. Revisa los datos e inténtalo de nuevo.';
  } finally {
    loading.value = false;
  }
}

onBeforeUnmount(() => {
  clearTimeout(switchModeTimer);
  clearTimeout(switchEndTimer);
});
</script>

<template>
  <header class="site-header">
    <RouterLink to="/" class="brand" aria-label="Ir al inicio">
      <span class="brand-icon" aria-hidden="true"></span>
      <span>Clonetube</span>
    </RouterLink>

    <div class="search-wrap" :class="{ focused: searchFocused, 'has-query': searchQuery }">
      <span aria-hidden="true">⌕</span>
      <input
        v-model="searchQuery"
        type="search"
        placeholder="Buscar"
        aria-label="Buscar vídeos"
        autocomplete="off"
        spellcheck="false"
        @focus="searchFocused = true"
        @blur="searchFocused = false"
        @keydown.esc="searchQuery = ''"
      />
      <button
        v-if="searchQuery"
        type="button"
        class="search-clear"
        aria-label="Limpiar búsqueda"
        @mousedown.prevent
        @click="searchQuery = ''"
      >
        ×
      </button>
    </div>

    <div v-if="user.logged_in" class="user-menu">
      <RouterLink to="/studio" class="studio-link">Studio</RouterLink>
      <div class="user-status">
        <span class="status-dot" aria-hidden="true"></span>
        {{ user.username }}
      </div>
      <button type="button" class="header-button logout" @click="signOut">
        Cerrar sesión
      </button>
    </div>
    <div v-else class="auth-actions">
      <button type="button" class="header-button secondary" @click="openAuth('login')">
        Iniciar sesión
      </button>
      <button type="button" class="header-button primary" @click="openAuth('register')">
        Registrarse
      </button>
    </div>
  </header>

  <TvModalShell
    v-if="authVisible"
    ref="authModal"
    :labelledby="`${authMode}-title`"
    :max-width="authMode === 'register' ? '42rem' : '28rem'"
    :switching="switching"
    @closing="markClosing"
    @close="finishClose"
  >
        <div class="auth-content" :class="{ 'signal-lost': switching }">
          <template v-if="registeredEmail">
            <div class="panel-heading">
              <span class="signal-label">CH 03 · VERIFICACIÓN</span>
              <h1 id="register-title">Revisa tu correo</h1>
              <p>
                Hemos enviado un enlace de verificación a
                <strong class="verify-email">{{ registeredEmail }}</strong>.
                Ábrelo para activar la cuenta @{{ registeredUsername }}.
              </p>
            </div>
            <p class="mode-switch">
              <button type="button" @click="closeAuth">Entendido</button>
            </p>
          </template>

          <template v-else-if="authMode === 'login'">
            <div class="panel-heading">
              <span class="signal-label">CH 01 · LOGIN</span>
              <h1 id="login-title">Bienvenido de nuevo</h1>
              <p>Inicia sesión para continuar en Clonetube.</p>
            </div>

            <form class="auth-form" @submit.prevent="submitLogin">
              <div class="field">
                <label for="header-login-username">Usuario</label>
                <input
                  id="header-login-username"
                  ref="loginUsernameInput"
                  v-model.trim="loginUsername"
                  type="text"
                  autocomplete="username"
                  @input="clearError"
                />
              </div>

              <div class="field">
                <label for="header-login-password">Contraseña</label>
                <input
                  id="header-login-password"
                  v-model="loginPassword"
                  type="password"
                  autocomplete="current-password"
                  :aria-invalid="Boolean(authError)"
                  @input="clearError"
                />
              </div>

              <Transition name="validation-message">
                <p v-if="authError" class="validation-error" role="alert">{{ authError }}</p>
              </Transition>

              <button class="submit-button" :disabled="!loginActive" type="submit">
                {{ loading ? 'Conectando…' : 'Iniciar sesión' }}
              </button>
            </form>

            <p class="mode-switch">
              ¿Todavía no tienes cuenta?
              <button type="button" @click="switchAuthMode('register')">Regístrate</button>
            </p>
          </template>

          <template v-else>
            <div class="panel-heading">
              <span class="signal-label">CH 02 · REGISTER</span>
              <h1 id="register-title">Crea tu cuenta</h1>
              <p>Completa tus datos para empezar.</p>
            </div>

            <form class="auth-form register-form" @submit.prevent="submitRegister">
              <div class="field">
                <label for="header-register-username">Usuario</label>
                <input
                  id="header-register-username"
                  ref="registerUsernameInput"
                  v-model.trim="registerUsername"
                  type="text"
                  autocomplete="username"
                  @input="clearError"
                />
              </div>

              <div class="field">
                <label for="header-full-name">Nombre completo</label>
                <input
                  id="header-full-name"
                  v-model.trim="fullName"
                  type="text"
                  autocomplete="name"
                  @input="clearError"
                />
              </div>

              <div class="field full-row">
                <label for="header-email">Correo electrónico</label>
                <input
                  id="header-email"
                  v-model.trim="email"
                  type="email"
                  autocomplete="email"
                  :aria-invalid="!emailIsValid"
                  @input="clearError"
                />
                <Transition name="validation-message">
                  <p v-if="!emailIsValid" class="validation-error" role="alert">
                    Introduce un correo electrónico válido.
                  </p>
                </Transition>
              </div>

              <div class="field">
                <label for="header-register-password">Contraseña</label>
                <input
                  id="header-register-password"
                  v-model="registerPassword"
                  type="password"
                  autocomplete="new-password"
                  @input="clearError"
                />
              </div>

              <div class="field">
                <label for="header-verify-password">Confirmar contraseña</label>
                <input
                  id="header-verify-password"
                  v-model="verifyPassword"
                  type="password"
                  autocomplete="new-password"
                  :aria-invalid="!passwordMatches"
                  @input="clearError"
                />
                <Transition name="validation-message">
                  <p v-if="!passwordMatches" class="validation-error" role="alert">
                    Las contraseñas no coinciden.
                  </p>
                </Transition>
              </div>

              <Transition name="validation-message">
                <p v-if="authError" class="validation-error full-row" role="alert">
                  {{ authError }}
                </p>
              </Transition>

              <button class="submit-button full-row" :disabled="!registerActive" type="submit">
                {{ loading ? 'Creando cuenta…' : 'Registrarse' }}
              </button>
            </form>

            <p class="mode-switch">
              ¿Ya tienes una cuenta?
              <button type="button" @click="switchAuthMode('login')">Inicia sesión</button>
            </p>
          </template>
        </div>
  </TvModalShell>
</template>

<style scoped>
.site-header {
  position: relative;
  z-index: 10;
  display: grid;
  grid-template-columns: 1fr minmax(12rem, 32rem) 1fr;
  align-items: center;
  gap: 1.5rem;
  min-height: 4.5rem;
  padding: 0.75rem clamp(1rem, 3vw, 2.5rem);
  border-bottom: 1px solid rgba(255, 255, 255, 0.07);
  background: rgba(15, 15, 26, 0.9);
  backdrop-filter: blur(12px);
}

.brand {
  display: inline-flex;
  align-items: center;
  gap: 0.65rem;
  width: fit-content;
  color: #f3f2ff;
  font-size: 1.05rem;
  font-weight: 800;
  letter-spacing: -0.02em;
}

.brand-icon {
  position: relative;
  display: grid;
  width: 2rem;
  height: 1.45rem;
  place-items: center;
  border-radius: 0.42rem;
  background: #6c63ff;
  box-shadow: 0 0 16px rgba(108, 99, 255, 0.35);
}

.brand-icon::after {
  width: 0;
  height: 0;
  border-top: 0.3rem solid transparent;
  border-bottom: 0.3rem solid transparent;
  border-left: 0.48rem solid #fff;
  content: '';
  transform: translateX(0.08rem);
}

.search-wrap {
  display: flex;
  align-items: center;
  gap: 0.55rem;
  min-width: 0;
  padding: 0 0.85rem;
  border: 1px solid #34334d;
  border-radius: 999px;
  background: #171725;
  color: #8f8da2;
  transition: border-color 160ms ease, box-shadow 160ms ease;
}

.search-wrap.focused {
  border-color: #6c63ff;
  box-shadow: 0 0 0 3px rgba(108, 99, 255, 0.16);
}

.search-wrap input {
  width: 100%;
  min-width: 0;
  height: 2.45rem;
  border: 0;
  outline: 0;
  background: transparent;
  color: #eeeeF7;
  font: inherit;
}

.search-clear {
  display: grid;
  flex: 0 0 auto;
  width: 1.65rem;
  height: 1.65rem;
  place-items: center;
  border: 0;
  border-radius: 50%;
  background: transparent;
  color: #a9a7b8;
  cursor: pointer;
  font: inherit;
  font-size: 1.15rem;
  line-height: 1;
}

.search-clear:hover {
  background: #2a293d;
  color: #fff;
}

.auth-actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.55rem;
}

.header-button {
  padding: 0.58rem 0.85rem;
  border: 1px solid #3c3a58;
  border-radius: 0.55rem;
  font: inherit;
  font-size: 0.86rem;
  font-weight: 700;
  white-space: nowrap;
  cursor: pointer;
  transition: border-color 160ms ease, background-color 160ms ease, transform 160ms ease;
}

.header-button:hover {
  transform: translateY(-1px);
}

.header-button.secondary {
  background: transparent;
  color: #deddeb;
}

.header-button.secondary:hover {
  border-color: #77728d;
  background: #242338;
}

.header-button.primary {
  border-color: #6c63ff;
  background: #6c63ff;
  color: #fff;
}

.header-button.primary:hover {
  background: #7c75ff;
}

.user-status {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 0.5rem;
  color: #d9d8e6;
  font-size: 0.9rem;
  font-weight: 650;
}

.user-menu {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 0.75rem;
}

.studio-link {
  padding: 0.55rem 0.75rem;
  border-radius: 0.5rem;
  color: #bdb9ff;
  font-size: 0.86rem;
  font-weight: 750;
}

.studio-link:hover {
  background: rgba(108, 99, 255, 0.12);
  color: #fff;
}

.header-button.logout {
  background: transparent;
  color: #d8d7e4;
}

.header-button.logout:hover {
  border-color: #ef6674;
  background: rgba(239, 102, 116, 0.1);
  color: #ff9aa4;
}

.status-dot {
  width: 0.5rem;
  height: 0.5rem;
  border-radius: 50%;
  background: #66df9b;
  box-shadow: 0 0 10px rgba(102, 223, 155, 0.65);
}

.auth-content {
  position: relative;
  z-index: 1;
}

.auth-content.signal-lost {
  animation: signal-loss 460ms steps(2, end) both;
}

.signal-label {
  display: block;
  margin-bottom: 0.65rem;
  color: #8f89ff;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 0.7rem;
  font-weight: 800;
  letter-spacing: 0.12em;
}

.panel-heading {
  margin-bottom: 1.45rem;
  padding-right: 2rem;
}

.panel-heading h1 {
  color: #fff;
  font-size: 1.65rem;
  line-height: 1.2;
}

.panel-heading p {
  margin-top: 0.45rem;
  color: #aaaabd;
  font-size: 0.9rem;
  line-height: 1.5;
}

.auth-form {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.register-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.field {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 0.4rem;
}

.full-row {
  grid-column: 1 / -1;
}

.field label {
  color: #d8d8e5;
  font-size: 0.84rem;
  font-weight: 650;
}

.field input {
  width: 100%;
  min-height: 2.7rem;
  padding: 0.65rem 0.8rem;
  border: 1px solid #3b3b56;
  border-radius: 0.5rem;
  outline: none;
  background: #12121e;
  color: #f1f1f6;
  font: inherit;
  transition: border-color 160ms ease, box-shadow 160ms ease;
}

.field input:focus {
  border-color: #6c63ff;
  box-shadow: 0 0 0 3px rgba(108, 99, 255, 0.2);
}

.field input[aria-invalid='true'] {
  border-color: #ef6674;
}

.validation-error {
  overflow: hidden;
  margin: 0;
  color: #ff7b88;
  font-size: 0.81rem;
  line-height: 1.4;
}

.validation-message-enter-active,
.validation-message-leave-active {
  overflow: hidden;
  transition: max-height 220ms ease, opacity 170ms ease, transform 220ms ease;
}

.validation-message-enter-from,
.validation-message-leave-to {
  max-height: 0;
  opacity: 0;
  transform: translateY(-0.2rem);
}

.validation-message-enter-to,
.validation-message-leave-from {
  max-height: 3rem;
  opacity: 1;
  transform: translateY(0);
}

.submit-button {
  width: 100%;
  min-height: 2.8rem;
  margin-top: 0.15rem;
  padding: 0.7rem 1rem;
  border: 1px solid transparent;
  border-radius: 0.5rem;
  background: #6c63ff;
  color: #fff;
  cursor: pointer;
  font: inherit;
  font-weight: 750;
  transition: background-color 160ms ease, transform 160ms ease, box-shadow 160ms ease;
}

.submit-button:not(:disabled):hover {
  background: #7c75ff;
  box-shadow: 0 8px 22px rgba(108, 99, 255, 0.23);
  transform: translateY(-1px);
}

.submit-button:disabled {
  cursor: not-allowed;
  background: #39394d;
  color: #9898a8;
}

.mode-switch {
  margin-top: 1.3rem;
  color: #aaaabd;
  font-size: 0.86rem;
  text-align: center;
}

.mode-switch button {
  padding: 0;
  border: 0;
  background: transparent;
  color: #918bff;
  cursor: pointer;
  font: inherit;
  font-weight: 700;
}

.mode-switch button:hover {
  color: #b1adff;
  text-decoration: underline;
  text-underline-offset: 0.18rem;
}

.verify-email {
  color: #bdb9ff;
  font-weight: 750;
}

@keyframes signal-loss {
  0%,
  100% { opacity: 1; transform: translate(0); }
  20% { opacity: 0.4; transform: translate(-3px, 1px); }
  48% { opacity: 0; transform: translate(4px, -1px); }
  72% { opacity: 0.3; transform: translate(-2px, 0); }
}

@media (max-width: 760px) {
  .site-header {
    grid-template-columns: 1fr auto;
  }

  .search-wrap {
    grid-column: 1 / -1;
    grid-row: 2;
  }

  .auth-actions {
    grid-column: 2;
  }

  .user-menu {
    grid-column: 2;
  }

  .register-form {
    display: flex;
  }
}

@media (max-width: 480px) {
  .site-header {
    gap: 0.75rem;
  }

  .brand > span:last-child,
  .header-button.secondary {
    display: none;
  }

}

@media (prefers-reduced-motion: reduce) {
  .auth-content.signal-lost {
    animation: none;
  }
}
</style>
