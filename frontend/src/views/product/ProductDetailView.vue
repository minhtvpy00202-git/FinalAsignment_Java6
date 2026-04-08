<script setup>
import {computed, onMounted, ref, watch} from "vue";
import {useRoute, useRouter} from "vue-router";
import {ProductDetailPage} from "@/legacy/pages";
import {api} from "@/api";

const {productId, data, error, load, money} = ProductDetailPage.setup();
const route = useRoute();
const router = useRouter();
const loadByQuery = async () => {
    if (typeof window !== "undefined") {
        window.scrollTo({top: 0, left: 0, behavior: "auto"});
    }
    const queryId = Number(route.query.id || route.query.productId || productId.value);
    if (Number.isFinite(queryId) && queryId > 0) {
        productId.value = queryId;
    }
    await load();
};
onMounted(loadByQuery);
watch(() => route.query.id, loadByQuery);
const quantity = ref(1);
const selectedSizeId = ref("");
const selectedSizeName = ref("");
const actionMessage = ref("Vui lòng chọn size và số lượng.");
const sizeWeight = (name) => {
    const map = {
        "XS": 1, "S": 2, "M": 3, "L": 4, "XL": 5, "XXL": 6, "2XL": 6, "3XL": 7, "XXXL": 7
    };
    return map[name?.toUpperCase()] || 99;
};
const sizeOptions = computed(() => {
    const product = data.value?.product;
    let opts = [];
    if (!product) {
        return [];
    }
    if (Array.isArray(product.productSizes)) {
        opts = product.productSizes.filter((x) => (x.quantity || 0) > 0).map((x) => ({
            id: x.size?.id || x.sizeId || "",
            name: x.size?.name || x.sizeName || "",
            max: x.quantity || 0
        }));
    } else if (Array.isArray(product.sizes)) {
        opts = product.sizes.map((s) => ({id: s.id || s.sizeId || "", name: s.name || s.sizeName || "", max: s.quantity || 999}));
    }
    
    return opts.sort((a, b) => {
        const wA = sizeWeight(a.name);
        const wB = sizeWeight(b.name);
        if (wA !== wB) return wA - wB;
        return String(a.name).localeCompare(String(b.name));
    });
});
const chooseSize = (option) => {
    selectedSizeId.value = option.id;
    selectedSizeName.value = option.name;
    actionMessage.value = "";
};
const decreaseQty = () => {
    if (quantity.value > 1) {
        quantity.value -= 1;
    }
};
const increaseQty = () => {
    quantity.value += 1;
};
const addToCart = async () => {
    if (!selectedSizeId.value) {
        actionMessage.value = "Vui lòng chọn size trước khi thêm vào giỏ hàng.";
        return;
    }
    try {
        await api.cart.addDetail(data.value.product.id, selectedSizeId.value, quantity.value);
        actionMessage.value = `Đã thêm vào giỏ hàng (${selectedSizeName.value}, SL ${quantity.value}).`;
    } catch (e) {
        actionMessage.value = e.message;
    }
};
const checkoutNow = async () => {
    if (!selectedSizeId.value) {
        actionMessage.value = "Vui lòng chọn size trước khi thanh toán.";
        return;
    }
    try {
        await api.cart.addDetail(data.value.product.id, selectedSizeId.value, quantity.value);
        router.push("/order/check-out");
    } catch (e) {
        actionMessage.value = e.message;
    }
};
const reviews = computed(() => Array.isArray(data.value?.reviews) ? data.value.reviews : []);
const reviewCount = computed(() => reviews.value.length);
const avgRating = computed(() => {
    const raw = Number(data.value?.avgRatingValue ?? 0);
    return Number.isFinite(raw) ? raw : 0;
});
const roundedAvg = computed(() => Math.round(avgRating.value * 10) / 10);
const starBuckets = computed(() => {
    const result = {1: 0, 2: 0, 3: 0, 4: 0, 5: 0};
    for (const review of reviews.value) {
        const star = Number(review?.starRating || 0);
        if (star >= 1 && star <= 5) {
            result[star] += 1;
        }
    }
    return result;
});
const starRows = computed(() => {
    const total = reviewCount.value || 1;
    return [5, 4, 3, 2, 1].map((star) => {
        const count = starBuckets.value[star] || 0;
        return {
            star,
            count,
            percent: Math.round((count * 10000) / total) / 100
        };
    });
});
const starFillWidth = (percent) => `${Math.max(0, Math.min(100, Number(percent || 0)))}%`;
const resolveImagePath = (name) => {
    if (!name) {
        return "";
    }
    const normalized = String(name).trim();
    if (!normalized) {
        return "";
    }
    if (normalized.startsWith("http://") || normalized.startsWith("https://") || normalized.startsWith("/")) {
        return normalized;
    }
    return `/images/${normalized}`;
};
const avatarText = (review) => {
    const label = review?.account?.fullname || review?.account?.username || "K";
    return String(label).trim().charAt(0).toUpperCase();
};
const reviewImages = (review) => {
    const raw = review?.images || "";
    if (!raw || typeof raw !== "string") {
        return [];
    }
    return raw
        .split(",")
        .map((item) => resolveImagePath(item))
        .filter((item) => !!item);
};
</script>

