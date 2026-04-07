<script setup>
import {computed, nextTick, onMounted, onUnmounted, ref, watch} from "vue";
import {useRoute} from "vue-router";
import {AdminOrderPage} from "@/legacy/pages";
import AdminNav from "@/components/AdminNav.vue";

const {rows, selected, status, payosCode, msg, detail: fetchDetail, updateStatus: persistStatus, cancelPayos, remove} = AdminOrderPage.setup();
const route = useRoute();
const mapRef = ref(null);
const detailModalOpen = ref(false);
const updatingStatus = ref(false);
const actionMessage = ref("");
const actionModalOpen = ref(false);
const confirmModalOpen = ref(false);
const confirmMessage = ref("");
const simulatingDelivery = ref(false);
const hasValidDestination = ref(false);
const mapInfo = ref("");
const deliveryButtonLabel = ref("Giao hàng");
const retryShippingStatus = ref("SHIPPING_UNPAID");
let map = null;
let marker = null;
let destMarker = null;
let routeLine = null;
let simulateTimer = null;
const statusLabel = (value) => {
    const labels = {
        PENDING_PAYMENT: "Đang chờ thanh toán",
        PLACED_UNPAID: "Đã đặt - Chưa TT",
        PLACED_PAID: "Đã đặt - Đã TT",
        SHIPPING_UNPAID: "Đang giao - Chưa TT",
        SHIPPING_PAID: "Đang giao - Đã TT",
        DELIVERED_SUCCESS: "Giao hàng thành công",
        DELIVERY_FAILED: "Giao hàng thất bại"
    };
    return labels[value] || value || "Không rõ";
};
const isDelivered = (value) => value === "DELIVERED_SUCCESS" || value === "DONE";
const statusColor = (value) => isDelivered(value) ? "#64d441" : "#b62c54";
const statusOptions = computed(() => {
    const current = selected.value?.order?.status || "";
    if (current === "PENDING_PAYMENT") {
        return [{value: "PLACED_UNPAID", label: "Chuyển thành COD (chưa TT)"}];
    }
    if (current === "PLACED_UNPAID") {
        return [{value: "SHIPPING_UNPAID", label: "Đang giao - chưa TT"}];
    }
    if (current === "PLACED_PAID") {
        return [{value: "SHIPPING_PAID", label: "Đang giao - đã TT"}];
    }
    if (current === "SHIPPING_UNPAID") {
        return [
            {value: "SHIPPING_UNPAID", label: "Đang giao - chưa TT"}
        ];
    }
    if (current === "SHIPPING_PAID") {
        return [
            {value: "SHIPPING_PAID", label: "Đang giao - đã TT"}
        ];
    }
    if (isDelivered(current)) {
        return [{value: current, label: "Giao thành công"}];
    }
    if (current === "DELIVERY_FAILED" || current === "CANCEL") {
        return [
            {value: current, label: "Giao thất bại"},
            {value: "SHIPPING_UNPAID", label: "Đang giao lại (chưa TT)"},
            {value: "SHIPPING_PAID", label: "Đang giao lại (đã TT)"}
        ];
    }
    return [{value: current || "PLACED_UNPAID", label: statusLabel(current || "PLACED_UNPAID")}];
});
const currentOrderStatus = computed(() => selected.value?.order?.status || "");
const canStartDelivery = computed(() => {
    if (simulatingDelivery.value) {
        return false;
    }
    if (!hasValidDestination.value) {
        return false;
    }
    return currentOrderStatus.value === "PLACED_UNPAID" || currentOrderStatus.value === "PLACED_PAID" || currentOrderStatus.value === "DELIVERY_FAILED";
});
const canCancelOrder = computed(() => currentOrderStatus.value === "DELIVERY_FAILED" && !simulatingDelivery.value);
const deliveryCompleted = computed(() => isDelivered(currentOrderStatus.value));
const showActionModal = (message) => {
    actionMessage.value = message || "";
    actionModalOpen.value = true;
};
const closeActionModal = () => {
    actionModalOpen.value = false;
};
const openConfirmModal = (message) => {
    confirmMessage.value = message || "Bạn có chắc chắn muốn thực hiện thao tác này?";
    confirmModalOpen.value = true;
};
const closeConfirmModal = () => {
    confirmModalOpen.value = false;
    confirmMessage.value = "";
};
const refreshActionUI = () => {
    if (currentOrderStatus.value === "DELIVERY_FAILED") {
        deliveryButtonLabel.value = "Giao lại";
        return;
    }
    deliveryButtonLabel.value = "Giao hàng";
};
const clearSimulation = () => {
    if (simulateTimer) {
        clearInterval(simulateTimer);
        simulateTimer = null;
    }
    simulatingDelivery.value = false;
};
const destroyMap = () => {
    clearSimulation();
    if (map) {
        map.remove();
        map = null;
    }
    marker = null;
    destMarker = null;
    routeLine = null;
};
const forceMapResize = () => {
    if (!map) {
        return;
    }
    map.invalidateSize();
    setTimeout(() => map && map.invalidateSize(), 80);
    setTimeout(() => map && map.invalidateSize(), 220);
    setTimeout(() => map && map.invalidateSize(), 420);
};
const openDetail = async (id) => {
    await fetchDetail(id);
    const firstOption = statusOptions.value[0]?.value;
    if (firstOption && !statusOptions.value.some((item) => item.value === status.value)) {
        status.value = firstOption;
    }
    detailModalOpen.value = true;
    await nextTick();
    await initMap();
    refreshActionUI();
    forceMapResize();
};
const closeDetailModal = () => {
    detailModalOpen.value = false;
    destroyMap();
};
const ensureLeaflet = async () => {
    if (window.L) {
        return window.L;
    }
    await new Promise((resolve, reject) => {
        const css = document.createElement("link");
        css.rel = "stylesheet";
        css.href = "https://unpkg.com/leaflet@1.9.4/dist/leaflet.css";
        document.head.appendChild(css);
        const script = document.createElement("script");
        script.src = "https://unpkg.com/leaflet@1.9.4/dist/leaflet.js";
        script.onload = resolve;
        script.onerror = reject;
        document.body.appendChild(script);
    });
    return window.L;
};
const initMap = async () => {
    if (!selected.value || !mapRef.value) {
        return;
    }
    const L = await ensureLeaflet();
    const store = [13.779876, 109.228232];
    
    const lat = Number(selected.value?.deliveryLat ?? selected.value?.order?.latitude);
    const lng = Number(selected.value?.deliveryLng ?? selected.value?.order?.longitude);
    const validLat = Number.isFinite(lat) && Math.abs(lat) <= 90;
    const validLng = Number.isFinite(lng) && Math.abs(lng) <= 180;
    hasValidDestination.value = validLat && validLng;
    mapInfo.value = hasValidDestination.value ? "" : "Đơn hàng này chưa có toạ độ giao hàng hợp lệ.";
    const dest = hasValidDestination.value ? [lat, lng] : store;
    if (map && map.getContainer && map.getContainer() !== mapRef.value) {
        destroyMap();
    }
    if (!map) {
        map = L.map(mapRef.value).setView([(store[0] + dest[0]) / 2, (store[1] + dest[1]) / 2], 12);
        L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {maxZoom: 19}).addTo(map);
    } else {
        map.invalidateSize();
    }
    if (routeLine) {
        map.removeLayer(routeLine);
    }
    if (marker) {
        map.removeLayer(marker);
    }
    if (destMarker) {
        map.removeLayer(destMarker);
    }
    routeLine = hasValidDestination.value ? L.polyline([store, dest], {color: "#2563eb", weight: 4}).addTo(map) : null;
    marker = L.marker(store).addTo(map).bindPopup("Cửa hàng");
    destMarker = L.marker(dest).addTo(map).bindPopup(hasValidDestination.value ? "Điểm giao" : "Chưa có toạ độ giao hàng");
    if (routeLine) {
        map.fitBounds(routeLine.getBounds(), {padding: [20, 20]});
    } else {
        map.setView(store, 13);
    }
};
const persistCurrentStatus = async () => {
    await persistStatus();
    await fetchDetail(selected.value.order.id);
    refreshActionUI();
};
const startDeliverySimulation = async () => {
    if (!selected.value?.order?.id || simulatingDelivery.value) {
        return;
    }
    if (!hasValidDestination.value) {
        showActionModal("Đơn hàng chưa có toạ độ giao hàng hợp lệ nên không thể mô phỏng giao.");
        return;
    }
    if (!(currentOrderStatus.value === "PLACED_UNPAID" || currentOrderStatus.value === "PLACED_PAID" || currentOrderStatus.value === "DELIVERY_FAILED")) {
        return;
    }
    updatingStatus.value = true;
    simulatingDelivery.value = true;
    try {
        if (currentOrderStatus.value === "PLACED_PAID") {
            status.value = "SHIPPING_PAID";
            retryShippingStatus.value = "SHIPPING_PAID";
        } else if (currentOrderStatus.value === "PLACED_UNPAID") {
            status.value = "SHIPPING_UNPAID";
            retryShippingStatus.value = "SHIPPING_UNPAID";
        } else {
            status.value = retryShippingStatus.value || "SHIPPING_UNPAID";
        }
        await persistCurrentStatus();
        await nextTick();
        await initMap();
        showActionModal("Đơn hàng đã chuyển sang trạng thái đang giao.");
    } finally {
        updatingStatus.value = false;
    }
    const points = routeLine ? routeLine.getLatLngs() : null;
    const totalSteps = 50;
    let step = 0;
    clearSimulation();
    simulatingDelivery.value = true;
    simulateTimer = setInterval(async () => {
        step += 1;
        if (points && points.length >= 2 && marker) {
            const latNow = points[0].lat + (points[1].lat - points[0].lat) * (step / totalSteps);
            const lngNow = points[0].lng + (points[1].lng - points[0].lng) * (step / totalSteps);
            marker.setLatLng([latNow, lngNow]);
        }
        if (step >= totalSteps) {
            clearSimulation();
            updatingStatus.value = true;
            try {
                const success = Math.random() < 0.5;
                status.value = success ? "DELIVERED_SUCCESS" : "DELIVERY_FAILED";
                await persistCurrentStatus();
                await nextTick();
                await initMap();
                if (success) {
                    showActionModal("Đã giao thành công đơn hàng!");
                } else {
                    showActionModal("Giao hàng thất bại. Vui lòng thử giao lại hoặc huỷ đơn.");
                }
            } finally {
                updatingStatus.value = false;
            }
        }
    }, 100);
};
const cancelOrder = async () => {
    if (!selected.value?.order?.id || !canCancelOrder.value) {
        return;
    }
    openConfirmModal("Bạn có chắc chắn muốn huỷ và xoá đơn hàng này không?");
};
const confirmCancelOrder = async () => {
    if (!selected.value?.order?.id || !canCancelOrder.value) {
        closeConfirmModal();
        return;
    }
    closeConfirmModal();
    updatingStatus.value = true;
    try {
        const id = selected.value.order.id;
        await remove(id);
        closeDetailModal();
        showActionModal("Đã huỷ và xoá đơn hàng thành công.");
    } finally {
        updatingStatus.value = false;
    }
};
watch(selected, async () => {
    const firstOption = statusOptions.value[0]?.value;
    if (firstOption && !statusOptions.value.some((item) => item.value === status.value)) {
        status.value = firstOption;
    }
    if (!detailModalOpen.value) {
        return;
    }
    await nextTick();
    await initMap();
    refreshActionUI();
});
onUnmounted(() => {
    destroyMap();
});
onMounted(async () => {
    const queryOrderId = Number(route.query.orderId || "");
    if (Number.isFinite(queryOrderId) && queryOrderId > 0) {
        await openDetail(queryOrderId);
    }
});
</script>

