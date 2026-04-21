<script setup>
import {HomePage} from "@/legacy/pages";
import {ref, onMounted, onUnmounted, nextTick, watch} from "vue";
import {useRoute, useRouter} from "vue-router";
import {formatVnd} from "@/utils/format";

const {filter, data, loading, error, load, productCard} = HomePage.setup();
const money = formatVnd;
const discountPercent = (product) => Math.max(0, Number(product?.discount || 0));
const hasDiscount = (product) => discountPercent(product) > 0;
const finalPrice = (product) => {
    const price = Number(product?.price || 0);
    const percent = discountPercent(product);
    return Math.max(0, price - (price * percent / 100));
};

const route = useRoute();
const router = useRouter();
const normalizeSortValue = (value) => {
    const raw = String(value || "").trim();
    if (!raw) return "";
    if (raw === "priceAsc") return "price_asc";
    if (raw === "priceDesc") return "price_desc";
    return raw;
};

// Restore filter state from URL on mount
onMounted(() => {
    if (route.query.keyword) filter.keyword = route.query.keyword;
    if (route.query.categoryId) filter.categoryId = route.query.categoryId;
    if (route.query.sort) filter.sort = normalizeSortValue(route.query.sort);
    if (route.query.page) filter.page = Number(route.query.page);
    load();
});

// Update URL when filter changes
watch(filter, (newFilter) => {
    const query = {};
    if (newFilter.keyword) query.keyword = newFilter.keyword;
    if (newFilter.categoryId) query.categoryId = newFilter.categoryId;
    if (newFilter.sort) query.sort = normalizeSortValue(newFilter.sort);
    if (newFilter.page > 0) query.page = newFilter.page;
    
    router.replace({ query }).catch(() => {});
}, { deep: true });

const resultsAnchor = ref(null);
const scrollToResults = async () => {
    await nextTick();
    resultsAnchor.value?.scrollIntoView({behavior: "smooth", block: "start"});
};
const applySelectFilters = async () => {
    filter.page = 0;
    await load();
    await scrollToResults();
};
const applySearchFilters = async () => {
    filter.page = 0;
    await load();
    await scrollToResults();
};

// Carousel logic
const currentSlide = ref(0);
const carouselTrack = ref(null);
let autoplayInterval = null;

const goToSlide = (index) => {
    currentSlide.value = index;
    updateCarousel();
};

const nextSlide = () => {
    currentSlide.value = (currentSlide.value + 1) % 3;
    updateCarousel();
};

const prevSlide = () => {
    currentSlide.value = (currentSlide.value - 1 + 3) % 3;
    updateCarousel();
};

const updateCarousel = () => {
    if (carouselTrack.value) {
        const slides = carouselTrack.value.querySelectorAll('.carousel-slide');
        slides.forEach((slide, index) => {
            slide.classList.toggle('active', index === currentSlide.value);
        });
    }
};

const startAutoplay = () => {
    autoplayInterval = setInterval(() => {
        nextSlide();
    }, 5000);
};

const stopAutoplay = () => {
    if (autoplayInterval) {
        clearInterval(autoplayInterval);
    }
};

onMounted(() => {
    startAutoplay();
});

onUnmounted(() => {
    stopAutoplay();
});
</script>

