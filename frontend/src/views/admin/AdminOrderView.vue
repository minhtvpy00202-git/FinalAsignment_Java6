<script setup>
import {computed, nextTick, onMounted, onUnmounted, ref, watch} from "vue";
import {useRoute} from "vue-router";
import {AdminOrderPage} from "@/legacy/pages";
import AdminNav from "@/components/AdminNav.vue";

const {rows, selected, status, payosCode, msg, paging, load, toPrevPage, toNextPage, detail: fetchDetail, updateStatus: persistStatus, cancelPayos, remove} = AdminOrderPage.setup();
const route = useRoute();
const mapRef = ref(null);
const detailModalOpen = ref(false);
const updatingStatus = ref(false);
const actionMessage = ref("");
const actionModalOpen = ref(false);
const confirmModalOpen = ref(false);
const confirmMessage = ref("");
const simulatingDelivery = ref(false);
const activeTab = ref("pending");
const hasValidDestination = ref(false);
const mapInfo = ref("");
const deliveryButtonLabel = ref("Giao hàng");
const retryShippingStatus = ref("SHIPPING_UNPAID");
let map = null;
let marker = null;
let destMarker = null;
let routeLine = null;
let simulateTimer = null;
const routingWarning = ref("");
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
const tabRows = computed(() => Array.isArray(rows.value) ? rows.value : []);
const currentPaging = computed(() => paging?.[activeTab.value] || {page: 0, totalPages: 0, totalElements: 0});
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
    try {
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
    } catch (e) {
        showActionModal("Không tải được chi tiết đơn hàng. Vui lòng thử lại.");
    }
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
const createTruckIcon = (L) => L.divIcon({
    className: "delivery-truck-icon",
    html: "<div style=\"font-size:28px;line-height:1;filter:drop-shadow(0 2px 4px rgba(0,0,0,.35));\">🚚</div>",
    iconSize: [28, 28],
    iconAnchor: [14, 14]
});
const fetchRoadRoute = async (start, dest) => {
    const url = `https://router.project-osrm.org/route/v1/driving/${start[1]},${start[0]};${dest[1]},${dest[0]}?overview=full&geometries=geojson`;
    const res = await fetch(url, {method: "GET"});
    if (!res.ok) {
        throw new Error("routing-failed");
    }
    const payload = await res.json();
    const coordinates = payload?.routes?.[0]?.geometry?.coordinates;
    if (!Array.isArray(coordinates) || coordinates.length < 2) {
        throw new Error("routing-empty");
    }
    return coordinates
        .map((point) => [Number(point[1]), Number(point[0])])
        .filter((point) => Number.isFinite(point[0]) && Number.isFinite(point[1]));
};
const getRoutePoints = () => {
    const points = routeLine?.getLatLngs?.();
    if (!Array.isArray(points)) {
        return [];
    }
    if (points.length && Array.isArray(points[0])) {
        return points[0];
    }
    return points;
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
    routingWarning.value = "";
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
    if (hasValidDestination.value) {
        let routePoints = [store, dest];
        try {
            const roadPoints = await fetchRoadRoute(store, dest);
            if (roadPoints.length >= 2) {
                routePoints = roadPoints;
            }
        } catch (e) {
            routingWarning.value = "Không tải được lộ trình thực, tạm hiển thị đường thẳng.";
        }
        routeLine = L.polyline(routePoints, {color: "#2563eb", weight: 4}).addTo(map);
    } else {
        routeLine = null;
    }
    marker = L.marker(store, {icon: createTruckIcon(L)}).addTo(map).bindPopup("Xe giao hàng");
    destMarker = L.marker(dest).addTo(map).bindPopup(hasValidDestination.value ? "Điểm giao" : "Chưa có toạ độ giao hàng");
    if (routeLine) {
        map.fitBounds(routeLine.getBounds(), {padding: [20, 20]});
    } else {
        map.setView(store, 13);
    }
};
const persistCurrentStatus = async () => {
    await persistStatus(activeTab.value);
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
    const points = getRoutePoints();
    const totalSteps = 50;
    let step = 0;
    clearSimulation();
    simulatingDelivery.value = true;
    simulateTimer = setInterval(async () => {
        step += 1;
        if (points.length >= 2 && marker) {
            const progress = step / totalSteps;
            const segmentProgress = progress * (points.length - 1);
            const segmentIndex = Math.min(points.length - 2, Math.floor(segmentProgress));
            const local = segmentProgress - segmentIndex;
            const from = points[segmentIndex];
            const to = points[segmentIndex + 1];
            const latNow = from.lat + (to.lat - from.lat) * local;
            const lngNow = from.lng + (to.lng - from.lng) * local;
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
        await remove(id, activeTab.value);
        closeDetailModal();
        showActionModal("Đã huỷ và xoá đơn hàng thành công.");
    } finally {
        updatingStatus.value = false;
    }
};
watch(activeTab, async (tab) => {
    await load(tab);
});
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
    await load(activeTab.value);
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
                    <div class="order-tabs">
                        <button class="order-tab-btn" :class="{active: activeTab === 'pending'}" type="button" @click="activeTab = 'pending'">Đơn chờ thanh toán</button>
                        <button class="order-tab-btn" :class="{active: activeTab === 'placed'}" type="button" @click="activeTab = 'placed'">Đơn đã đặt</button>
                        <button class="order-tab-btn" :class="{active: activeTab === 'delivered'}" type="button" @click="activeTab = 'delivered'">Đơn đã giao</button>
                    </div>
                    <div class="table-actions" style="justify-content: space-between; margin-bottom: 10px;">
                        <span class="status-message" style="margin:0;">Trang {{ (currentPaging.page || 0) + 1 }} / {{ currentPaging.totalPages || 0 }} — Tổng {{ currentPaging.totalElements || 0 }} đơn</span>
                        <div style="display:flex;gap:8px;">
                            <button class="btn btn-outline-primary" type="button" @click="toPrevPage(activeTab)" :disabled="(currentPaging.page || 0) <= 0">Trang trước</button>
                            <button class="btn btn-outline-primary" type="button" @click="toNextPage(activeTab)" :disabled="(currentPaging.page || 0) + 1 >= (currentPaging.totalPages || 0)">Trang sau</button>
                        </div>
                    </div>
                    <table>
                        <thead>
                        <tr>
                            <th>Mã đơn</th>
                            <th>Username</th>
                            <th>Trạng thái</th>
                            <th v-if="activeTab === 'placed'">Dự kiến nhận hàng</th>
                            <th v-if="activeTab === 'delivered'">Thời gian giao</th>
                            <th>Địa chỉ giao hàng</th>
                            <th v-if="activeTab !== 'pending'"></th>
                        </tr>
                        </thead>
                        <tbody>
                        <tr v-for="o in tabRows" :key="o.id">
                            <td>{{ o.id }}</td>
                            <td>{{ o.account?.username || "N/A" }}</td>
                            <td><span class="badge" :style="{color: statusColor(o.status)}">{{ statusLabel(o.status) }}</span></td>
                            <td v-if="activeTab === 'placed'">{{ formatExpectedDelivery(o) }}</td>
                            <td v-if="activeTab === 'delivered'">{{ formatDeliveredTime(o.deliveredAt) }}</td>
                            <td>
                                <div class="order-address-scroll">{{ o.address || "" }}</div>
                            </td>
                            <td class="table-actions" v-if="activeTab !== 'pending'">
                                <button class="btn btn-action-outline" type="button" @click="openDetail(o.id)">Chi tiết</button>
                            </td>
                        </tr>
                        <tr v-if="!tabRows.length">
                            <td :colspan="activeTab === 'pending' ? 5 : 6" class="order-empty-row">Không có đơn hàng trong mục này.</td>
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
                <div style="margin-top:4px;">SĐT giao hàng: <strong>{{ selected.order.shippingPhone || "Chưa có" }}</strong></div>
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
                    <div class="status-message" v-if="routingWarning">{{ routingWarning }}</div>
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

.order-address-scroll {
    max-width: 420px;
    overflow-x: auto;
    white-space: nowrap;
    padding-bottom: 4px;
}

.order-empty-row {
    text-align: center;
    color: #6b7280;
    padding: 16px;
}
</style>