<template>
    <main class="container admin-product-page">
        <h3 class="page-title">Quản lý đơn hàng</h3>
        <div class="admin-product-shell">
            <div class="admin-product-menu">
                <AdminNav/>
                <div class="card">
                    <h4>Huỷ PayOS</h4>
                    <div v-if="msg" class="status-message">{{ msg }}</div>
                    <div class="form-group">
                        <label>Mã đơn hàng</label>
                        <input type="number" min="1" v-model="payosCode">
                    </div>
                    <button class="btn btn-primary" type="button" @click="cancelPayos">Gửi lệnh huỷ</button>
                </div>
            </div>
            <div class="admin-product-main">
                <div class="card">
                    <table>
                        <thead>
                        <tr>
                            <th>Mã đơn</th>
                            <th>Username</th>
                            <th>Trạng thái</th>
                            <th></th>
                        </tr>
                        </thead>
                        <tbody>
                        <tr v-for="o in rows" :key="o.id">
                            <td>{{ o.id }}</td>
                            <td>{{ o.account?.username || "N/A" }}</td>
                            <td><span class="badge" :style="{color: statusColor(o.status)}">{{ statusLabel(o.status) }}</span></td>
                            <td class="table-actions">
                                <button class="btn btn-action-outline" type="button" @click="openDetail(o.id)">Chi tiết</button>
                            </td>
                        </tr>
                        </tbody>
                    </table>
                </div>
            </div>
        </div>
        <div class="modal-backdrop" :class="{open: detailModalOpen}" v-if="detailModalOpen && selected">
            <div class="admin-modal-panel">
                <div class="table-actions" style="justify-content: space-between; margin-bottom: 8px;">
                    <h4 style="margin:0;">Chi tiết đơn hàng</h4>
                    <button class="btn btn-outline-primary" type="button" @click="closeDetailModal">Đóng</button>
                </div>
                <div>Mã đơn: <strong>{{ selected.order.id }}</strong> - Địa chỉ: <span>{{ selected.order.address }}</span></div>
                <div style="margin-top:12px;">
                    <div class="form-group">
                        <label>Trạng thái đơn hàng</label>
                        <div class="status-message" style="margin-bottom: 0;">{{ statusLabel(currentOrderStatus) }}</div>
                    </div>
                    <div class="table-actions" style="margin-top: 8px;">
                        <button class="btn btn-primary" type="button" @click="startDeliverySimulation" :disabled="!canStartDelivery || updatingStatus || simulatingDelivery || deliveryCompleted" :style="deliveryCompleted ? 'opacity:0.45;cursor:not-allowed;' : ''">
                            {{ simulatingDelivery ? "Đang giao hàng..." : deliveryButtonLabel }}
                        </button>
                        <button class="btn btn-action-solid" type="button" @click="cancelOrder" v-if="canCancelOrder" :disabled="updatingStatus">
                            Huỷ đơn
                        </button>
                    </div>
                </div>
                <table style="margin-top:10px;">
                    <thead>
                    <tr>
                        <th>Sản phẩm</th>
                        <th>Giá</th>
                        <th>SL</th>
                        <th>Size</th>
                    </tr>
                    </thead>
                    <tbody>
                    <tr v-for="d in (selected.details || [])" :key="d.id">
                        <td>{{ d.product?.name }}</td>
                        <td>{{ d.price }}</td>
                        <td>{{ d.quantity }}</td>
                        <td>{{ d.sizeName }}</td>
                    </tr>
                    </tbody>
                </table>
                <div class="card" style="margin-top:12px;">
                    <h4>Bản đồ giao hàng</h4>
                    <div class="status-message status-error" v-if="mapInfo">{{ mapInfo }}</div>
                    <div ref="mapRef" style="height:360px;"></div>
                </div>
            </div>
        </div>
        <div class="modal-backdrop" :class="{open: confirmModalOpen}" v-if="confirmModalOpen">
            <div class="admin-modal-panel" style="max-width: 420px;">
                <div class="modal-header">
                    <h4>Xác nhận</h4>
                    <button type="button" class="btn btn-outline-primary" @click="closeConfirmModal">Đóng</button>
                </div>
                <div class="status-message">{{ confirmMessage }}</div>
                <div class="admin-form-actions">
                    <button class="btn btn-action-solid" type="button" @click="confirmCancelOrder">Xác nhận</button>
                    <button class="btn btn-outline-primary" type="button" @click="closeConfirmModal">Huỷ</button>
                </div>
            </div>
        </div>
        <div class="modal-backdrop" :class="{open: actionModalOpen}" v-if="actionModalOpen">
            <div class="admin-modal-panel" style="max-width: 420px;">
                <div class="modal-header">
                    <h4>Thông báo</h4>
                    <button type="button" class="btn btn-outline-primary" @click="closeActionModal">Đóng</button>
                </div>
                <div class="status-message">{{ actionMessage }}</div>
                <div class="admin-form-actions">
                    <button class="btn btn-primary" type="button" @click="closeActionModal">OK</button>
                </div>
            </div>
        </div>
    </main>
</template>
