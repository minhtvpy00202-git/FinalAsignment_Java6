<script setup>
import {LoginPage} from "@/legacy/pages";
import {computed, ref} from "vue";
import {useRoute, useRouter} from "vue-router";
import {useSession} from "@/composables/useSession";

const {form, me, loading, error, login} = LoginPage.setup();
const router = useRouter();
const route = useRoute();
const {refreshSession} = useSession();
const redirectTo = computed(() => {
    const redirect = route.query.redirect;
    if (typeof redirect === "string" && redirect.startsWith("/")) {
        return redirect;
    }
    return "/home/index";
});
const requiredLoginMessage = computed(() => {
    const message = route.query.message;
    if (typeof message === "string" && message.trim()) {
        return message.trim();
    }
    return "";
});
const googleLoginUrl = computed(() => `/oauth2/authorization/google?redirect=${encodeURIComponent(redirectTo.value)}`);
const showPassword = ref(false);
const submitLogin = async () => {
    await login();
    if (!me.value) {
        return;
    }
    await refreshSession();
    await router.push(redirectTo.value);
};
</script>

<template>
    <main class="container auth-page">
        <div class="auth-card">
            <div class="auth-card__header">
                <h1 class="auth-card__title">Đăng nhập</h1>
                <p class="auth-card__subtitle">Chào mừng bạn quay trở lại!</p>
            </div>
            <div class="auth-card__body">
                <div v-if="requiredLoginMessage" class="alert alert--error mb-3">{{ requiredLoginMessage }}</div>
                <div v-if="error" class="alert alert--error mb-3">{{ error }}</div>
                <form class="auth-form" @submit.prevent="submitLogin">
                    <div class="form-group">
                        <label for="username" class="form-label">Tài khoản</label>
                        <input id="username" type="text" v-model="form.username" class="form-control" placeholder="Nhập tài khoản" required>
                    </div>
                    <div class="form-group">
                        <label for="password" class="form-label">Mật khẩu</label>
                        <div class="password-field">
                            <input id="password" :type="showPassword ? 'text' : 'password'" v-model="form.password" class="form-control" placeholder="Nhập mật khẩu" required>
                            <button class="password-toggle" type="button" @click="showPassword = !showPassword">
                                {{ showPassword ? "Ẩn" : "Hiện" }}
                            </button>
                        </div>
                    </div>
                    <div class="form-group form-group--row">
                        <router-link class="form-link" to="/account/forgot-password">Quên mật khẩu?</router-link>
                    </div>
                    <button class="btn btn--primary btn--block" type="submit" :disabled="loading">Đăng nhập</button>
                </form>
                <div class="divider"><span>Hoặc</span></div>
                <a class="btn btn--outline btn--block btn--social-google" :href="googleLoginUrl">
                    <img src="https://developers.google.com/identity/images/g-logo.png" alt="Google" width="20" height="20">
                    <span>Đăng nhập với Google</span>
                </a>
                <div v-if="me" class="card mt-3"><div class="card-body"><div><strong>User:</strong> {{ me.username }}</div><div><strong>Roles:</strong> {{ (me.roles || []).join(', ') }}</div></div></div>
            </div>
            <div class="auth-card__footer">
                <p>Chưa có tài khoản? <router-link to="/account/sign-up">Đăng ký ngay</router-link></p>
            </div>
        </div>
    </main>
</template>

<style scoped>
.password-field{
    position:relative;
}
.password-field .form-control{
    padding-right:72px;
}
.password-toggle{
    position:absolute;
    right:8px;
    top:50%;
    transform:translateY(-50%);
    border:0;
    background:transparent;
    color:#374151;
    font-size:13px;
    font-weight:700;
    cursor:pointer;
}
</style>