<template>
    <main class="home-page">
        <div class="container">
            <!-- Hero Carousel -->
            <div class="hero-carousel">
                <div class="carousel-track" ref="carouselTrack">
                    <div class="carousel-slide active">
                        <div class="carousel-content">
                            <h1 class="carousel-title">Bộ sưu tập Xuân Hè 2026</h1>
                            <p class="carousel-subtitle">Khám phá phong cách mới, tự tin tỏa sáng</p>
                            <router-link to="/product/list" class="btn btn-primary carousel-btn">Khám phá ngay</router-link>
                        </div>
                        <div class="carousel-images" v-if="data?.newProducts?.length >= 2">
                            <router-link :to="'/product/detail?id=' + data.newProducts[0].id" class="carousel-img-link">
                                <img :src="data.newProducts[0].image ? '/images/' + data.newProducts[0].image : '/images/product1.jpg'" class="slider-img" />
                            </router-link>
                            <router-link :to="'/product/detail?id=' + data.newProducts[1].id" class="carousel-img-link">
                                <img :src="data.newProducts[1].image ? '/images/' + data.newProducts[1].image : '/images/product1.jpg'" class="slider-img" />
                            </router-link>
                        </div>
                        <div class="carousel-bg" style="background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);"></div>
                    </div>
                    <div class="carousel-slide">
                        <div class="carousel-content">
                            <h1 class="carousel-title">Giảm giá đến 50%</h1>
                            <p class="carousel-subtitle">Săn sale ngay hôm nay</p>
                            <router-link to="/product/list" class="btn btn-primary carousel-btn">Mua sắm</router-link>
                        </div>
                        <div class="carousel-images" v-if="data?.discountProducts?.length >= 2">
                            <router-link :to="'/product/detail?id=' + data.discountProducts[0].id" class="carousel-img-link">
                                <img :src="data.discountProducts[0].image ? '/images/' + data.discountProducts[0].image : '/images/product1.jpg'" class="slider-img" />
                            </router-link>
                            <router-link :to="'/product/detail?id=' + data.discountProducts[1].id" class="carousel-img-link">
                                <img :src="data.discountProducts[1].image ? '/images/' + data.discountProducts[1].image : '/images/product1.jpg'" class="slider-img" />
                            </router-link>
                        </div>
                        <div class="carousel-bg" style="background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);"></div>
                    </div>
                    <div class="carousel-slide">
                        <div class="carousel-content">
                            <h1 class="carousel-title">Sản phẩm bán chạy</h1>
                            <p class="carousel-subtitle">Phong cách sang trọng, đẳng cấp</p>
                            <router-link to="/product/list" class="btn btn-primary carousel-btn">Xem thêm</router-link>
                        </div>
                        <div class="carousel-images" v-if="data?.bestSellerProducts?.length >= 2">
                            <router-link :to="'/product/detail?id=' + data.bestSellerProducts[0].id" class="carousel-img-link">
                                <img :src="data.bestSellerProducts[0].image ? '/images/' + data.bestSellerProducts[0].image : '/images/product1.jpg'" class="slider-img" />
                            </router-link>
                            <router-link :to="'/product/detail?id=' + data.bestSellerProducts[1].id" class="carousel-img-link">
                                <img :src="data.bestSellerProducts[1].image ? '/images/' + data.bestSellerProducts[1].image : '/images/product1.jpg'" class="slider-img" />
                            </router-link>
                        </div>
                        <div class="carousel-bg" style="background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%);"></div>
                    </div>
                </div>
                <button class="carousel-nav carousel-prev" @click="prevSlide">‹</button>
                <button class="carousel-nav carousel-next" @click="nextSlide">›</button>
                <div class="carousel-dots">
                    <span class="dot" :class="{active: currentSlide === 0}" @click="goToSlide(0)"></span>
                    <span class="dot" :class="{active: currentSlide === 1}" @click="goToSlide(1)"></span>
                    <span class="dot" :class="{active: currentSlide === 2}" @click="goToSlide(2)"></span>
                </div>
            </div>
            
            <div class="home-filters">
                <div class="filters-card">
                    <div class="filters-grid">
                        <input v-model="filter.keyword" class="form-control" placeholder="Tìm kiếm sản phẩm...">
                        <select v-model="filter.categoryId" class="form-control" @change="applySelectFilters">
                            <option value="">Tất cả danh mục</option>
                            <option v-for="c in (data?.categories || [])" :key="c.id" :value="c.id">{{ c.name }}</option>
                        </select>
                        <select v-model="filter.sort" class="form-control" @change="applySelectFilters">
                            <option value="">Sắp xếp</option>
                            <option value="price_asc">Giá tăng dần</option>
                            <option value="price_desc">Giá giảm dần</option>
                        </select>
                        <button class="btn btn-primary" @click="applySearchFilters">Tìm kiếm</button>
                    </div>
                </div>
            </div>

            <div v-if="loading" class="status-message">Đang tải...</div>
            <div v-if="error" class="status-message status-error">{{ error }}</div>
            
            <template v-if="data">
                <div ref="resultsAnchor"></div>
                <section class="home-section" v-if="(data.filteredProducts || []).length">
                    <h2 class="section-title">Kết quả tìm kiếm</h2>
                    <div class="product-grid" v-auto-grid>
                        <div class="product-card" v-for="p in data.filteredProducts" :key="'f'+p.id">
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
                                    <div v-if="hasDiscount(p)" class="product-card__discount-badge">-{{ discountPercent(p) }}%</div>
                                </div>
                                <div class="product-card__stock" style="font-size: 12px; color: #666; margin-top: 4px;">Kho: {{ p.quantity || 0 }}</div>
                            </div>
                            <div class="product-card__actions">
                                <router-link class="btn btn-primary btn--sm btn--block" :to="'/product/detail?id=' + p.id">Xem chi tiết</router-link>
                            </div>
                        </div>
                    </div>
                </section>

                <section class="home-section">
                    <h2 class="section-title">
                        <span class="title-icon">✨</span>
                        Sản phẩm mới
                        <span class="title-icon">✨</span>
                    </h2>
                    <div class="product-grid" v-auto-grid>
                        <div class="product-card" v-for="p in (data.newProducts||[])" :key="'n'+p.id">
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
                                    <div v-if="hasDiscount(p)" class="product-card__discount-badge">-{{ discountPercent(p) }}%</div>
                                </div>
                            </div>
                            <div class="product-card__actions">
                                <router-link class="btn btn-primary btn--sm btn--block" :to="'/product/detail?id=' + p.id">Xem chi tiết</router-link>
                            </div>
                        </div>
                    </div>
                </section>

                <section class="home-section">
                    <h2 class="section-title">
                        <span class="title-icon">🔥</span>
                        Giảm giá đặc biệt
                        <span class="title-icon">🔥</span>
                    </h2>
                    <div class="product-grid" v-auto-grid>
                        <div class="product-card" v-for="p in (data.discountProducts||[])" :key="'d'+p.id">
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
                                    <div v-if="hasDiscount(p)" class="product-card__discount-badge">-{{ discountPercent(p) }}%</div>
                                </div>
                            </div>
                            <div class="product-card__actions">
                                <router-link class="btn btn-primary btn--sm btn--block" :to="'/product/detail?id=' + p.id">Xem chi tiết</router-link>
                            </div>
                        </div>
                    </div>
                </section>

                <section class="home-section">
                    <h2 class="section-title">
                        <span class="title-icon">⭐</span>
                        Bán chạy nhất
                        <span class="title-icon">⭐</span>
                    </h2>
                    <div class="product-grid" v-auto-grid>
                        <div class="product-card" v-for="p in (data.bestSellerProducts||[])" :key="'b'+p.id">
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
                                    <div v-if="hasDiscount(p)" class="product-card__discount-badge">-{{ discountPercent(p) }}%</div>
                                </div>
                            </div>
                            <div class="product-card__actions">
                                <router-link class="btn btn-primary btn--sm btn--block" :to="'/product/detail?id=' + p.id">Xem chi tiết</router-link>
                            </div>
                        </div>
                    </div>
                </section>
            </template>
        </div>
    </main>
</template>