<template>
    <main class="product-detail-page">
        <div class="container">
            <div v-if="error" class="status-message status-error">{{ error }}</div>
            
            <section class="product-detail-content" v-if="data && data.product">
                <div class="detail-image-wrap">
                    <img class="detail-image" :src="data.product.image ? '/images/' + data.product.image : '/images/product1.jpg'" :alt="data.product.name">
                </div>
                
                <div class="detail-info-wrap">
                    <h1 class="detail-product-name">{{ data.product.name }}</h1>
                    
                    <div class="price-row">
                        <span class="price-amount">{{ money(data.product.price - (data.product.price * (data.product.discount || 0) / 100)) }}</span>
                        <span class="price-currency">VNĐ</span>
                        <span class="price-old" style="text-decoration: line-through; color: #999; margin-left: 12px; font-size: 1.2rem;" v-if="data.product.discount > 0">{{ money(data.product.price) }} VNĐ</span>
                        <span class="badge" style="background: #ef4444; color: white; margin-left: 8px;" v-if="data.product.discount > 0">-{{ data.product.discount }}%</span>
                    </div>
                    
                    <div class="detail-description">
                        <p>{{ data.product.description }}</p>
                    </div>
                    
                    <div class="detail-size-wrap">
                        <div class="size-row">
                            <div class="size-title">Chọn size</div>
                            <div class="size-options-detail">
                                <button type="button" class="size-option" v-for="opt in sizeOptions" :key="opt.id" :class="{active: selectedSizeId === opt.id}" @click="chooseSize(opt)">
                                    {{ opt.name }}
                                </button>
                            </div>
                        </div>
                    </div>
                    
                    <div class="detail-stock-info" v-if="selectedSizeId" style="margin-bottom: 20px; font-size: 14px; color: #666;">
                        <span v-for="opt in sizeOptions" :key="opt.id" v-show="opt.id === selectedSizeId">
                            Còn lại: <strong>{{ opt.max || 0 }}</strong> sản phẩm
                        </span>
                    </div>
                    
                    <div class="qty-control">
                        <span class="qty-label">Số lượng</span>
                        <button type="button" class="qty-btn" @click="decreaseQty">-</button>
                        <input type="number" v-model.number="quantity" min="1">
                        <button type="button" class="qty-btn" @click="increaseQty">+</button>
                    </div>
                    
                    <div class="detail-message-wrap" v-if="actionMessage">
                        <div class="status-message" :class="{'status-error': actionMessage.includes('Vui lòng')}">
                            {{ actionMessage }}
                        </div>
                    </div>
                    
                    <div class="detail-actions">
                        <button class="btn btn-primary" type="button" @click="addToCart">Thêm vào giỏ hàng</button>
                        <button class="btn btn-outline-primary" type="button" @click="checkoutNow">Thanh toán ngay</button>
                    </div>
                </div>
            </section>
            
            <section class="product-reviews" v-if="data">
                <h2 class="section-title">Đánh giá sản phẩm</h2>
                <div class="review-card">
                    <div class="review-google-layout">
                        <div class="review-google-overview">
                            <div class="review-google-score">{{ roundedAvg }}</div>
                            <div class="review-google-stars">
                                <span v-for="s in 5" :key="'avg-star-' + s" :class="s <= Math.round(avgRating) ? 'active' : ''">★</span>
                            </div>
                            <div class="review-google-count">{{ reviewCount }} bài đánh giá</div>
                        </div>
                        <div class="review-google-bars">
                            <div class="review-google-bar-row" v-for="row in starRows" :key="'bar-' + row.star">
                                <div class="review-google-bar-label">{{ row.star }} sao</div>
                                <div class="review-google-bar-track">
                                    <div class="review-google-bar-fill" :style="{width: starFillWidth(row.percent)}"></div>
                                </div>
                                <div class="review-google-bar-value">{{ row.count }}</div>
                            </div>
                        </div>
                    </div>
                    <div v-if="reviews.length" class="review-google-list">
                        <div v-for="review in reviews" :key="review.id" class="review-google-item">
                            <div class="review-google-user">
                                <img v-if="resolveImagePath(review.account?.photo)" class="review-google-avatar" :src="resolveImagePath(review.account?.photo)" alt="avatar">
                                <div v-else class="review-google-avatar review-google-avatar-fallback">{{ avatarText(review) }}</div>
                                <div class="review-google-user-meta">
                                    <strong>{{ review.account?.fullname || review.account?.username || "Khách hàng" }}</strong>
                                    <div class="review-google-item-stars">
                                        <span v-for="s in 5" :key="'item-star-' + review.id + '-' + s" :class="s <= (review.starRating || 0) ? 'active' : ''">★</span>
                                    </div>
                                </div>
                            </div>
                            <p class="review-google-content">{{ review.reviewContent || review.review_content || "Không có nội dung đánh giá." }}</p>
                            <div v-if="reviewImages(review).length" class="review-google-images">
                                <img v-for="(image, index) in reviewImages(review)" :key="'review-image-' + review.id + '-' + index" :src="image" alt="review image">
                            </div>
                        </div>
                    </div>
                    <div v-else class="text-muted mt-3">Chưa có đánh giá nào cho sản phẩm này.</div>
                </div>
            </section>
        </div>
    </main>
</template>
