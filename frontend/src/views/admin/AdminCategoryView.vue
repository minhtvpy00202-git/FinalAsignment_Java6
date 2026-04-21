<script setup>
import {computed, ref} from "vue";
import {AdminCategoryPage} from "@/legacy/pages";
import AdminNav from "@/components/AdminNav.vue";
import AppToast from "@/components/AppToast.vue";

const {rows, modal, openEdit, save} = AdminCategoryPage.setup();
const modalOpen = ref(false);
const saving = ref(false);
const mode = ref("create");
const title = computed(() => mode.value === "edit" ? "Chỉnh sửa danh mục" : "Thêm danh mục");
const formError = ref("");
const toastOpen = ref(false);
const toastText = ref("");
let toastTimer = null;
const filterKeyword = ref("");
const filteredRows = computed(() => {
    const keyword = String(filterKeyword.value || "").trim().toLowerCase();
    if (!keyword) return rows.value || [];
    return (rows.value || []).filter((item) => {
        return String(item?.id || "").toLowerCase().includes(keyword)
            || String(item?.name || "").toLowerCase().includes(keyword);
    });
});

const resetFormState = () => {
    modal.id = "";
    modal.name = "";
    modal.editing = false;
};

const closeFormModal = () => {
    if (saving.value) return;
    modalOpen.value = false;
    formError.value = "";
    resetFormState();
};

const nextCategoryId = () => {
    let max = 0;
    let maxDigits = 2;
    (rows.value || []).forEach((item) => {
        const raw = String(item?.id || "").toUpperCase().replace(/\s+/g, "");
        const matched = raw.match(/^CAT(\d+)$/);
        if (!matched) return;
        const digits = matched[1];
        const value = Number(digits);
        if (!Number.isFinite(value)) return;
        if (value > max) max = value;
        if (digits.length > maxDigits) maxDigits = digits.length;
    });
    const next = max + 1;
    return `CAT${String(next).padStart(Math.max(2, maxDigits), "0")}`;
};

const openCreateModal = () => {
    mode.value = "create";
    modal.editing = false;
    modal.id = nextCategoryId();
    modal.name = "";
    modalOpen.value = true;
};

const openEditModal = async (id) => {
    mode.value = "edit";
    await openEdit(id);
    modalOpen.value = true;
};

const submit = async () => {
    if (saving.value) return;
    saving.value = true;
    formError.value = "";
    try {
        await save();
        toastText.value = mode.value === "edit" ? "Cập nhật danh mục thành công." : "Thêm danh mục thành công.";
        toastOpen.value = true;
        if (toastTimer) clearTimeout(toastTimer);
        toastTimer = setTimeout(() => {
            toastOpen.value = false;
        }, 2300);
        closeFormModal();
    } catch (e) {
        formError.value = e.message || "Không thể lưu danh mục.";
    } finally {
        saving.value = false;
    }
};
const resetFilter = () => {
    filterKeyword.value = "";
};
</script>

<template>
    <main class="container admin-product-page">
        <h3 class="page-title">Quản lý danh mục</h3>
        <div class="admin-product-shell">
            <div class="admin-product-menu">
                <AdminNav/>
            </div>
            <div class="admin-product-main">
                <div class="card admin-category-list">
                    <div class="table-actions" style="justify-content: space-between; margin-bottom: 12px;">
                        <h4 style="margin:0;">Danh sách</h4>
                        <div class="table-actions" style="gap:8px;">
                            <input v-model.trim="filterKeyword" class="form-control" style="max-width:260px;" placeholder="Tìm mã loại hoặc tên loại">
                            <button class="btn btn-outline-primary" type="button" @click="resetFilter">Làm mới lọc</button>
                            <button class="btn btn-primary" type="button" @click="openCreateModal">Thêm</button>
                        </div>
                    </div>
                    <table>
                        <thead>
                        <tr>
                            <th>Mã loại</th>
                            <th>Tên loại</th>
                            <th></th>
                        </tr>
                        </thead>
                        <tbody>
                        <tr v-for="c in filteredRows" :key="c.id">
                            <td>{{ c.id }}</td>
                            <td>{{ c.name }}</td>
                            <td class="table-actions">
                                <button class="btn btn-action-outline" type="button" @click="openEditModal(c.id)">Sửa</button>
                            </td>
                        </tr>
                        <tr v-if="!filteredRows.length">
                            <td colspan="3" style="text-align:center;color:#6b7280;padding:18px;">Không có danh mục phù hợp bộ lọc.</td>
                        </tr>
                        </tbody>
                    </table>
                </div>
            </div>
        </div>
        <div class="modal-backdrop" :class="{open: modalOpen}" v-if="modalOpen" @click.self="closeFormModal">
            <div class="admin-modal-panel" style="max-width: 520px;">
                <div class="modal-header">
                    <h4>{{ title }}</h4>
                    <button type="button" class="btn btn-outline-primary" @click="closeFormModal">Đóng</button>
                </div>
                <form class="admin-form-grid" @submit.prevent="submit">
                    <div class="form-group">
                        <label>Mã loại</label>
                        <input type="text" v-model="modal.id" readonly class="form-control">
                    </div>
                    <div class="form-group">
                        <label>Tên loại</label>
                        <input type="text" v-model="modal.name" required class="form-control">
                    </div>
                    <div v-if="formError" class="status-message status-error">{{ formError }}</div>
                    <div class="admin-form-actions">
                        <button class="btn btn-primary" type="submit" :disabled="saving">{{ saving ? "Đang lưu..." : "Lưu" }}</button>
                        <button class="btn btn-outline-primary" type="button" @click="closeFormModal" :disabled="saving">Huỷ</button>
                    </div>
                </form>
            </div>
        </div>
        <AppToast :open="toastOpen" :text="toastText" type="success" />
    </main>
</template>
