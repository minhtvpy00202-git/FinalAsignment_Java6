<script setup>
import {ProductListPage} from "@/legacy/pages";
import {nextTick, ref, onMounted, watch} from "vue";
import {useRoute, useRouter} from "vue-router";

const {filter, data, loading, error, load, next, prev, productCard} = ProductListPage.setup();
const money = (value) => Number(value || 0).toLocaleString("vi-VN");
const PRICE_MIN = 0;
const PRICE_MAX = 2000000;
const PRICE_STEP = 50000;
const discountPercent = (product) => Math.max(0, Number(product?.discount || 0));
const hasDiscount = (product) => discountPercent(product) > 0;
const finalPrice = (product) => {
    const price = Number(product?.price || 0);
    const percent = discountPercent(product);
    return Math.max(0, price - (price * percent / 100));
};

const route = useRoute();
const router = useRouter();

const resultsRef = ref(null);
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

// Restore state from URL
onMounted(() => {
    if (route.query.keyword) filter.keyword = route.query.keyword;
    if (route.query.categoryId) filter.categoryId = route.query.categoryId;
    if (route.query.sort) filter.sort = route.query.sort;
    if (route.query.page) filter.page = Number(route.query.page);
    
    if (route.query.minPrice || route.query.maxPrice) {
        filter.minPrice = route.query.minPrice || "";
        filter.maxPrice = route.query.maxPrice || "";
        sliderMin.value = filter.minPrice ? Number(filter.minPrice) : PRICE_MIN;
        sliderMax.value = filter.maxPrice ? Number(filter.maxPrice) : PRICE_MAX;
        
        // Find matching pre-defined range if any
        const match = priceRanges.find(r => String(r.min) === String(filter.minPrice) && String(r.max) === String(filter.maxPrice));
        if (match) {
            selectedPriceRange.value = match.id;
        }
    }
    
    load();
});

// Sync state to URL
watch(filter, (newFilter) => {
    const query = {};
    if (newFilter.keyword) query.keyword = newFilter.keyword;
    if (newFilter.categoryId) query.categoryId = newFilter.categoryId;
    if (newFilter.sort) query.sort = newFilter.sort;
    if (newFilter.page > 0) query.page = newFilter.page;
    if (newFilter.minPrice !== "" && newFilter.minPrice !== null) query.minPrice = newFilter.minPrice;
    if (newFilter.maxPrice !== "" && newFilter.maxPrice !== null) query.maxPrice = newFilter.maxPrice;
    
    router.replace({ query }).catch(() => {});
}, { deep: true });

const onSliderMinInput = () => {
    if (sliderMin.value > sliderMax.value) {
        sliderMax.value = sliderMin.value;
    }
    selectedPriceRange.value = "all";
};
const onSliderMaxInput = () => {
    if (sliderMax.value < sliderMin.value) {
        sliderMin.value = sliderMax.value;
    }
    selectedPriceRange.value = "all";
};
const scrollToResults = async () => {
    await nextTick();
    resultsRef.value?.scrollIntoView({behavior: "smooth", block: "start"});
};
const applySearchFilters = async () => {
    if (selectedPriceRange.value === "all") {
        filter.minPrice = Math.min(sliderMin.value, sliderMax.value);
        filter.maxPrice = Math.max(sliderMin.value, sliderMax.value);
    }
    filter.page = 0;
    await load();
    await scrollToResults();
};
const applySelectFilters = async () => {
    filter.page = 0;
    await load();
    await scrollToResults();
};
const applyPriceRange = async () => {
    const selected = priceRanges.find((range) => range.id === selectedPriceRange.value) || priceRanges[0];
    filter.minPrice = selected.min;
    filter.maxPrice = selected.max;
    sliderMin.value = selected.min === "" ? PRICE_MIN : Number(selected.min);
    sliderMax.value = selected.max === "" ? PRICE_MAX : Number(selected.max);
    await applySelectFilters();
};
const clearFilters = async () => {
    filter.keyword = "";
    filter.categoryId = "";
    filter.minPrice = "";
    filter.maxPrice = "";
    filter.sort = "";
    selectedPriceRange.value = "all";
    sliderMin.value = PRICE_MIN;
    sliderMax.value = PRICE_MAX;
    filter.page = 0;
    await load();
    await scrollToResults();
};
</script>

