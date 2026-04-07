<script setup>
import {computed, nextTick, ref} from "vue";
import {AdminProductPage} from "@/legacy/pages";
import AdminNav from "@/components/AdminNav.vue";

const {state, form, editing, message, load, edit, reset, save, remove, next, prev, money} = AdminProductPage.setup();
const PRICE_MIN = 0;
const PRICE_MAX = 2000000;
const PRICE_STEP = 50000;
const modalOpen = ref(false);
const errorMessage = ref("");
const successMessage = ref("");
const modalMessage = ref("");
const modalMessageError = ref(false);
const filters = ref({
    keyword: "",
    categoryId: "",
    minPrice: "",
    maxPrice: ""
});
const selectedPriceRange = ref("all");
const sliderMin = ref(PRICE_MIN);
const sliderMax = ref(PRICE_MAX);
const priceRanges = [
    {id: "all", label: "Tất cả", min: "", max: ""},
    {id: "lt100", label: "Dưới 100.000", min: "", max: 100000},
    {id: "100_300", label: "Từ 100k - 300k", min: 100000, max: 300000},
    {id: "300_500", label: "Từ 300k - 500k", min: 300000, max: 500000},
    {id: "gt500", label: "Trên 500k", min: 500000, max: ""}
];
const listRef = ref(null);

const displayedProducts = computed(() => state.rows || []);
const visiblePages = computed(() => {
    const total = Number(state.totalPages || 0);
    const current = Number(state.page || 0);
    if (total <= 1) {
        return total === 1 ? [0] : [];
    }
    const start = Math.max(0, current - 2);
    const end = Math.min(total - 1, start + 4);
    const adjustedStart = Math.max(0, end - 4);
    return Array.from({length: end - adjustedStart + 1}, (_, index) => adjustedStart + index);
});
const sizeTotalQuantity = computed(() => (state.sizes || []).reduce((total, size) => {
    const qty = Number(form.sizeQtyMap?.[String(size.id)] || 0);
    return total + (Number.isFinite(qty) ? Math.max(0, qty) : 0);
}, 0));

const resolveImage = (name) => name ? `/images/${name}` : "/images/logo.png";
const formatCurrency = (val) => money(val);
const ensureSizeInputs = () => {
    if (!form.sizeQtyMap) {
        form.sizeQtyMap = {};
    }
    (state.sizes || []).forEach((size) => {
        const key = String(size.id);
        if (form.sizeQtyMap[key] === undefined || form.sizeQtyMap[key] === null) {
            form.sizeQtyMap[key] = 0;
        }
    });
};
const openCreateModal = () => {
    reset();
    editing.value = false;
    ensureSizeInputs();
    modalMessage.value = "";
    modalMessageError.value = false;
    modalOpen.value = true;
};
const openEditModal = async (product) => {
    try {
        await edit(product.id);
    } catch (e) {
        form.id = product.id;
        form.name = product.name || "";
        form.price = product.price || "";
        form.discount = product.discount || "";
        form.quantity = product.quantity || "";
        form.image = product.image || "";
        form.imageFile = null;
        form.categoryId = product.category?.id || product.categoryId || "";
        form.description = product.description || "";
        errorMessage.value = e.message || "Không tải được chi tiết sản phẩm, đang mở form với dữ liệu hiện có.";
    }
    ensureSizeInputs();
    modalMessage.value = "";
    modalMessageError.value = false;
    modalOpen.value = true;
};
const closeModal = () => {
    modalOpen.value = false;
    modalMessage.value = "";
    modalMessageError.value = false;
    reset();
};
const submitForm = async () => {
    const wasEditing = editing.value;
    form.quantity = sizeTotalQuantity.value;
    try {
        await save();
        await load();
        successMessage.value = wasEditing ? "Cập nhật sản phẩm thành công" : "Thêm sản phẩm thành công";
        errorMessage.value = "";
        modalMessage.value = successMessage.value;
        modalMessageError.value = false;
        modalOpen.value = false;
    } catch (e) {
        modalMessage.value = e.message || "Cập nhật thất bại";
        modalMessageError.value = true;
        errorMessage.value = modalMessage.value;
    }
};
const onImageChange = (event) => {
    const file = event?.target?.files?.[0] || null;
    form.imageFile = file;
};
const removeProduct = async (product) => {
    const accepted = typeof window !== "undefined"
        ? window.confirm(`Bạn có chắc chắn muốn xoá sản phẩm "${product.name}" không?`)
        : true;
    if (!accepted) {
        return;
    }
    try {
        await remove(product.id);
        successMessage.value = "Xoá sản phẩm thành công";
        errorMessage.value = "";
    } catch (e) {
        successMessage.value = "";
        errorMessage.value = e.message || "Xoá sản phẩm thất bại";
    }
};
const scrollToResults = async () => {
    await nextTick();
    listRef.value?.scrollIntoView({behavior: "smooth", block: "start"});
};
const normalizePrice = (value) => {
    if (value === "" || value === null || value === undefined) {
        return "";
    }
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : "";
};
const applySliderToFilter = () => {
    filters.value.minPrice = Math.min(sliderMin.value, sliderMax.value);
    filters.value.maxPrice = Math.max(sliderMin.value, sliderMax.value);
};
const onSliderMinInput = () => {
    if (sliderMin.value > sliderMax.value) {
        sliderMax.value = sliderMin.value;
    }
    selectedPriceRange.value = "all";
    applySliderToFilter();
};
const onSliderMaxInput = () => {
    if (sliderMax.value < sliderMin.value) {
        sliderMin.value = sliderMax.value;
    }
    selectedPriceRange.value = "all";
    applySliderToFilter();
};
const applyFilters = async () => {
    state.page = 0;
    state.keyword = filters.value.keyword?.trim() || "";
    state.categoryId = filters.value.categoryId || "";
    state.minPrice = normalizePrice(filters.value.minPrice);
    state.maxPrice = normalizePrice(filters.value.maxPrice);
    await load();
    await scrollToResults();
};
const applyPriceRange = async () => {
    const selected = priceRanges.find((range) => range.id === selectedPriceRange.value) || priceRanges[0];
    filters.value.minPrice = selected.min;
    filters.value.maxPrice = selected.max;
    sliderMin.value = selected.min === "" ? PRICE_MIN : Number(selected.min);
    sliderMax.value = selected.max === "" ? PRICE_MAX : Number(selected.max);
    await applyFilters();
};
const clearFilters = async () => {
    filters.value = {keyword: "", categoryId: "", minPrice: "", maxPrice: ""};
    selectedPriceRange.value = "all";
    sliderMin.value = PRICE_MIN;
    sliderMax.value = PRICE_MAX;
    state.page = 0;
    state.keyword = "";
    state.categoryId = "";
    state.minPrice = "";
    state.maxPrice = "";
    await load();
    await scrollToResults();
};
const goToPage = async (page) => {
    const targetPage = Number(page);
    if (!Number.isInteger(targetPage) || targetPage < 0 || targetPage >= Number(state.totalPages || 0) || targetPage === state.page) {
        return;
    }
    state.page = targetPage;
    await load();
    await scrollToResults();
};
</script>

