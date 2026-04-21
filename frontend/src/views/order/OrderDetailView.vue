<script setup>
import {computed, onMounted, reactive, ref, watch} from "vue";
import {useRoute} from "vue-router";
import {OrderDetailPage} from "@/legacy/pages";
import {api, openSupportChat} from "@/api";
import router from "@/router";
import {isValidVnPhone10, normalizePhone} from "@/utils/phone";
import {formatExpectedDelivery} from "@/utils/order";
import {orderStatusLabel} from "@/utils/orderStatus";

const {orderId, data, error, load, money} = OrderDetailPage.setup();
const route = useRoute();
const reviewForms = reactive({});
const reviewMessage = ref("");
const reviewedSet = computed(() => new Set(data.value?.reviewedProductIds || []));
const isReviewable = computed(() => !!data.value?.reviewable);
const orderStatus = computed(() => String(data.value?.order?.status || ""));
const isUnpaidPlaced = computed(() => orderStatus.value === "PLACED_UNPAID");
const canEditShipping = computed(() => orderStatus.value === "PLACED_UNPAID" || orderStatus.value === "PLACED_PAID");
const shippingModalOpen = ref(false);
const shippingSaving = ref(false);
const shippingError = ref("");
const shippingForm = reactive({address: "", shippingPhone: ""});
const exchangeOpen = ref(false);
const exchangeTarget = ref(null);
const exchangeRows = ref([]);
const exchangeCategories = ref([]);
const exchangePage = ref(0);
const exchangeTotalPages = ref(0);
const exchangeLoading = ref(false);
const exchangeError = ref("");
const exchangeFilters = reactive({keyword: "", categoryId: "", minPrice: "", maxPrice: ""});
const exchangeSelection = reactive({});
const previewImage = ref("");
const expectedDeliveryDate = computed(() => formatExpectedDelivery(data.value?.order));
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
        reviewForms[key] = {starRating: 0, hoverRating: 0, reviewContent: "", images: []};
    }
    return reviewForms[key];
};
const reviewDisplayRating = (detail) => {
    // Hover ưu tiên hơn điểm đã chọn để tạo hiệu ứng preview số sao.
    const form = ensureReviewForm(detail);
    return Number(form.hoverRating || form.starRating || 0);
};
const setReviewStar = (detail, star) => {
    const form = ensureReviewForm(detail);
    form.starRating = star;
};
const setReviewHover = (detail, star) => {
    const form = ensureReviewForm(detail);
    form.hoverRating = star;
};
const clearReviewHover = (detail) => {
    const form = ensureReviewForm(detail);
    form.hoverRating = 0;
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
    if (Number(form.starRating || 0) <= 0) {
        reviewMessage.value = "Vui lòng chọn số sao đánh giá.";
        return;
    }
    try {
        const response = await api.reviews.createFromOrder({
            orderId: data.value.order.id,
            productId,
            starRating: Number(form.starRating || 0),
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
const finalPrice = (row) => {
    const p = Number(row?.finalPrice ?? row?.price ?? 0);
    return Number.isFinite(p) ? p : 0;
};
const lineOriginal = (detail) => Number(detail?.price || 0) * Number(detail?.quantity || 0);
const lineDiscount = (detail) => {
    const percent = Number(detail?.product?.discount || detail?.discount || 0);
    const original = lineOriginal(detail);
    if (percent <= 0) return 0;
    return original * (percent / 100);
};
const lineFinal = (detail) => lineOriginal(detail) - lineDiscount(detail);
const orderTotalBeforeDiscount = computed(() => (data.value?.details || []).reduce((sum, detail) => sum + lineOriginal(detail), 0));
const orderTotalDiscount = computed(() => (data.value?.details || []).reduce((sum, detail) => sum + lineDiscount(detail), 0));
const orderTotalFinal = computed(() => orderTotalBeforeDiscount.value - orderTotalDiscount.value);
const selectedSizeId = (row) => Number(exchangeSelection[row.id]?.sizeId || 0);
const selectedQty = (row) => Number(exchangeSelection[row.id]?.quantity || 1);
const lineTotal = (row) => finalPrice(row) * selectedQty(row);
const ensureSelection = (row) => {
    if (!exchangeSelection[row.id]) {
        const firstSize = Array.isArray(row.sizes) && row.sizes.length ? row.sizes[0].sizeId : "";
        exchangeSelection[row.id] = {sizeId: firstSize, quantity: 1};
    }
    return exchangeSelection[row.id];
};
const showProductImage = (image) => {
    if (!image) return;
    previewImage.value = image.startsWith("/") ? image : `/images/${image}`;
};
const closeImagePreview = () => {
    previewImage.value = "";
};
const loadExchangeCatalog = async (page = 0) => {
    exchangeLoading.value = true;
    exchangeError.value = "";
    try {
        const res = await api.orderWorkflow.exchangeCatalog({
            page,
            size: 8,
            keyword: exchangeFilters.keyword || undefined,
            categoryId: exchangeFilters.categoryId || undefined,
            minPrice: exchangeFilters.minPrice || undefined,
            maxPrice: exchangeFilters.maxPrice || undefined
        });
        const payload = res.data || {};
        exchangeRows.value = Array.isArray(payload.rows) ? payload.rows : [];
        exchangeCategories.value = Array.isArray(payload.categories) ? payload.categories : [];
        exchangePage.value = Number(payload.page || 0);
        exchangeTotalPages.value = Number(payload.totalPages || 0);
        exchangeRows.value.forEach((row) => ensureSelection(row));
    } catch (e) {
        exchangeError.value = e.message || "Không tải được danh sách sản phẩm để đổi.";
    } finally {
        exchangeLoading.value = false;
    }
};
const openExchange = async (detail) => {
    exchangeTarget.value = detail;
    exchangeOpen.value = true;
    await loadExchangeCatalog(0);
};
const closeExchange = () => {
    exchangeOpen.value = false;
    exchangeTarget.value = null;
    exchangeRows.value = [];
    exchangeError.value = "";
};
const applyExchange = async (row) => {
    if (!exchangeTarget.value || !data.value?.order?.id) return;
    const selected = ensureSelection(row);
    const payload = {
        productId: row.id,
        sizeId: Number(selected.sizeId || 0),
        quantity: Number(selected.quantity || 1)
    };
    if (!payload.productId || !payload.sizeId || payload.quantity <= 0) {
        exchangeError.value = "Vui lòng chọn size và số lượng hợp lệ.";
        return;
    }
    try {
        await api.orderWorkflow.exchangeDetail(data.value.order.id, exchangeTarget.value.id, payload);
        await load();
        closeExchange();
    } catch (e) {
        exchangeError.value = e.message || "Không thể đổi sản phẩm.";
    }
};
const removeOrderDetail = async (detail) => {
    if (!data.value?.order?.id) return;
    // Confirm trước khi xoá để tránh thao tác nhầm.
    const accepted = typeof window !== "undefined"
        ? window.confirm("Bạn có chắc chắn muốn xoá sản phẩm khỏi đơn hàng?")
        : true;
    if (!accepted) {
        return;
    }
    try {
        const res = await api.orderWorkflow.removeDetail(data.value.order.id, detail.id);
        if (res.data?.orderDeleted) {
            await router.push("/order/order-list");
            return;
        }
        await load();
    } catch (e) {
        reviewMessage.value = e.message || "Không thể xoá sản phẩm khỏi đơn hàng.";
    }
};
const openShippingModal = () => {
    shippingForm.address = String(data.value?.order?.address || "").trim();
    shippingForm.shippingPhone = String(data.value?.order?.shippingPhone || "").trim();
    shippingError.value = "";
    shippingModalOpen.value = true;
};
const closeShippingModal = () => {
    if (shippingSaving.value) return;
    shippingModalOpen.value = false;
    shippingError.value = "";
};
const submitShippingUpdate = async () => {
    if (!data.value?.order?.id) return;
    const address = String(shippingForm.address || "").trim();
    const phone = normalizePhone(shippingForm.shippingPhone);
    if (!address) {
        shippingError.value = "Vui lòng nhập địa chỉ giao hàng.";
        return;
    }
    if (!isValidVnPhone10(phone)) {
        shippingError.value = "Số điện thoại phải gồm 10 số, bắt đầu bằng 0 và không được là 10 số 0.";
        return;
    }
    shippingSaving.value = true;
    shippingError.value = "";
    try {
        await api.orderWorkflow.updateShipping(data.value.order.id, {address, shippingPhone: phone});
        await load();
        closeShippingModal();
    } catch (e) {
        shippingError.value = e.message || "Không thể cập nhật thông tin giao hàng.";
    } finally {
        shippingSaving.value = false;
    }
};
const contactSeller = (detail) => {
    const productId = Number(detail?.product?.id || detail?.productId || 0);
    if (!productId) {
        return;
    }
    openSupportChat({
        productId,
        productName: detail?.product?.name || detail?.productName || "",
        thumbnailUrl: detail?.product?.image ? `/images/${detail.product.image}` : ""
    });
};
const statusLabel = (status) => orderStatusLabel(status);
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
                <div class="order-detail-line" v-if="canEditShipping">
                    <button class="btn btn-outline-primary" type="button" @click="openShippingModal">Đổi địa chỉ / số ĐT giao hàng</button>
                </div>
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
                                <td style="text-decoration: line-through; color: #999;">{{ money(d.price) }} VNĐ</td>
                                <td><strong style="color: #d4af37;">{{ money(d.price - (d.price * (d.product?.discount || d.discount || 0) / 100)) }} VNĐ</strong></td>
                                <td>{{ d.quantity }}</td>
                                <td>{{ d.sizeName }}</td>
                                <td><strong>{{ money((d.price - (d.price * (d.product?.discount || d.discount || 0) / 100)) * d.quantity) }} VNĐ</strong></td>
                                <td style="display:flex;gap:8px;flex-wrap:wrap">
                                    <button v-if="!isUnpaidPlaced" class="btn btn-outline" type="button" @click="buyAgain(d)">Mua lại</button>
                                    <template v-if="isUnpaidPlaced">
                                        <button class="btn btn-outline" type="button" @click="openExchange(d)">Đổi hàng</button>
                                        <button class="btn btn-outline" type="button" @click="removeOrderDetail(d)">Xoá</button>
                                    </template>
                                    <button class="btn btn-outline" type="button" @click="contactSeller(d)">Liên hệ người bán</button>
                                </td>
                            </tr>
                    <tr>
                        <td colspan="7">
                            <div v-if="isReviewable && !reviewedSet.has(d.product?.id)" class="review-box mt-3 mb-3">
                                <form @submit.prevent="submitReview(d)">
                                    <h5 class="mb-3">Đánh giá sản phẩm</h5>
                                    <div class="form-group mb-3">
                                        <div class="star-rating-input" @mouseleave="clearReviewHover(d)">
                                            <span 
                                                v-for="star in 5" 
                                                :key="star" 
                                                class="star-icon" 
                                                :class="{ active: star <= reviewDisplayRating(d) }"
                                                @mouseenter="setReviewHover(d, star)"
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
                <div class="order-money-row">
                    <span>Tổng giá:</span>
                    <strong>{{ money(orderTotalBeforeDiscount) }} VNĐ</strong>
                </div>
                <div class="order-money-row">
                    <span>Tổng giảm:</span>
                    <strong>{{ money(orderTotalDiscount) }} VNĐ</strong>
                </div>
                <div class="order-money-row">
                    <span>Thành tiền:</span>
                    <strong class="order-money-final">{{ money(orderTotalFinal) }} VNĐ</strong>
                </div>
            </div>
        </div>
        <div class="modal-backdrop" :class="{open: exchangeOpen}" v-if="exchangeOpen">
            <div class="admin-modal-panel exchange-modal-panel">
                <div class="table-actions" style="justify-content: space-between; margin-bottom: 10px;">
                    <h4 style="margin:0;">Đổi sản phẩm cho dòng #{{ exchangeTarget?.id }}</h4>
                    <button class="btn btn-outline-primary" type="button" @click="closeExchange">Đóng</button>
                </div>
                <div class="exchange-filter-bar">
                    <div class="form-group exchange-filter-item exchange-keyword"><label>Từ khoá</label><input v-model="exchangeFilters.keyword" placeholder="Tên sản phẩm"></div>
                    <div class="form-group exchange-filter-item exchange-category">
                        <label>Thể loại</label>
                        <select v-model="exchangeFilters.categoryId">
                            <option value="">Tất cả</option>
                            <option v-for="c in exchangeCategories" :key="c.id" :value="c.id">{{ c.name }}</option>
                        </select>
                    </div>
                    <div class="form-group exchange-filter-item exchange-price"><label>Giá từ</label><input type="number" v-model="exchangeFilters.minPrice"></div>
                    <div class="form-group exchange-filter-item exchange-price"><label>Giá đến</label><input type="number" v-model="exchangeFilters.maxPrice"></div>
                    <div class="exchange-filter-item exchange-filter-action">
                        <button class="btn btn-outline-primary exchange-filter-btn" type="button" @click="loadExchangeCatalog(0)">Lọc</button>
                    </div>
                </div>
                <div v-if="exchangeError" class="status-message status-error">{{ exchangeError }}</div>
                <div style="overflow-x:auto;">
                    <table style="min-width:980px;">
                        <thead>
                        <tr><th>Tên sản phẩm</th><th>Ảnh</th><th>Giá</th><th>Giá sau giảm</th><th>Size</th><th>Số lượng</th><th>Thành tiền</th><th></th></tr>
                        </thead>
                        <tbody>
                        <tr v-for="row in exchangeRows" :key="row.id">
                            <td>{{ row.name }}</td>
                            <td><button class="btn btn-outline-primary" type="button" @click="showProductImage(row.image)">Xem ảnh</button></td>
                            <td>{{ money(row.price) }} VNĐ</td>
                            <td><strong style="color:#dc2626;">{{ money(finalPrice(row)) }} VNĐ</strong></td>
                            <td>
                                <select v-model="ensureSelection(row).sizeId" style="min-width:90px;">
                                    <option v-for="s in row.sizes || []" :key="s.sizeId" :value="s.sizeId">{{ s.sizeName }} ({{ s.stock }})</option>
                                </select>
                            </td>
                            <td><input type="number" min="1" v-model.number="ensureSelection(row).quantity" style="width:80px;"></td>
                            <td>{{ money(lineTotal(row)) }} VNĐ</td>
                            <td><button class="btn btn-primary" type="button" @click="applyExchange(row)">Chọn</button></td>
                        </tr>
                        <tr v-if="!exchangeRows.length && !exchangeLoading"><td colspan="8">Không có sản phẩm phù hợp.</td></tr>
                        </tbody>
                    </table>
                </div>
                <div class="table-actions" style="justify-content:flex-end;gap:8px;margin-top:10px;">
                    <button class="btn btn-outline-primary" type="button" :disabled="exchangePage<=0" @click="loadExchangeCatalog(exchangePage-1)">Trang trước</button>
                    <button class="btn btn-outline-primary" type="button" :disabled="exchangePage+1>=exchangeTotalPages" @click="loadExchangeCatalog(exchangePage+1)">Trang sau</button>
                </div>
            </div>
        </div>
        <div class="modal-backdrop" :class="{open: !!previewImage}" v-if="previewImage" @click.self="closeImagePreview">
            <div class="admin-modal-panel" style="max-width:360px;text-align:center;">
                <img :src="previewImage" alt="preview" style="width:300px;height:300px;object-fit:cover;border-radius:10px;">
            </div>
        </div>
        <div class="modal-backdrop" :class="{open: shippingModalOpen}" v-if="shippingModalOpen">
            <div class="admin-modal-panel" style="max-width:560px;">
                <div class="table-actions" style="justify-content: space-between; margin-bottom: 10px;">
                    <h4 style="margin:0;">Cập nhật thông tin giao hàng</h4>
                    <button class="btn btn-outline-primary" type="button" @click="closeShippingModal">Đóng</button>
                </div>
                <div class="form-group">
                    <label>Địa chỉ giao hàng</label>
                    <input v-model="shippingForm.address" type="text" placeholder="Nhập địa chỉ giao hàng">
                </div>
                <div class="form-group">
                    <label>Số điện thoại giao hàng</label>
                    <input v-model="shippingForm.shippingPhone" type="text" placeholder="0xxxxxxxxx">
                </div>
                <div v-if="shippingError" class="status-message status-error">{{ shippingError }}</div>
                <div class="table-actions" style="justify-content:flex-end;gap:8px;">
                    <button class="btn btn-primary" type="button" :disabled="shippingSaving" @click="submitShippingUpdate">
                        {{ shippingSaving ? "Đang lưu..." : "Lưu thay đổi" }}
                    </button>
                </div>
            </div>
        </div>
    </main>
</template>

<style scoped>
.exchange-modal-panel{
    width:min(96vw, 1400px);
    max-width:1400px !important;
}
.exchange-filter-bar{
    display:grid;
    grid-template-columns: 2.2fr 1.4fr 1.1fr 1.1fr auto;
    gap:12px;
    align-items:stretch;
    margin-bottom:12px;
}
.exchange-filter-item{
    min-width:0;
}
.exchange-filter-item.form-group{
    display:flex;
    flex-direction:column;
    align-items:stretch;
    gap:6px;
    margin:0;
}
.exchange-filter-item.form-group label{
    margin:0;
    line-height:1.2;
}
.exchange-filter-action{
    display:flex;
    align-items:flex-end;
    justify-content:flex-start;
}
.exchange-filter-btn{
    height:42px;
    min-width:92px;
    white-space:nowrap;
}
.order-money-row{
    display:flex;
    align-items:center;
    justify-content:space-between;
    gap:12px;
    font-size:18px;
    line-height:1.8;
}
.order-money-row strong{
    text-align:right;
    margin-left:auto;
}
.order-money-final{
    font-size:24px;
    color:#1a1a1a;
}
@media (max-width:1200px){
    .exchange-modal-panel{
        width:min(98vw, 1200px);
    }
    .exchange-filter-bar{
        grid-template-columns: repeat(2, minmax(240px, 1fr));
    }
    .exchange-filter-action{
        grid-column: span 2;
    }
}
</style>