<template>
    <main class="product-list-page">
        <div class="container">
            <div class="catalog-layout">
                <aside class="filters-sidebar">
                    <form class="filters-vertical card" @submit.prevent="applySearchFilters">
                        <div class="filter-group">
                            <h4 class="filter-title">Tìm kiếm</h4>
                            <div class="form-group">
                                <input type="text" v-model="filter.keyword" class="form-control" placeholder="Tên sản phẩm...">
                            </div>
                        </div>
                        <div class="filter-group">
                            <h4 class="filter-title">Danh mục</h4>
                            <div class="form-group">
                                <select v-model="filter.categoryId" class="form-control">
                                    <option value="">Tất cả danh mục</option>
                                    <option v-for="c in (data.categories || [])" :key="c.id" :value="c.id">{{ c.name }}</option>
                                </select>
                            </div>
                        </div>
                        <div class="filter-group">
                            <h4 class="filter-title">Sắp xếp</h4>
                            <div class="form-group">
                                <select v-model="filter.sort" class="form-control">
                                    <option value="">Mặc định</option>
                                    <option value="price_asc">Giá tăng dần</option>
                                    <option value="price_desc">Giá giảm dần</option>
                                </select>
                            </div>
                        </div>
                        <div class="filter-group">
                            <h4 class="filter-title">Mức giá</h4>
                            <div class="price-range-list">
                                <label class="price-range-item" v-for="range in priceRanges" :key="range.id">
                                    <input type="radio" name="product-price-range" v-model="selectedPriceRange" :value="range.id" @change="applyPriceRange">
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
                        <div class="filter-actions">
                            <button class="btn btn-primary btn--block" type="submit">Áp dụng</button>
                            <button class="btn btn-outline-primary btn--block" type="button" @click="clearFilters">Xóa lọc</button>
                        </div>
                    </form>
                </aside>
                
                <section class="catalog-content" ref="resultsRef">
                    <div class="catalog-header">
                        <h1 class="page-title">Tất cả sản phẩm</h1>
                        <p class="product-count">{{ data.totalElements ?? data.totalProducts ?? (data.products || []).length }} sản phẩm</p>
                    </div>
                    
                    <div v-if="loading" class="status-message">Đang tải dữ liệu...</div>
                    <div v-if="error" class="status-message status-error">{{ error }}</div>
                    
                    <div class="product-grid">
                        <div class="product-card" v-for="p in (data.products || [])" :key="p.id">
                            <div class="product-card__image-wrapper">
                                <router-link :to="'/product/detail?id=' + p.id">
                                    <img class="product-card__image" :src="p.image ? '/images/' + p.image : '/images/product1.jpg'" :alt="p.name">
                                </router-link>
                            </div>
                            <div class="product-card__info">
                                <h3 class="product-card__name">
                                    <router-link :to="'/product/detail?id=' + p.id">{{ p.name }}</router-link>
                                </h3>
                                <div class="product-card__price-row">
                                    <div class="product-card__price">{{ money(finalPrice(p)) }} VNĐ</div>
                                    <div v-if="hasDiscount(p)" class="product-card__price-old">{{ money(p.price) }} VNĐ</div>
                                </div>
                                <div v-if="hasDiscount(p)" class="product-card__discount-badge">-{{ discountPercent(p) }}%</div>
                                <div class="product-card__stock" style="font-size: 12px; color: #666; margin-top: 4px;">Kho: {{ p.quantity || 0 }}</div>
                            </div>
                            <div class="product-card__actions">
                                <router-link class="btn btn-primary btn--sm btn--block" :to="'/product/detail?id=' + p.id">Xem chi tiết</router-link>
                            </div>
                        </div>
                    </div>
                    
                    <nav class="pagination-wrapper" v-if="(data.totalPages || 0) > 1">
                        <ul class="pagination">
                            <li class="page-item">
                                <button class="page-link" type="button" @click="prev">Trước</button>
                            </li>
                            <li class="page-item active">
                                <span class="page-link">[{{ (data.currentPage || 0) + 1 }}] / [{{ data.totalPages || 0 }}]</span>
                            </li>
                            <li class="page-item">
                                <button class="page-link" type="button" @click="next">Sau</button>
                            </li>
                        </ul>
                    </nav>
                </section>
            </div>
        </div>
    </main>
</template>
