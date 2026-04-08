<script setup>
import {computed, onMounted, reactive, ref, watch} from "vue";
import {useRoute} from "vue-router";
import {OrderDetailPage} from "@/legacy/pages";
import {api} from "@/api";
import router from "@/router";

const {orderId, data, error, load, money} = OrderDetailPage.setup();
const route = useRoute();
const reviewForms = reactive({});
const reviewMessage = ref("");
const reviewedSet = computed(() => new Set(data.value?.reviewedProductIds || []));
const isReviewable = computed(() => !!data.value?.reviewable);
const expectedDeliveryDate = computed(() => {
    const base = new Date();
    base.setDate(base.getDate() + 3);
    return base.toLocaleDateString("vi-VN");
});
const formatOrderDateTime = (value) => {
    if (!value) {
        return "";
    }
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
        return value;
    }
    const dd = String(date.getDate()).padStart(2, "0");
    const mm = String(date.getMonth() + 1).padStart(2, "0");
    const yyyy = date.getFullYear();
    const hh = String(date.getHours()).padStart(2, "0");
    const min = String(date.getMinutes()).padStart(2, "0");
    return `${dd}/${mm}/${yyyy} - ${hh}:${min}`;
};
const reviewKey = (detail) => String(detail.product?.id || detail.productId || "");
const ensureReviewForm = (detail) => {
    const key = reviewKey(detail);
    if (!reviewForms[key]) {
        reviewForms[key] = {starRating: 5, reviewContent: "", images: []};
    }
    return reviewForms[key];
};
const setReviewStar = (detail, star) => {
    const form = ensureReviewForm(detail);
    form.starRating = star;
};
const onReviewImagesChange = (detail, event) => {
    const form = ensureReviewForm(detail);
    form.images = Array.from(event.target.files || []);
};
const submitReview = async (detail) => {
    const productId = detail.product?.id || detail.productId;
    if (!productId || !data.value?.order?.id) {
        reviewMessage.value = "Không xác định được sản phẩm hoặc đơn hàng.";
        return;
    }
    const form = ensureReviewForm(detail);
    try {
        const response = await api.reviews.createFromOrder({
            orderId: data.value.order.id,
            productId,
            starRating: Number(form.starRating || 5),
            reviewContent: form.reviewContent || "",
            images: form.images || []
        });
        reviewMessage.value = response.message || "Đã gửi đánh giá.";
        await load();
    } catch (e) {
        reviewMessage.value = e.message;
    }
};
const buyAgain = async (detail) => {
    const productId = detail.product?.id || detail.productId;
    const sizeId = detail.sizeId;
    const quantity = Number(detail.quantity || 1);
    if (!productId || !sizeId) {
        return;
    }
    try {
        await api.cart.addDetail(productId, sizeId, quantity > 0 ? quantity : 1);
    } catch (e) {
    }
    await router.push(`/product/detail?id=${productId}`);
};
const statusLabel = (status) => {
    const map = {
        PLACED_PAID: "Đã đặt - đã TT",
        PLACED_UNPAID: "Đã đặt - chưa TT",
        NEW: "Đã đặt - chưa TT",
        PLACED: "Đã đặt - chưa TT",
        SHIPPING_PAID: "Đang giao - đã TT",
        SHIPPING_UNPAID: "Đang giao - chưa TT",
        SHIPPING: "Đang giao - chưa TT",
        DONE: "Giao thành công",
        DELIVERED_SUCCESS: "Giao thành công",
        CANCEL: "Giao thất bại",
        DELIVERY_FAILED: "Giao thất bại"
    };
    return map[status] || status;
};
const initOrderByQuery = async () => {
    const queryId = Number(route.query.id || route.query.orderId || "");
    if (!Number.isFinite(queryId) || queryId <= 0) {
        return;
    }
    orderId.value = String(queryId);
    await load();
};
onMounted(initOrderByQuery);
watch(() => route.query.id, initOrderByQuery);
watch(() => route.query.orderId, initOrderByQuery);
</script>

