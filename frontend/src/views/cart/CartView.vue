<script setup>
import {CartPage} from "@/legacy/pages";
import {computed, ref} from "vue";
import {openSupportChat} from "@/api";

const {state, error, loading, updateItem, removeItem, clear, money} = CartPage.setup();

const updating = ref({});
const quantityMessage = ref("");
const lineDiscount = (item) => {
    const price = Number(item?.price || 0);
    const quantity = Number(item?.quantity || 0);
    const discountPercent = Number(item?.discount || 0);
    if (!price || !quantity || !discountPercent) {
        return 0;
    }
    return (price * quantity * discountPercent) / 100;
};
const totalDiscount = computed(() => {
    return (state.items || []).reduce((sum, item) => sum + lineDiscount(item), 0);
});
const totalBeforeDiscount = computed(() => Number(state.totalPrice || 0) + Number(totalDiscount.value || 0));
const payableTotal = computed(() => Number(totalBeforeDiscount.value || 0) - Number(totalDiscount.value || 0));
const itemKey = (item) => item.productId + '-' + item.sizeId;
const itemMaxStock = (item) => {
    const stock = Number(item?.stock || 0);
    return Number.isFinite(stock) && stock > 0 ? Math.floor(stock) : 1;
};
const itemMinQty = () => 1;
const canDecrease = (item) => Number(item?.quantity || 1) > itemMinQty();
const canIncrease = (item) => Number(item?.quantity || 0) < itemMaxStock(item);
const minusTooltip = (item) => {
    if (!canDecrease(item)) {
        return "Số lượng tối thiểu là 1";
    }
    return "";
};
const plusTooltip = (item) => {
    if (!canIncrease(item)) {
        return "Đã đạt tồn kho tối đa";
    }
    return "";
};
const normalizeQty = (item, value) => {
    const max = itemMaxStock(item);
    const parsed = Number.parseInt(String(value || "").replace(/\D+/g, ""), 10);
    if (!Number.isFinite(parsed) || parsed < 1) {
        return 1;
    }
    return Math.min(parsed, max);
};
const onQtyKeydown = (event) => {
    const allow = ["Backspace", "Delete", "Tab", "ArrowLeft", "ArrowRight", "Home", "End", "Enter"];
    if (allow.includes(event.key)) {
        return;
    }
    if (!/^\d$/.test(event.key)) {
        event.preventDefault();
    }
};
const onQtyInput = (item, event) => {
    const raw = String(event?.target?.value || "");
    const digits = raw.replace(/\D+/g, "");
    event.target.value = digits;
    if (!digits) {
        item.quantity = 1;
        return;
    }
    const next = normalizeQty(item, digits);
    item.quantity = next;
    const typed = Number.parseInt(digits, 10);
    if (Number.isFinite(typed) && typed > itemMaxStock(item)) {
        quantityMessage.value = `Vì mặt hàng "${item.name}" chỉ còn tồn kho ${itemMaxStock(item)} cái, nên Số lượng tối đa được nhập là ${itemMaxStock(item)}.`;
    } else {
        quantityMessage.value = "";
    }
};
const applyQuantity = async (item) => {
    const key = itemKey(item);
    if (updating.value[key]) return;
    const next = normalizeQty(item, item.quantity);
    item.quantity = next;
    updating.value[key] = true;
    quantityMessage.value = "";
    try {
        await updateItem(item);
    } catch (e) {
        quantityMessage.value = e.message || "Không thể cập nhật số lượng.";
    } finally {
        updating.value[key] = false;
    }
};

const minus = async (item) => {
    const key = itemKey(item);
    if (updating.value[key]) return;
    
    if (canDecrease(item)) {
        item.quantity = normalizeQty(item, Number(item.quantity || 1) - 1);
        await applyQuantity(item);
    }
};

const plus = async (item) => {
    const key = itemKey(item);
    if (updating.value[key]) return;
    const next = Number(item.quantity || 0) + 1;
    const max = itemMaxStock(item);
    if (next > max) {
        quantityMessage.value = `Số lượng tối đa cho "${item.name}" là ${max}.`;
        item.quantity = max;
        return;
    }
    item.quantity = normalizeQty(item, next);
    await applyQuantity(item);
};

const openSellerChat = (item) => {
    const productId = Number(item?.productId || 0);
    if (!productId) {
        return;
    }
    openSupportChat({
        productId,
        productName: item?.name || "",
        thumbnailUrl: item?.image ? `/images/${item.image}` : ""
    });
};
const confirmRemoveItem = async (item) => {
    const accepted = typeof window !== "undefined"
        ? window.confirm(`Bạn có chắc chắn muốn xoá "${item?.name || "sản phẩm"}" khỏi giỏ hàng?`)
        : true;
    if (!accepted) {
        return;
    }
    await removeItem(item);
};
const confirmClearCart = async () => {
    const accepted = typeof window !== "undefined"
        ? window.confirm("Bạn có chắc chắn muốn xoá toàn bộ sản phẩm trong giỏ hàng?")
        : true;
    if (!accepted) {
        return;
    }
    await clear();
};
</script>

