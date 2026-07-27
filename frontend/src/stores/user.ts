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
        try {
            await reload();
            logged_in.value = true;
        } catch (error) {
            logout();
            throw error;
        }
    }

    async function initialize() {
        const storedToken = localStorage.getItem("token");
        if (!storedToken) return;

        token.value = {
            access_token: storedToken,
            token_type: 'bearer'
        };

        try {
            await reload();
            logged_in.value = true;
        } catch {
            // A token in localStorage is only a session candidate until the API confirms it.
            logout();
        }
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

    return {
        user,
        token,
        logged_in,
        username,
        login,
        register,
        initialize,
        reload,
        logout,
    }
})
