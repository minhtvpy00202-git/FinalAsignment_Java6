<script setup>
import {computed} from "vue";
import {AdminAccountPage} from "@/legacy/pages";
import {useSession} from "@/composables/useSession";
import AdminNav from "@/components/AdminNav.vue";

const {rows, roles, form, modalOpen, editing, msg, edit, openCreate, closeModal, onPhotoChange, save, remove} = AdminAccountPage.setup();
const {state} = useSession();
const currentUsername = computed(() => state.me?.username || "");
</script>

<template>
    <main class="container admin-product-page">
        <h3 class="page-title">Quản lý tài khoản</h3>
        <div class="admin-product-shell">
            <div class="admin-product-menu">
                <AdminNav/>
            </div>
            <div class="admin-product-main">
            <div class="modal-backdrop" :class="{open: modalOpen}" v-if="modalOpen">
                <div class="admin-modal-panel">
                    <form @submit.prevent="save">
                        <h4>{{ editing ? "Cập nhật tài khoản" : "Thêm tài khoản" }}</h4>
                        <div class="admin-form-grid">
                            <div class="form-group">
                                <label>Username</label>
                                <input type="text" v-model="form.username" :readonly="editing" required class="form-control">
                            </div>
                            <div class="form-group">
                                <label>Mật khẩu</label>
                                <input type="password" v-model="form.password" class="form-control" :placeholder="editing ? 'Để trống nếu không đổi' : 'Nhập mật khẩu'" :required="!editing">
                            </div>
                            <div class="form-group">
                                <label>Họ tên</label>
                                <input type="text" v-model="form.fullname" required class="form-control">
                            </div>
                            <div class="form-group">
                                <label>Email</label>
                                <input type="email" v-model="form.email" required class="form-control">
                            </div>
                            <div class="form-group">
                                <label>Số điện thoại</label>
                                <input type="text" v-model.trim="form.phone" required class="form-control" pattern="^(0|\+84)\d{9,10}$" placeholder="VD: 0912345678">
                            </div>
                            <div class="form-group">
                                <label>Địa chỉ</label>
                                <input type="text" v-model.trim="form.address" required class="form-control" placeholder="Nhập địa chỉ">
                            </div>
                            <div class="form-group">
                                <label>Vai trò</label>
                                <select v-model="form.roleId" class="form-control">
                                    <option value="">Chọn vai trò</option>
                                    <option v-for="r in roles" :key="r.id" :value="r.id">{{ r.id }}</option>
                                </select>
                            </div>
                            <div class="form-group">
                                <label>Kích hoạt</label>
                                <div class="checkbox-wrapper">
                                    <input type="checkbox" v-model="form.activated" id="activated">
                                    <label for="activated" class="checkbox-label">Tài khoản đang hoạt động</label>
                                </div>
                            </div>
                            <div class="form-group full-span">
                                <label>Ảnh đại diện</label>
                                <input type="file" accept="image/*" class="form-control" @change="onPhotoChange">
                                <div class="form-hint">Ảnh hiện tại: {{ form.photo || "Chưa có" }}</div>
                            </div>
                            <div class="admin-form-actions">
                                <button class="btn btn-primary" type="submit">{{ editing ? "Cập nhật" : "Thêm" }}</button>
                                <button class="btn btn-outline-primary" type="button" @click="closeModal">Huỷ</button>
                            </div>
                        </div>
                    </form>
                </div>
            </div>
            <div class="status-message" v-if="msg">{{ msg }}</div>
            <div class="card admin-product-list">
                <div class="admin-product-list-header">
                    <h4>Danh sách tài khoản</h4>
                    <button class="btn btn-primary btn-add" type="button" @click="openCreate">
                        <svg width="16" height="16" viewBox="0 0 16 16" fill="none" xmlns="http://www.w3.org/2000/svg">
                            <path d="M8 1V15M1 8H15" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
                        </svg>
                        Thêm tài khoản
                    </button>
                </div>
                <table>
                    <thead>
                    <tr>
                        <th>Username</th>
                        <th>Họ tên</th>
                        <th>Email</th>
                        <th>Số điện thoại</th>
                        <th>Địa chỉ</th>
                        <th>Vai trò</th>
                        <th></th>
                    </tr>
                    </thead>
                    <tbody>
                    <tr v-for="u in rows" :key="u.username">
                        <td>{{ u.username }}</td>
                        <td>{{ u.fullname }}</td>
                        <td>{{ u.email }}</td>
                        <td>{{ u.phone }}</td>
                        <td>{{ u.address }}</td>
                        <td>{{ u.roleId === "ADMIN" ? "Quản trị viên" : "Người dùng" }}</td>
                        <td class="table-actions">
                            <button class="btn btn-action-outline" type="button" @click="edit(u.username)">Sửa</button>
                            <button class="btn btn-action-solid" type="button" v-if="u.username !== currentUsername" @click="remove(u.username)">Xoá</button>
                        </td>
                    </tr>
                    </tbody>
                </table>
            </div>
        </div>
        </div>
    </main>
</template>
