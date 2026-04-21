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
                            <button class="password-toggle" type="button" @click="showCurrentPassword = !showCurrentPassword">
                                {{ showCurrentPassword ? "Ẩn" : "Hiện" }}
                            </button>
                        </div>
                    </div>
                    <div class="form-group">
                        <label>Mật khẩu mới</label>
                        <div class="password-field">
                            <input :type="showNewPassword ? 'text' : 'password'" v-model="form.newPassword" required>
                            <button class="password-toggle" type="button" @click="showNewPassword = !showNewPassword">
                                {{ showNewPassword ? "Ẩn" : "Hiện" }}
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
