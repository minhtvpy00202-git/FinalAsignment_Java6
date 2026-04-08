<script setup>
import {computed, onMounted, onUnmounted, ref, watch} from "vue";
import {useRouter} from "vue-router";
import {api} from "@/api";
import {useSession} from "@/composables/useSession";

const router = useRouter();
const {state, isAuthenticated, isAdmin, refreshSession, clearSession} = useSession();
const notifications = ref([]);
const unreadCount = ref(0);
const bellOpen = ref(false);
const profileOpen = ref(false);
const bellRef = ref(null);
const profileRef = ref(null);
let pollTimer = null;
const backendUrl = import.meta.env.VITE_BACKEND_URL || "";
const displayName = computed(() => String(state.me?.fullname || state.me?.username || "Người dùng"));
const defaultAvatar = computed(() => {
    const seed = encodeURIComponent(displayName.value);
    return `https://ui-avatars.com/api/?background=F4F4F5&color=111827&name=${seed}`;
});
const profilePhotoUrl = computed(() => {
    const raw = String(state.me?.photo || "").trim();
    if (!raw) {
        return defaultAvatar.value;
    }
    if (raw.startsWith("http://") || raw.startsWith("https://") || raw.startsWith("data:") || raw.startsWith("/")) {
        return raw;
    }
    if (backendUrl) {
        return `${backendUrl}/images/${encodeURIComponent(raw)}`;
    }
    return `/images/${encodeURIComponent(raw)}`;
});

const loadNotifications = async () => {
    if (!isAuthenticated.value) {
        notifications.value = [];
        unreadCount.value = 0;
        return;
    }
    try {
        const [latestRes, countRes] = await Promise.all([
            api.notifications.latest(100),
            api.notifications.unreadCount()
        ]);
        notifications.value = latestRes?.data || [];
        unreadCount.value = countRes?.data?.count || 0;
    } catch (e) {
    }
};
const startPolling = () => {
    if (pollTimer) {
        clearInterval(pollTimer);
    }
    pollTimer = setInterval(loadNotifications, 10000);
};
const stopPolling = () => {
    if (pollTimer) {
        clearInterval(pollTimer);
        pollTimer = null;
    }
};
const toggleBell = async () => {
    bellOpen.value = !bellOpen.value;
    if (bellOpen.value) {
        profileOpen.value = false;
    }
    if (bellOpen.value) {
        await loadNotifications();
    }
};
const toggleProfile = () => {
    profileOpen.value = !profileOpen.value;
    if (profileOpen.value) {
        bellOpen.value = false;
    }
};
const onGlobalClick = (event) => {
    const target = event?.target;
    if (bellRef.value && !bellRef.value.contains(target)) {
        bellOpen.value = false;
    }
    if (profileRef.value && !profileRef.value.contains(target)) {
        profileOpen.value = false;
    }
};
const goProfile = async () => {
    profileOpen.value = false;
    await router.push("/account/edit-profile");
};
const openNotification = async (notification) => {
    if (!notification?.id) {
        return;
    }
    try {
        const res = await api.notifications.read(notification.id);
        const redirectUrl = res.data?.redirectUrl || notification.redirectUrl || "/home/index";
        bellOpen.value = false;
        await loadNotifications();
        await router.push(redirectUrl);
    } catch (e) {
    }
};

const hoverNotification = async (notification) => {
    if (notification.read) return;
    
    notification.read = true;
    unreadCount.value = Math.max(0, unreadCount.value - 1);
    
    try {
        await api.notifications.read(notification.id);
    } catch (e) {
    }
};

const logout = async () => {
    try {
        await api.auth.logout();
    } catch (e) {
    } finally {
        clearSession();
        stopPolling();
        notifications.value = [];
        unreadCount.value = 0;
        await router.push("/home/index");
    }
};

onMounted(async () => {
    await refreshSession();
    await loadNotifications();
    startPolling();
    document.addEventListener("click", onGlobalClick);
});

watch(isAuthenticated, async (value) => {
    if (!value) {
        notifications.value = [];
        unreadCount.value = 0;
        bellOpen.value = false;
        return;
    }
    await loadNotifications();
});

onUnmounted(() => {
    stopPolling();
    document.removeEventListener("click", onGlobalClick);
});
</script>

<template>
    <header class="app-header">
        <nav class="navbar navbar-expand-lg">
            <div class="container">
                <router-link class="navbar-brand" to="/home/index">
                    <span class="brand-text">FASHION</span>
                    <span class="brand-accent">STORE</span>
                </router-link>
                <button class="navbar-toggler" type="button" data-bs-toggle="collapse" data-bs-target="#mainNav">
                    <span class="navbar-toggler-icon"></span>
                </button>
                <div class="collapse navbar-collapse" id="mainNav">
                    <ul class="navbar-nav mx-auto mb-2 mb-lg-0">
                        <li class="nav-item"><router-link class="nav-link" to="/home/index">Trang chủ</router-link></li>
                        <li class="nav-item"><router-link class="nav-link" to="/product/list">Sản phẩm</router-link></li>
                        <li class="nav-item"><router-link class="nav-link" to="/cart/index">Giỏ hàng</router-link></li>
                        <li class="nav-item"><router-link class="nav-link" to="/order/order-list">Đơn hàng</router-link></li>
                        <li class="nav-item" v-if="isAdmin"><router-link class="nav-link" to="/admin/product">Quản trị</router-link></li>
                    </ul>
                    <div class="header-actions" v-if="!isAuthenticated">
                        <router-link class="btn btn-outline-primary btn-sm" to="/auth/login">Đăng nhập</router-link>
                        <router-link class="btn btn-primary btn-sm" to="/account/sign-up">Đăng ký</router-link>
                    </div>
                    <div class="header-user" v-else>
                        <div class="notification-bell-wrap" ref="bellRef">
                            <button class="notification-bell-btn" type="button" @click="toggleBell">
                                🔔
                                <span class="notification-badge" v-if="unreadCount > 0">{{ unreadCount }}</span>
                            </button>
                            <div class="notification-dropdown" v-if="bellOpen" style="max-height: 400px; overflow-y: auto;">
                                <div class="notification-dropdown-title">Thông báo</div>
                                <button class="notification-item" :class="{ 'unread': !item.read }" type="button" v-for="item in notifications" :key="item.id" @click="openNotification(item)" @mouseenter="hoverNotification(item)">
                                    <div class="notification-item-title">
                                        <span class="unread-dot" v-if="!item.read"></span>
                                        {{ item.title }}
                                    </div>
                                    <div class="notification-item-content">{{ item.content }}</div>
                                </button>
                                <div class="notification-empty" v-if="!notifications.length">Chưa có thông báo</div>
                            </div>
                        </div>
                        <div class="profile-menu-wrap" ref="profileRef">
                            <button class="profile-menu-trigger" type="button" @click="toggleProfile">
                                <img :src="profilePhotoUrl" alt="avatar" class="profile-avatar">
                                <span class="user-name">{{ displayName }}</span>
                            </button>
                            <div class="profile-dropdown" v-if="profileOpen">
                                <button class="profile-dropdown-item" type="button" @click="goProfile">Quản lý tài khoản</button>
                                <button class="profile-dropdown-item profile-dropdown-item--danger" type="button" @click="logout">Đăng xuất</button>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </nav>
    </header>
</template>
