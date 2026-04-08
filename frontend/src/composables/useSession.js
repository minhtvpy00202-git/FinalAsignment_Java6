import {computed, reactive} from "vue";
import {api} from "@/api";

const state = reactive({
    me: null,
    loaded: false
});

const refreshSession = async () => {
    try {
        state.me = (await api.auth.me()).data || null;
    } catch (e) {
        state.me = null;
    } finally {
        state.loaded = true;
    }
    return state.me;
};

const clearSession = () => {
    state.me = null;
    state.loaded = true;
};

export const useSession = () => {
    const isAuthenticated = computed(() => {
        const username = String(state.me?.username || "").trim();
        return username !== "" && username !== "anonymousUser";
    });
    const isAdmin = computed(() => {
        const roles = state.me?.roles || [];
        return roles.some((role) => {
            if (typeof role === "string") {
                return role === "ADMIN" || role === "ROLE_ADMIN";
            }
            if (role && typeof role === "object") {
                const value = role.id || role.name || role.authority || role.role || "";
                return value === "ADMIN" || value === "ROLE_ADMIN";
            }
            return false;
        });
    });
    return {state, isAuthenticated, isAdmin, refreshSession, clearSession};
};
