<script setup>
import {ChangePasswordPage} from "@/legacy/pages";
import {computed, onBeforeUnmount, ref, watch} from "vue";
import AppToast from "@/components/AppToast.vue";

const {form, message, submit} = ChangePasswordPage.setup();
const submitting = ref(false);
const toastOpen = ref(false);
const toastText = ref("");
const showCurrentPassword = ref(false);
const showNewPassword = ref(false);
// Chỉ giữ lại lỗi để hiển thị inline ngay trên form, tránh popup làm ngắt luồng nhập liệu.
const errorText = computed(() => {
    const text = String(message.value || "").trim();
    if (!text) return "";
    return text.toLowerCase().includes("thành công") ? "" : text;
});
let toastTimer = null;
// Khi backend trả thông báo thành công thì hiển thị toast ngắn, không chặn người dùng.
watch(message, (value) => {
    const text = String(value || "").trim();
    if (!text || !text.toLowerCase().includes("thành công")) return;
    toastText.value = text;
    toastOpen.value = true;
    if (toastTimer) {
        clearTimeout(toastTimer);
    }
    toastTimer = setTimeout(() => {
        toastOpen.value = false;
    }, 2300);
});
const submitForm = async () => {
    // Submit luôn đi qua hàm `submit()` của page logic để thống nhất xử lý với API.
    submitting.value = true;
    try {
        await submit();
    } finally {
        submitting.value = false;
    }
};
onBeforeUnmount(() => {
    if (toastTimer) {
        clearTimeout(toastTimer);
    }
});
</script>

<template>
    <main class="container change-password-page">
        <div class="change-password-shell">
            <div class="change-password-side">
                <h3 class="page-title">Đổi Mật Khẩu</h3>
                <p class="change-password-note">Mật khẩu mới tối thiểu 8 ký tự và bao gồm CHỮ HOA, chữ THƯỜNG, SỐ và KÝ TỰ ĐẶC BIỆT.</p>
            </div>
            <div class="change-password-card">
                <div v-if="errorText" class="status-message status-error">{{ errorText }}</div>
                <form @submit.prevent="submitForm">
                    <div class="form-group">
                        <label>Mật khẩu hiện tại</label>
                        <div class="password-field">
                            <input :type="showCurrentPassword ? 'text' : 'password'" v-model="form.currentPassword" required>
                            <button
                                class="password-toggle"
                                type="button"
                                :aria-label="showCurrentPassword ? 'Ẩn mật khẩu hiện tại' : 'Hiện mật khẩu hiện tại'"
                                :title="showCurrentPassword ? 'Ẩn mật khẩu hiện tại' : 'Hiện mật khẩu hiện tại'"
                                @click="showCurrentPassword = !showCurrentPassword"
                            >
                                <svg v-if="!showCurrentPassword" width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                                    <path d="M2 12C4.5 7.8 8 5.5 12 5.5C16 5.5 19.5 7.8 22 12C19.5 16.2 16 18.5 12 18.5C8 18.5 4.5 16.2 2 12Z" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>
                                    <circle cx="12" cy="12" r="3.2" stroke="currentColor" stroke-width="1.8"/>
                                </svg>
                                <svg v-else width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                                    <path d="M3 3L21 21" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/>
                                    <path d="M10.6 6.1C11.1 5.9 11.5 5.8 12 5.8C16 5.8 19.5 8 22 12C21.2 13.3 20.3 14.4 19.3 15.3" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>
                                    <path d="M6.2 8.2C4.7 9.2 3.3 10.5 2 12C4.5 16 8 18.2 12 18.2C13.6 18.2 15 17.8 16.3 17.1" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>
                                    <path d="M9.9 9.9C9.3 10.5 9 11.2 9 12C9 13.7 10.3 15 12 15C12.8 15 13.5 14.7 14.1 14.1" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/>
                                </svg>
                            </button>
                        </div>
                    </div>
                    <div class="form-group">
                        <label>Mật khẩu mới</label>
                        <div class="password-field">
                            <input :type="showNewPassword ? 'text' : 'password'" v-model="form.newPassword" required>
                            <button
                                class="password-toggle"
                                type="button"
                                :aria-label="showNewPassword ? 'Ẩn mật khẩu mới' : 'Hiện mật khẩu mới'"
                                :title="showNewPassword ? 'Ẩn mật khẩu mới' : 'Hiện mật khẩu mới'"
                                @click="showNewPassword = !showNewPassword"
                            >
                                <svg v-if="!showNewPassword" width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                                    <path d="M2 12C4.5 7.8 8 5.5 12 5.5C16 5.5 19.5 7.8 22 12C19.5 16.2 16 18.5 12 18.5C8 18.5 4.5 16.2 2 12Z" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>
                                    <circle cx="12" cy="12" r="3.2" stroke="currentColor" stroke-width="1.8"/>
                                </svg>
                                <svg v-else width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                                    <path d="M3 3L21 21" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/>
                                    <path d="M10.6 6.1C11.1 5.9 11.5 5.8 12 5.8C16 5.8 19.5 8 22 12C21.2 13.3 20.3 14.4 19.3 15.3" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>
                                    <path d="M6.2 8.2C4.7 9.2 3.3 10.5 2 12C4.5 16 8 18.2 12 18.2C13.6 18.2 15 17.8 16.3 17.1" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>
                                    <path d="M9.9 9.9C9.3 10.5 9 11.2 9 12C9 13.7 10.3 15 12 15C12.8 15 13.5 14.7 14.1 14.1" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/>
                                </svg>
                            </button>
                        </div>
                    </div>
                    <button class="btn btn-primary change-password-btn" type="submit" :disabled="submitting">
                        {{ submitting ? "Đang cập nhật..." : "Đổi mật khẩu" }}
                    </button>
                </form>
            </div>
        </div>
        <AppToast :open="toastOpen" :text="toastText" type="success" />
    </main>