<template>
    <main class="cart-page">
        <div class="container">
            <h1 class="page-title">Giỏ hàng của bạn</h1>
            
            <div v-if="loading" class="status-message">Đang tải...</div>
            <div v-if="error" class="status-message status-error">{{ error }}</div>
            <div v-if="quantityMessage" class="status-message status-error">{{ quantityMessage }}</div>
            
            <div v-if="!state.items.length && !loading" class="empty-cart">
                <div class="empty-cart-icon">🛒</div>
                <h3>Giỏ hàng trống</h3>
                <p>Hãy thêm sản phẩm vào giỏ hàng để tiếp tục mua sắm</p>
                <router-link class="btn btn-primary" to="/product/list">Khám phá sản phẩm</router-link>
            </div>
            
            <div class="cart-content" v-if="state.items.length">
                <div class="cart-table-wrap">
                    <table class="cart-table">
                        <thead>
                            <tr>
                                <th>Sản phẩm</th>
                                <th>Giá</th>
                                <th>Giảm giá</th>
                                <th>Số lượng</th>
                                <th>Size</th>
                                <th></th>
                            </tr>
                        </thead>
                        <tbody>
                            <tr v-for="item in state.items" :key="item.productId + '-' + item.sizeId">
                                <td>
                                    <div class="cart-item-product">
                                        <img :src="item.image ? '/images/' + item.image : '/images/product1.jpg'" :alt="item.name" width="80" height="80" class="cart-item-image">
                                        <strong>{{ item.name }}</strong>
                                    </div>
                                </td>
                                <td>{{ money(item.price) }} VNĐ</td>
                                <td>{{ money(lineDiscount(item)) }} VNĐ</td>
                                <td>
                                    <div class="cart-item-qty-form">
                                        <button
                                            class="btn btn-outline-secondary btn-sm"
                                            type="button"
                                            @click="minus(item)"
                                            :title="minusTooltip(item)"
                                            :disabled="updating[item.productId + '-' + item.sizeId] || !canDecrease(item)"
                                        >-</button>
                                        <input
                                            type="text"
                                            inputmode="numeric"
                                            pattern="[0-9]*"
                                            :value="item.quantity"
                                            class="cart-item-qty-input"
                                            @keydown="onQtyKeydown"
                                            @input="onQtyInput(item, $event)"
                                            @blur="applyQuantity(item)"
                                            @keyup.enter="applyQuantity(item)"
                                        >
                                        <button
                                            class="btn btn-outline-secondary btn-sm"
                                            type="button"
                                            @click="plus(item)"
                                            :title="plusTooltip(item)"
                                            :disabled="updating[item.productId + '-' + item.sizeId] || !canIncrease(item)"
                                        >+</button>
                                    </div>
                                </td>
                                <td>{{ item.sizeName }}</td>
                                <td>
                                    <div class="cart-row-actions">
                                        <button class="btn btn-outline-secondary btn-sm cart-row-btn" type="button" @click="openSellerChat(item)">Liên hệ người bán</button>
                                        <button class="btn btn-action-solid btn-sm cart-row-btn" type="button" @click="confirmRemoveItem(item)">Xóa</button>
                                    </div>
                                </td>
                            </tr>
                        </tbody>
                    </table>
                </div>
                
                <div class="cart-summary">
                    <div class="cart-summary-content">
                        <div class="cart-summary-row">
                            <span>Tổng:</span>
                            <strong>{{ money(totalBeforeDiscount) }} VNĐ</strong>
                        </div>
                        <div class="cart-summary-row">
                            <span>Giảm giá:</span>
                            <strong>{{ money(totalDiscount) }} VNĐ</strong>
                        </div>
                        <div class="cart-summary-row">
                            <span>Số tiền cần thanh toán:</span>
                            <strong>{{ money(payableTotal) }} VNĐ</strong>
                        </div>
                    </div>
                    <div class="cart-summary-actions">
                        <button class="btn btn-outline-secondary" type="button" @click="confirmClearCart">Xóa tất cả</button>
                        <router-link class="btn checkout-btn" to="/order/check-out">Thanh toán</router-link>
                    </div>
                </div>
            </div>
        </div>
    </main>
</template>

<style scoped>
.cart-row-actions{
    display:flex;
    align-items:center;
    gap:8px;
    flex-wrap:nowrap;
}
.cart-row-btn{
    height:34px;
    padding:0 12px !important;
    line-height:1 !important;
    white-space:nowrap;
}
</style>
