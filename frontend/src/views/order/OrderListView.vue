<script setup>
import {OrderListPage} from "@/legacy/pages";
import {api} from "@/api";
import {computed, ref} from "vue";
import {useRouter} from "vue-router";

const {orders, error, load, dateTime} = OrderListPage.setup();
const router = useRouter();
const actionMessage = ref("");
const actionMessageType = ref("error");
const repayingOrderId = ref(null);
const refundingOrderId = ref(null);
const activeTab = ref("pending");
const refundRequests = ref([]);
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
        DELIVERY_FAILED: "Giao thất bại",
        REFUND_REQUEST: "Đang yêu cầu hoàn tiền"
    };
    return map[status] || status;
};
const canOnlyDetail = (status) => status === "PLACED_UNPAID" || status === "PLACED_PAID" || status === "REFUND_REQUEST";
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
        return rows.filter((item) => {
            const id = Number(item?.id || 0);
            if (id > 0 && refundedOrderIdSet.value.has(id)) {
                return false;
            }
            return item?.status === "PLACED_UNPAID" || item?.status === "PLACED_PAID";
        });
    }
    return rows.filter((item) => item?.status === "DELIVERED_SUCCESS");
});
const refundRows = computed(() => Array.isArray(refundRequests.value) ? refundRequests.value : []);
const refundedOrderIdSet = computed(() => new Set(refundRows.value.map((item) => Number(item.orderId || 0)).filter((id) => id > 0)));
const refundStatusLabel = (status) => {
    const key = String(status || "").toUpperCase();
    if (key === "PENDING") return "Chờ xử lý";
    if (key === "SUCCESS") return "Đã chấp nhận";
    if (key === "DECLINED" || key === "DECLINE") return "Đã từ chối";
    return key || "Chờ xử lý";
};
const refundStatusStyle = (status) => {
    const key = String(status || "").toUpperCase();
    if (key === "PENDING") {
        return {background: "#fef3c7", color: "#92400e", border: "1px solid #f59e0b"};
    }
    if (key === "SUCCESS") {
        return {background: "#dcfce7", color: "#166534", border: "1px solid #22c55e"};
    }
    if (key === "DECLINED") {
        return {background: "#fee2e2", color: "#991b1b", border: "1px solid #ef4444"};
    }
    return {background: "#f3f4f6", color: "#374151", border: "1px solid #d1d5db"};
};
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
const requestRefund = async (order) => {
    if (!order?.id || refundingOrderId.value) {
        return;
    }
    refundingOrderId.value = order.id;
    actionMessage.value = "";
    try {
        await api.orderWorkflow.requestRefund(order.id);
        await load();
        const list = await api.orderWorkflow.refundRequests();
        refundRequests.value = Array.isArray(list.data) ? list.data : [];
        actionMessage.value = "Đã gửi yêu cầu hoàn tiền.";
        actionMessageType.value = "success";
    } catch (e) {
        actionMessage.value = e.message || "Không thể gửi yêu cầu hoàn tiền.";
        actionMessageType.value = "error";
    } finally {
        refundingOrderId.value = null;
    }
};
const loadRefundRequests = async () => {
    try {
        const res = await api.orderWorkflow.refundRequests();
        refundRequests.value = Array.isArray(res.data) ? res.data : [];
    } catch (e) {
        refundRequests.value = [];
    }
};
loadRefundRequests();
const onTabChange = async (tab) => {
    activeTab.value = tab;
    if (tab === "refund") {
        await loadRefundRequests();
    }
};
</script>

<template>
    <main class="container page-shell">
        <h3 class="page-title">Đơn hàng của tôi</h3>
        <div v-if="error" class="alert alert-danger">{{ error }}</div>
        <div v-if="actionMessage" class="status-message" :class="actionMessageType === 'error' ? 'status-error' : 'status-success'">{{ actionMessage }}</div>
        <div class="order-tabs">
            <button class="order-tab-btn" :class="{active: activeTab === 'pending'}" type="button" @click="onTabChange('pending')">Đơn chờ thanh toán</button>
            <button class="order-tab-btn" :class="{active: activeTab === 'placed'}" type="button" @click="onTabChange('placed')">Đơn đã đặt</button>
            <button class="order-tab-btn" :class="{active: activeTab === 'delivered'}" type="button" @click="onTabChange('delivered')">Đơn đã giao</button>
            <button class="order-tab-btn" :class="{active: activeTab === 'refund'}" type="button" @click="onTabChange('refund')">Yêu cầu hoàn tiền</button>
        </div>
        <div class="card" v-if="activeTab !== 'refund'">
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
                            <button v-if="o.status === 'PLACED_PAID' && !refundedOrderIdSet.has(Number(o.id || 0))" class="btn btn-outline" type="button" :disabled="refundingOrderId === o.id" @click="requestRefund(o)">
                                {{ refundingOrderId === o.id ? "Đang gửi..." : "Yêu cầu hoàn tiền" }}
                            </button>
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
        <div class="card" v-else>
            <table>
                <thead><tr><th>Mã đơn</th><th>Ngày yêu cầu</th><th>Trạng thái</th><th>Lý do từ chối</th></tr></thead>
                <tbody>
                <tr v-for="r in refundRows" :key="r.orderId + '_' + r.createdAt">
                    <td>{{ r.orderId }}</td>
                    <td>{{ dateTime(r.createdAt) }}</td>
                    <td><span class="badge refund-status-badge" :style="refundStatusStyle(r.status)">{{ refundStatusLabel(r.status) }}</span></td>
                    <td>{{ r.declineReason || "-" }}</td>
                </tr>
                <tr v-if="!refundRows.length">
                    <td colspan="4" class="order-empty-row">Chưa có yêu cầu hoàn tiền.</td>
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
.refund-status-badge{
    text-transform: uppercase;
    letter-spacing: .3px;
    font-weight: 700;
    border-radius: 999px;
    padding: 4px 10px;
}
</style>
