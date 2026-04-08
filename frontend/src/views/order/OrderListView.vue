<script setup>
import {OrderListPage} from "@/legacy/pages";
import {api} from "@/api";
import {computed, ref} from "vue";
import {useRouter} from "vue-router";

const {orders, error, load, dateTime} = OrderListPage.setup();
const router = useRouter();
const actionMessage = ref("");
const repayingOrderId = ref(null);
const activeTab = ref("pending");
const statusLabel = (status) => {
    const map = {
        PENDING_PAYMENT: "Đang chờ thanh toán",
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
const canOnlyDetail = (status) => status === "PLACED_UNPAID" || status === "PLACED_PAID";
const isPendingPayment = (status) => status === "PENDING_PAYMENT";
const tabOrders = computed(() => {
    const rows = Array.isArray(orders.value) ? orders.value : [];
    if (activeTab.value === "pending") {
        return rows.filter((item) => item?.status === "PENDING_PAYMENT");
    }
    if (activeTab.value === "placed") {
        return rows.filter((item) => item?.status === "PLACED_UNPAID" || item?.status === "PLACED_PAID");
    }
    return rows.filter((item) => item?.status === "DELIVERED_SUCCESS");
});
const retryPayment = async (order) => {
    if (!order?.id || repayingOrderId.value) {
        return;
    }
    repayingOrderId.value = order.id;
    actionMessage.value = "";
    try {
        const detailRes = await api.orderWorkflow.orderDetail(order.id);
        const details = detailRes?.data?.details || [];
        await api.cart.clear();
        for (const item of details) {
            const productId = item?.product?.id || item?.productId;
            const sizeId = item?.sizeId;
            const quantity = Number(item?.quantity || 1);
            if (!productId || !sizeId || !Number.isFinite(quantity) || quantity <= 0) {
                continue;
            }
            await api.cart.addDetail(productId, sizeId, quantity);
        }
        await router.push("/order/check-out");
    } catch (e) {
        actionMessage.value = e.message || "Không thể chuẩn bị thanh toán lại đơn hàng.";
    } finally {
        repayingOrderId.value = null;
    }
};
</script>

<template>
    <main class="container page-shell">
        <h3 class="page-title">Đơn hàng của tôi</h3>
        <div v-if="error" class="alert alert-danger">{{ error }}</div>
        <div v-if="actionMessage" class="status-message status-error">{{ actionMessage }}</div>
        <div class="order-tabs">
            <button class="order-tab-btn" :class="{active: activeTab === 'pending'}" type="button" @click="activeTab = 'pending'">Đơn chờ thanh toán</button>
            <button class="order-tab-btn" :class="{active: activeTab === 'placed'}" type="button" @click="activeTab = 'placed'">Đơn đã đặt</button>
            <button class="order-tab-btn" :class="{active: activeTab === 'delivered'}" type="button" @click="activeTab = 'delivered'">Đơn đã giao</button>
        </div>
        <div class="card">
            <table>
                <thead><tr><th>Mã đơn</th><th>Ngày</th><th>Trạng thái</th><th>Địa chỉ</th><th></th></tr></thead>
                <tbody>
                <tr v-for="o in tabOrders" :key="o.id">
                    <td>{{ o.id }}</td>
                    <td>{{ dateTime(o.createDate) }}</td>
                    <td><span class="badge" style="color:black;">{{ statusLabel(o.status) }}</span></td>
                    <td>{{ o.address }}</td>
                    <td class="table-actions">
                        <button
                            v-if="isPendingPayment(o.status)"
                            class="btn btn-outline"
                            type="button"
                            :disabled="repayingOrderId === o.id"
                            @click="retryPayment(o)"
                        >
                            {{ repayingOrderId === o.id ? "Đang xử lý..." : "Thanh toán" }}
                        </button>
                        <template v-else-if="canOnlyDetail(o.status)">
                            <router-link class="btn btn-outline" :to="'/order/order-detail?id=' + o.id">Xem chi tiết</router-link>
                        </template>
                        <template v-else>
                            <router-link class="btn btn-outline" :to="'/order/order-detail?id=' + o.id">Xem chi tiết</router-link>
                            <router-link class="btn btn-outline" to="/order/my-product-list">Mua lại</router-link>
                        </template>
                    </td>
                </tr>
                <tr v-if="!tabOrders.length">
                    <td colspan="5" class="order-empty-row">Không có đơn hàng trong mục này.</td>
                </tr>
                </tbody>
            </table>
        </div>
        <div class="table-actions" style="margin-top:10px;">
            <button class="btn" type="button" @click="load">Tải danh sách</button>
        </div>
    </main>
</template>

<style scoped>
.order-tabs {
    display: flex;
    gap: 10px;
    margin-bottom: 12px;
}

.order-tab-btn {
    border: 1px solid #d1d5db;
    border-radius: 10px;
    background: #fff;
    color: #111827;
    font-weight: 600;
    padding: 8px 14px;
}

.order-tab-btn.active {
    background: #111827;
    color: #fff;
    border-color: #111827;
}

.order-empty-row {
    text-align: center;
    color: #6b7280;
    padding: 16px;
}
</style>