<template>
    <main class="container page-shell">
        <h3 class="page-title">Chi tiết đơn hàng</h3>
        <div v-if="error" class="status-message">{{ error }}</div>
        <div class="card order-detail-summary" v-if="data">
            <div class="card-body">
                <div class="order-detail-line">Mã đơn: <strong>{{ data.order?.id }}</strong></div>
                <div class="order-detail-line">Ngày đặt: <span>{{ formatOrderDateTime(data.order?.createDate) }}</span></div>
                <div class="order-detail-line">Trạng thái: <span class="badge order-status-badge">{{ statusLabel(data.order?.status) }}</span></div>
                <div class="order-detail-line">Địa chỉ: <span>{{ data.order?.address }}</span></div>
                <div class="order-detail-line">Số điện thoại giao hàng: <span>{{ data.order?.shippingPhone || "Chưa có" }}</span></div>
                <div class="order-detail-line">Ngày nhận hàng dự kiến: <strong>{{ expectedDeliveryDate }}</strong></div>
            </div>
        </div>
        <div v-if="reviewMessage" class="status-message">{{ reviewMessage }}</div>
        <h4 class="section-title" style="margin-top: 2rem;">Sản phẩm</h4>
        <div class="card" v-if="data">
            <div class="card-body">
                <div style="overflow-x: auto;">
                    <table style="min-width: 600px;">
                        <thead>
                        <tr><th>Sản phẩm</th><th>Giá gốc</th><th>Giá mua</th><th>Số lượng</th><th>Size</th><th>Thành tiền</th><th></th></tr>
                        </thead>
                        <tbody>
                        <template v-for="d in (data.details||[])" :key="d.id">
                            <tr>
                                <td><router-link class="order-detail-product-link" :to="'/product/detail?id=' + (d.product?.id || '')">{{ d.product?.name || d.productName }}</router-link></td>
                                <td style="text-decoration: line-through; color: #999;">{{ money(d.price) }} đ</td>
                                <td><strong style="color: #d4af37;">{{ money(d.price - (d.price * (d.product?.discount || d.discount || 0) / 100)) }} đ</strong></td>
                                <td>{{ d.quantity }}</td>
                                <td>{{ d.sizeName }}</td>
                                <td><strong>{{ money((d.price - (d.price * (d.product?.discount || d.discount || 0) / 100)) * d.quantity) }} đ</strong></td>
                                <td><button class="btn btn-outline" type="button" @click="buyAgain(d)">Mua lại</button></td>
                            </tr>
                    <tr>
                        <td colspan="7">
                            <div v-if="isReviewable && !reviewedSet.has(d.product?.id)" class="review-box mt-3 mb-3">
                                <form @submit.prevent="submitReview(d)">
                                    <h5 class="mb-3">Đánh giá sản phẩm</h5>
                                    <div class="form-group mb-3">
                                        <div class="star-rating-input">
                                            <span 
                                                v-for="star in 5" 
                                                :key="star" 
                                                class="star-icon" 
                                                :class="{ active: star <= ensureReviewForm(d).starRating }"
                                                @click="setReviewStar(d, star)"
                                            >★</span>
                                        </div>
                                    </div>
                                    <div class="form-group mb-3">
                                        <textarea v-model="ensureReviewForm(d).reviewContent" class="form-control" rows="3" placeholder="Chia sẻ trải nghiệm của bạn về sản phẩm này..."></textarea>
                                    </div>
                                    <div class="form-group mb-3">
                                        <label class="form-label text-muted small">Ảnh đính kèm (không bắt buộc)</label>
                                        <input type="file" class="form-control form-control-sm" multiple accept="image/*" @change="onReviewImagesChange(d, $event)">
                                    </div>
                                    <button type="submit" class="btn btn-primary btn-sm">Gửi đánh giá</button>
                                </form>
                            </div>
                            <div v-else-if="reviewedSet.has(d.product?.id)" class="text-success small fst-italic mt-2 mb-2">
                                ✓ Bạn đã đánh giá sản phẩm này.
                            </div>
                            <div v-else class="text-muted small fst-italic mt-2 mb-2">
                                Chỉ được đánh giá khi đơn hàng giao thành công.
                            </div>
                        </td>
                    </tr>
                    </template>
                    </tbody>
                </table>
            </div>
            </div>
        </div>
        <div class="card" v-if="data" style="margin-top: 1rem;">
            <div class="card-body">
                <div style="display: flex; justify-content: space-between; align-items: center; font-size: 18px;">
                    <span>Tổng đơn hàng:</span>
                    <strong style="font-size: 24px; color: #1a1a1a;">{{ money(data.totalAmount) }} đ</strong>
                </div>
            </div>
        </div>
    </main>
</template>
