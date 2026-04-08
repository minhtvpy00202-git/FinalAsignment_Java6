<script setup>
import {EditProfilePage} from "@/legacy/pages";
import {computed} from "vue";

const {form, message, save, onPhotoChange} = EditProfilePage.setup();
const backendUrl = import.meta.env.VITE_BACKEND_URL || "";
const defaultAvatar = computed(() => {
    const seed = encodeURIComponent(String(form.fullname || "User"));
    return `https://ui-avatars.com/api/?name=${seed}&background=111827&color=ffffff`;
});
const avatarUrl = computed(() => {
    const raw = String(form.photo || "").trim();
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
const photoFileName = computed(() => form.photoFile?.name || "");
</script>

<template>
    <main class="container profile-page">
        <div class="profile-heading">
            <h3 class="page-title">Quản Lý Tài Khoản</h3>
            <p class="profile-subtitle">Cập nhật thông tin cá nhân, ảnh đại diện và bảo mật tài khoản của bạn.</p>
        </div>
        <div v-if="message" class="status-message">{{ message }}</div>
        <form class="profile-shell" @submit.prevent="save">
            <div class="profile-avatar-card">
                <img :src="avatarUrl" alt="avatar" class="profile-avatar-image">
                <div class="profile-avatar-name">{{ form.fullname || "Người dùng" }}</div>
                <div class="profile-avatar-email">{{ form.email || "Chưa cập nhật email" }}</div>
                <label class="profile-upload-btn">
                    Tải Ảnh Mới
                    <input type="file" accept="image/*" @change="onPhotoChange">
                </label>
                <div class="profile-upload-note" v-if="photoFileName">{{ photoFileName }}</div>
            </div>
            <div class="profile-form-card">
                <div class="profile-form-title">Thông Tin Cá Nhân</div>
                <div class="profile-grid">
                    <div class="form-group">
                        <label>Họ tên</label>
                        <input type="text" v-model="form.fullname" required>
                    </div>
                    <div class="form-group">
                        <label>Email</label>
                        <input type="email" v-model="form.email" required>
                    </div>
                    <div class="form-group">
                        <label>Số điện thoại</label>
                        <input type="text" v-model="form.phone" required>
                    </div>
                    <div class="form-group">
                        <label>Địa chỉ</label>
                        <input type="text" v-model="form.address" required>
                    </div>
                </div>
                <div class="profile-actions">
                    <button class="btn profile-save-btn" type="submit">Lưu Thay Đổi</button>
                    <router-link class="btn btn-outline-primary" to="/account/change-password">Đổi mật khẩu</router-link>
                </div>
            </div>
        </form>
    </main>
</template>

<style scoped>
.profile-page {
    padding: 28px 0 40px;
}

.profile-heading {
    margin-bottom: 14px;
}

.profile-subtitle {
    margin: 4px 0 0;
    color: #6b7280;
    font-size: 14px;
}

.profile-shell {
    display: grid;
    grid-template-columns: 330px 1fr;
    gap: 22px;
}

.profile-avatar-card,
.profile-form-card {
    background: #ffffff;
    border: 1px solid #edf0f5;
    border-radius: 18px;
    padding: 22px;
    box-shadow: 0 10px 28px rgba(15, 23, 42, 0.08);
}

.profile-avatar-card {
    display: flex;
    flex-direction: column;
    align-items: center;
    text-align: center;
    background: linear-gradient(180deg, #fafcff 0%, #ffffff 100%);
}

.profile-avatar-image {
    width: 116px;
    height: 116px;
    border-radius: 50%;
    object-fit: cover;
    border: 4px solid #f3f4f6;
    margin-bottom: 12px;
}

.profile-avatar-name {
    font-size: 18px;
    font-weight: 700;
    color: #111827;
}

.profile-avatar-email {
    font-size: 13px;
    color: #6b7280;
    margin-top: 4px;
    margin-bottom: 14px;
    word-break: break-word;
}

.profile-upload-btn {
    width: 100%;
    border: 1px dashed #cfd8e3;
    border-radius: 12px;
    background: #f8fbff;
    color: #111827;
    font-size: 14px;
    font-weight: 600;
    padding: 11px 12px;
    cursor: pointer;
}

.profile-upload-btn input {
    display: none;
}

.profile-upload-note {
    margin-top: 8px;
    font-size: 12px;
    color: #6b7280;
}

.profile-form-title {
    font-size: 18px;
    font-weight: 700;
    color: #0f172a;
    margin-bottom: 12px;
}

.profile-grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 16px;
}

.profile-form-card :deep(.form-group) {
    margin-bottom: 0;
}

.profile-form-card :deep(label) {
    display: block;
    font-size: 12px;
    font-weight: 700;
    color: #6b7280;
    text-transform: uppercase;
    letter-spacing: .04em;
    margin-bottom: 8px;
}

.profile-form-card :deep(input) {
    width: 100%;
    height: 44px;
    border-radius: 12px;
    border: 1px solid #dbe1ea;
    padding: 0 14px;
    font-size: 15px;
    color: #111827;
    background: #ffffff;
    transition: border-color .2s ease, box-shadow .2s ease;
}

.profile-form-card :deep(input:focus) {
    outline: none;
    border-color: #111827;
    box-shadow: 0 0 0 3px rgba(17, 24, 39, 0.08);
}

.profile-actions {
    display: flex;
    gap: 10px;
    margin-top: 18px;
    align-items: center;
}

.profile-save-btn {
    min-width: 150px;
}

@media (max-width: 992px) {
    .profile-shell {
        grid-template-columns: 1fr;
    }
    .profile-grid {
        grid-template-columns: 1fr;
    }
}
</style>