<template>
    <main class="container admin-product-page">
        <h3 class="page-title">Quản lý sản phẩm</h3>
        <div class="admin-product-shell">
            <div class="admin-product-menu">
                <AdminNav/>
            </div>
            <div class="admin-product-main">
                <div class="card admin-product-list" ref="listRef">
                    <div class="admin-product-list-header">
                        <h4>Danh sách</h4>
                        <button class="btn btn-primary btn-add" type="button" @click="openCreateModal">
                            <svg width="16" height="16" viewBox="0 0 16 16" fill="none" xmlns="http://www.w3.org/2000/svg">
                                <path d="M8 1V15M1 8H15" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
                            </svg>
                            Thêm sản phẩm
                        </button>
                    </div>
                    <div class="status-message status-error" v-if="errorMessage">{{ errorMessage }}</div>
                    <div class="status-message status-success" v-if="successMessage || message">{{ successMessage || message }}</div>
                    <div style="overflow-x: auto;">
                        <table style="min-width: 600px;">
                            <thead>
                            <tr>
                                <th>Ảnh</th>
                                <th>Tên</th>
                                <th>Danh mục</th>
                                <th>Giá (VND)</th>
                                <th>Số lượng</th>
                                <th></th>
                            </tr>
                            </thead>
                            <tbody>
                            <tr v-for="product in displayedProducts" :key="product.id">
                                <td class="product-image-cell"><img class="product-thumb" :src="resolveImage(product.image)" alt="Ảnh sản phẩm"></td>
                                <td>{{ product.name }}</td>
                                <td>{{ product.category?.name || product.categoryName || "-" }}</td>
                                <td>{{ formatCurrency(product.price) }}</td>
                                <td>{{ product.quantity ?? 0 }}</td>
                                <td class="table-actions">
                                    <button class="btn btn-action-outline" type="button" @click="openEditModal(product)">Sửa</button>
                                    <button class="btn btn-action-solid" type="button" @click="removeProduct(product)">Xoá</button>
                                </td>
                            </tr>
                            </tbody>
                        </table>
                    </div>
                </div>
                <div class="pagination-wrapper" v-if="state.totalPages > 1">
                    <ul class="pagination">
                        <li class="page-item">
                            <button class="page-link" type="button" @click="prev" :disabled="state.page <= 0">Trước</button>
                        </li>
                        <li class="page-item" v-for="page in visiblePages" :key="'admin-page-'+page" :class="{ active: state.page === page }">
                            <button class="page-link" type="button" @click="goToPage(page)">{{ page + 1 }}</button>
                        </li>
                        <li class="page-item">
                            <button class="page-link" type="button" @click="next" :disabled="state.page + 1 >= state.totalPages">Sau</button>
                        </li>
                    </ul>
                </div>
            </div>
            <div class="admin-product-filter">
                <form class="card admin-product-filter-form" @submit.prevent="applyFilters">
                    <div class="form-group">
                        <label>Từ khoá</label>
                        <input type="text" v-model.trim="filters.keyword" placeholder="Tên sản phẩm">
                    </div>
                    <div class="form-group">
                        <label>Thể loại</label>
                        <select v-model="filters.categoryId">
                            <option value="">Tất cả</option>
                            <option v-for="c in state.categories" :key="c.id" :value="c.id">{{ c.name }}</option>
                        </select>
                    </div>
                    <div class="form-group full-span">
                        <label>Mức giá</label>
                        <div class="price-range-list">
                            <label class="price-range-item" v-for="range in priceRanges" :key="'admin-'+range.id">
                                <input type="radio" name="admin-product-price-range" v-model="selectedPriceRange" :value="range.id" @change="applyPriceRange">
                                <span>{{ range.label }}</span>
                            </label>
                        </div>
                        <p class="price-range-note">Hoặc nhập khoảng giá phù hợp với bạn:</p>
                        <div class="price-custom-slider">
                            <div class="price-slider-values">
                                <strong>{{ money(sliderMin) }}đ</strong>
                                <span>~</span>
                                <strong>{{ money(sliderMax) }}đ</strong>
                            </div>
                            <input type="range" :min="PRICE_MIN" :max="PRICE_MAX" :step="PRICE_STEP" v-model.number="sliderMin" @input="onSliderMinInput">
                            <input type="range" :min="PRICE_MIN" :max="PRICE_MAX" :step="PRICE_STEP" v-model.number="sliderMax" @input="onSliderMaxInput">
                        </div>
                    </div>
                    <div class="table-actions">
                        <button class="btn btn-primary" type="submit">Áp dụng</button>
                        <button class="btn btn-outline-primary" type="button" @click="clearFilters">Xoá lọc</button>
                    </div>
                </form>
            </div>
        </div>
        <div class="modal-backdrop" :class="{ open: modalOpen }" @click.self="closeModal">
            <div class="admin-modal-panel">
                <div class="modal-header">
                    <h4>{{ editing ? "Chỉnh sửa sản phẩm" : "Thêm sản phẩm" }}</h4>
                    <button type="button" class="btn btn-outline-primary" @click="closeModal">Đóng</button>
                </div>
                <form class="admin-product-form" @submit.prevent="submitForm">
                    <div v-if="modalMessage" class="status-message" :class="{ 'status-error': modalMessageError }">{{ modalMessage }}</div>
                    <div class="admin-form-grid">
                        <div class="form-group">
                            <label>Tên</label>
                            <input type="text" v-model.trim="form.name" required>
                        </div>
                        <div class="form-group">
                            <label>Giá</label>
                            <input type="number" step="0.01" min="0" v-model.number="form.price" required>
                        </div>
                        <div class="form-group">
                            <label>Giảm giá (%)</label>
                            <input type="number" step="0.01" min="0" v-model.number="form.discount">
                        </div>
                        <div class="form-group full-span">
                            <label>Ảnh sản phẩm</label>
                            <input type="file" accept="image/*" @change="onImageChange">
                            <div class="form-hint" v-if="form.image">Ảnh hiện tại: {{ form.image }}</div>
                        </div>
                        <div class="form-group full-span">
                            <label>Số lượng theo size</label>
                            <div class="size-quantity-grid">
                                <div class="form-group" v-for="size in state.sizes" :key="'sz-'+size.id">
                                    <label>Size {{ size.name }}</label>
                                    <input type="number" min="0" v-model.number="form.sizeQtyMap[String(size.id)]">
                                </div>
                            </div>
                        </div>
                        <div class="form-group">
                            <label>Tổng số lượng</label>
                            <input type="number" :value="sizeTotalQuantity" readonly>
                        </div>
                        <div class="form-group full-span">
                            <label>Mô tả</label>
                            <input type="text" v-model.trim="form.description">
                        </div>
                        <div class="form-group">
                            <label>Danh mục</label>
                            <select v-model="form.categoryId">
                                <option value="">Không chọn</option>
                                <option v-for="c in state.categories" :key="'m'+c.id" :value="c.id">{{ c.name }}</option>
                            </select>
                        </div>
                        <div class="table-actions admin-form-actions">
                            <button class="btn btn-primary" type="submit">{{ editing ? "Cập nhật" : "Thêm" }}</button>
                            <button class="btn btn-outline-primary" type="button" @click="closeModal">Huỷ</button>
                        </div>
                    </div>
                </form>
            </div>
        </div>
    </main>
</template>
