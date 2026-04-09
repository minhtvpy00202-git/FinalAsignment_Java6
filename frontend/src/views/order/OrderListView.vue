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
const formatExpectedDelivery = (order) => {
    const date = String(order?.expectedDeliveryDate || "").trim();
    const distanceM = Number(order?.deliveryDistanceM || 0);
    if (!date) {
        return "Chưa có";
    }
    const [year, month, day] = date.split("-");
    const dateLabel = year && month && day ? `${day}/${month}/${year}` : date;
    const km = distanceM > 0 ? `${(distanceM / 1000).toFixed(distanceM >= 10000 ? 0 : 1)} km` : "";
    return km ? `${dateLabel} • ${km}` : dateLabel;
};
const formatDeliveredTime = (value) => {
    const raw = String(value || "").trim();
    if (!raw) {
        return "Chưa có";
    }
    const dt = new Date(raw.replace(" ", "T"));
    if (Number.isNaN(dt.getTime())) {
        return raw;
    }
    const day = String(dt.getDate()).padStart(2, "0");
    const month = String(dt.getMonth() + 1).padStart(2, "0");
    const year = dt.getFullYear();
    const hh = String(dt.getHours()).padStart(2, "0");
    const mm = String(dt.getMinutes()).padStart(2, "0");
    const ss = String(dt.getSeconds()).padStart(2, "0");
    return `${hh}:${mm}:${ss} ${day}/${month}/${year}`;
};
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
        await api.orderWorkflow.retryPayment(order.id);
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
                <thead><tr><th>Mã đơn</th><th>Ngày đặt  </th><th>Trạng thái</th><th v-if="activeTab === 'placed'">Dự kiến nhận hàng</th><th v-if="activeTab === 'delivered'">Thời gian giao</th><th>Địa chỉ giao hàng</th><th></th></tr></thead>
                <tbody>
                <tr v-for="o in tabOrders" :key="o.id">
                    <td>{{ o.id }}</td>
                    <td>{{ dateTime(o.createDate) }}</td>
                    <td><span class="badge" style="color:black;">{{ statusLabel(o.status) }}</span></td>
                    <td v-if="activeTab === 'placed'">{{ formatExpectedDelivery(o) }}</td>
                    <td v-if="activeTab === 'delivered'">{{ formatDeliveredTime(o.deliveredAt) }}</td>
                    <td>
                        <div class="order-address-scroll">{{ o.address }}</div>
                    </td>
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
                    <td :colspan="activeTab === 'placed' || activeTab === 'delivered' ? 6 : 5" class="order-empty-row">Không có đơn hàng trong mục này.</td>
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

.order-address-scroll {
    max-width: 460px;
    overflow-x: auto;
    white-space: nowrap;
    padding-bottom: 4px;
}
</style>
