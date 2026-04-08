<script setup>
import {ChangePasswordPage} from "@/legacy/pages";
import {ref} from "vue";

const {form, message, submit} = ChangePasswordPage.setup();
const submitting = ref(false);
const submitForm = async () => {
    submitting.value = true;
    try {
        await submit();
    } finally {
        submitting.value = false;
    }
};
</script>

<template>
    <main class="container change-password-page">
        <div class="change-password-shell">
            <div class="change-password-side">
                <h3 class="page-title">Đổi Mật Khẩu</h3>
                <p class="change-password-note">Mật khẩu mới tối thiểu 8 ký tự và bao gồm CHỮ HOA, chữ THƯỜNG, SỐ và KÝ TỰ ĐẶC BIỆT.</p>
            </div>
            <div class="change-password-card">
                <div v-if="message" class="status-message" :class="message.includes('thành công') ? 'status-success' : 'status-error'">{{ message }}</div>
                <form @submit.prevent="submitForm">
                    <div class="form-group">
                        <label>Mật khẩu hiện tại</label>
                        <input type="password" v-model="form.currentPassword" required>
                    </div>
                    <div class="form-group">
                        <label>Mật khẩu mới</label>
                        <input type="password" v-model="form.newPassword" required>
                    </div>
                    <button class="btn btn-primary change-password-btn" type="submit" :disabled="submitting">
                        {{ submitting ? "Đang cập nhật..." : "Đổi mật khẩu" }}
                    </button>
                </form>
            </div>
        </div>
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
