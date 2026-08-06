import { defineStore } from 'pinia';
import { ref } from 'vue';
import { useUserStore } from '@/stores/user';
import { getPoints } from '@/api/points';

export const usePointsStore = defineStore('points', () => {
    const balance = ref(0);

    async function refresh() {
        const user = useUserStore();
        const token = user.token?.access_token;
        if (!token) {
            balance.value = 0;
            return;
        }
        try {
            balance.value = await getPoints(token);
        } catch {
            balance.value = 0;
        }
    }

    return {
        balance,
        refresh,
    };
});