</template>

<style scoped>
.change-password-page {
    padding: 26px 0 40px;
}

.change-password-shell {
    display: grid;
    grid-template-columns: 320px 1fr;
    gap: 22px;
}

.change-password-side,
.change-password-card {
    background: #fff;
    border: 1px solid #eceff4;
    border-radius: 16px;
    box-shadow: 0 10px 30px rgba(15, 23, 42, 0.08);
    padding: 22px;
}

.change-password-note {
    color: #64748b;
    font-size: 14px;
    margin: 6px 0 0;
    line-height: 1.6;
}

.change-password-card :deep(.form-group) {
    margin-bottom: 14px;
}

.change-password-card :deep(label) {
    display: block;
    font-size: 12px;
    font-weight: 700;
    color: #6b7280;
    text-transform: uppercase;
    letter-spacing: .04em;
    margin-bottom: 8px;
}

.change-password-card :deep(input) {
    width: 100%;
    height: 44px;
    border-radius: 12px;
    border: 1px solid #dbe1ea;
    padding: 0 14px;
    font-size: 15px;
}
.password-field{
    position:relative;
}
.change-password-card .password-field :deep(input){
    padding-right:48px;
}
.password-toggle{
    position:absolute;
    right:8px;
    top:50%;
    transform:translateY(-50%);
    border:0;
    background:transparent;
    color:#374151;
    width:30px;
    height:30px;
    display:flex;
    align-items:center;
    justify-content:center;
    cursor:pointer;
}

.change-password-card :deep(input:focus) {
    outline: none;
    border-color: #111827;
    box-shadow: 0 0 0 3px rgba(17, 24, 39, 0.08);
}

.change-password-btn {
    margin-top: 8px;
    min-width: 180px;
}

@media (max-width: 992px) {
    .change-password-shell {
        grid-template-columns: 1fr;
    }
}
</style>
