import { defineStore } from 'pinia';
import { computed, ref } from 'vue';
import type { Ref } from 'vue';
import type { User, Token } from '@/types';
import axios from 'axios';

interface RegisterPayload {
    username: string;
    password: string;
    full_name: string;
    email: string;
}

export const useUserStore = defineStore('user', () => {
    const user: Ref<User|null> = ref(null);
    const token: Ref<Token|null> = ref(null);
    const logged_in: Ref<boolean> = ref(false);

    const username = computed(() => user.value?.username);

    async function login(username: string, password: string) {
        const response = await axios.post<Token>("/api/v1/auth/login", {
            username,
            password
        });
        await finishAuthentication(response.data);
    }

    async function register(payload: RegisterPayload) {
        const response = await axios.post<Token>("/api/v1/auth/register", payload);
        await finishAuthentication(response.data);
    }

    async function finishAuthentication(authToken: Token) {
        token.value = authToken;
        localStorage.setItem("token", authToken.access_token);
        await reload();
        logged_in.value = true;
    }

    function init() {
        const tok_string = localStorage.getItem("token");
        if (tok_string === null) return;
        logged_in.value = true;
        token.value = {
            access_token: tok_string,
            token_type: 'bearer'
        }
        reload();
    }

    async function reload() {
        if (token.value===null) return;
        let response = await axios.get("/api/v1/auth/me", {
            headers: {
                Authorization: `Bearer ${token.value.access_token}`
            }
        })
        user.value = response.data;
    }

    function logout() {
        user.value = null;
        token.value = null;
        logged_in.value = false;
        localStorage.removeItem("token");
    }

    init();
    return {
        user,
        token,
        logged_in,
        username,
        login,
        register,
        reload,
        logout,
    }
})
