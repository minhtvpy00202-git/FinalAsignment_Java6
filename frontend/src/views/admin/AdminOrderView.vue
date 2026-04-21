<script setup>
import {computed, nextTick, onMounted, onUnmounted, ref, watch} from "vue";
import {useRoute} from "vue-router";
import {AdminOrderPage} from "@/legacy/pages";
import AdminNav from "@/components/AdminNav.vue";
import {api} from "@/api";
import {formatDeliveredTime, formatExpectedDelivery} from "@/utils/order";
import {orderStatusLabel} from "@/utils/orderStatus";
import {formatVnd} from "@/utils/format";
import AppToast from "@/components/AppToast.vue";

const {rows, selected, status, payosCode, msg, paging, load, toPrevPage, toNextPage, detail: fetchDetail, updateStatus: persistStatus, cancelPayos, remove} = AdminOrderPage.setup();
const route = useRoute();
const mapRef = ref(null);
const detailModalOpen = ref(false);
const updatingStatus = ref(false);
const actionMessage = ref("");
const actionModalOpen = ref(false);
const confirmModalOpen = ref(false);
const confirmMessage = ref("");
const confirmAction = ref("");
const confirmOrderId = ref(null);
const declineModalOpen = ref(false);
const declineReason = ref("");
const declineError = ref("");
const declineTargetOrderId = ref(null);
const decliningRefund = ref(false);
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
const refundToastOpen = ref(false);
const refundToastText = ref("");
let refundToastTimer = null;
const money = formatVnd;
const detailLineTotal = (detail) => Number(detail?.price || 0) * Number(detail?.quantity || 0);
const detailTotalAmount = computed(() => (selected.value?.details || []).reduce((sum, item) => sum + detailLineTotal(item), 0));
const statusLabel = (value) => orderStatusLabel(value);
const isDelivered = (value) => value === "DELIVERED_SUCCESS" || value === "DONE";
const statusColor = (value) => isDelivered(value) ? "#64d441" : "#b62c54";
const refundStatusStyle = (value) => {
    const key = String(value || "").toUpperCase();
    if (key === "REFUND_REQUEST") {
        return {background: "#fef3c7", color: "#92400e", border: "1px solid #f59e0b"};
    }
    if (key === "SUCCESS") {
        return {background: "#dcfce7", color: "#166534", border: "1px solid #22c55e"};
    }
    if (key === "DECLINED") {
        return {background: "#fee2e2", color: "#991b1b", border: "1px solid #ef4444"};
    }
    return null;
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
const showRefundToast = (text) => {
    refundToastText.value = text || "Đã cập nhật yêu cầu hoàn tiền.";
    refundToastOpen.value = true;
    if (refundToastTimer) {
        clearTimeout(refundToastTimer);
    }
    refundToastTimer = setTimeout(() => {
        refundToastOpen.value = false;
    }, 2300);
};
const closeActionModal = () => {
    actionModalOpen.value = false;
};
const openConfirmModal = (message) => {
    confirmMessage.value = message || "Bạn có chắc chắn muốn thực hiện thao tác này?";
    confirmModalOpen.value = true;
};
const openConfirmAction = (action, orderId, message) => {
    confirmAction.value = action || "";
    confirmOrderId.value = orderId ?? null;
    openConfirmModal(message);
};
const closeConfirmModal = () => {
    confirmModalOpen.value = false;
    confirmMessage.value = "";
    confirmAction.value = "";
    confirmOrderId.value = null;
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
    openConfirmAction("cancel_order", selected.value.order.id, "Bạn có chắc chắn muốn huỷ và xoá đơn hàng này không?");
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
const approveRefund = async (id) => {
    if (!id) return;
    openConfirmAction("approve_refund", id, "Duyệt hoàn tiền sẽ xoá dữ liệu đơn hàng này. Bạn có chắc chắn?");
};
const confirmApproveRefund = async (id) => {
    if (!id) return;
    updatingStatus.value = true;
    try {
        await api.admin.orders.approveRefund(id);
        await load(activeTab.value);
        showRefundToast("Đã duyệt hoàn tiền thành công.");
    } catch (e) {
        showActionModal(e.message || "Không thể duyệt hoàn tiền.");
    } finally {
        updatingStatus.value = false;
    }
};
const handleConfirmAction = async () => {
    const action = confirmAction.value;
    const id = confirmOrderId.value;
    closeConfirmModal();
    if (action === "cancel_order") {
        await confirmCancelOrder();
        return;
    }
    if (action === "approve_refund") {
        await confirmApproveRefund(id);
    }
};
const openDeclineModal = (id) => {
    if (!id) return;
    declineTargetOrderId.value = id;
    declineReason.value = "";
    declineError.value = "";
    declineModalOpen.value = true;
};
const closeDeclineModal = (force = false) => {
    if (!force && decliningRefund.value) return;
    declineModalOpen.value = false;
    declineTargetOrderId.value = null;
    declineReason.value = "";
    declineError.value = "";
};
const submitDeclineRefund = async () => {
    const id = declineTargetOrderId.value;
    const reason = String(declineReason.value || "").trim();
    if (!id) {
        closeDeclineModal();
        return;
    }
    if (!reason) {
        declineError.value = "Vui lòng nhập lý do từ chối.";
        return;
    }
    declineError.value = "";
    decliningRefund.value = true;
    try {
        await api.admin.orders.declineRefund(id, reason);
        await load(activeTab.value);
        decliningRefund.value = false;
        closeDeclineModal(true);
        showRefundToast("Đã từ chối yêu cầu hoàn tiền.");
    } catch (e) {
        showActionModal(e.message || "Không thể từ chối hoàn tiền.");
    } finally {
        decliningRefund.value = false;
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
    if (refundToastTimer) {
        clearTimeout(refundToastTimer);
    }
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
                        <button class="order-tab-btn" :class="{active: activeTab === 'refund'}" type="button" @click="activeTab = 'refund'">Yêu cầu hoàn tiền</button>
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
                            <th v-if="activeTab === 'refund'">Lý do từ chối</th>
                            <th>Địa chỉ giao hàng</th>
                            <th v-if="activeTab !== 'pending'"></th>
                        </tr>
                        </thead>
                        <tbody>
                        <tr v-for="o in tabRows" :key="o.id">
                            <td>{{ o.id }}</td>
                            <td>{{ o.account?.username || "N/A" }}</td>
                            <td>
                                <span
                                    class="badge"
                                    :class="activeTab === 'refund' ? 'refund-status-badge' : ''"
                                    :style="activeTab === 'refund' ? refundStatusStyle(o.status) : {color: statusColor(o.status)}"
                                >
                                    {{ statusLabel(o.status) }}
                                </span>
                            </td>
                            <td v-if="activeTab === 'placed'">{{ formatExpectedDelivery(o) }}</td>
                            <td v-if="activeTab === 'delivered'">{{ formatDeliveredTime(o.deliveredAt) }}</td>
                            <td v-if="activeTab === 'refund'">{{ o.refundDeclineReason || "-" }}</td>
                            <td>
                                <div class="order-address-scroll">{{ o.address || "" }}</div>
                            </td>
                            <td class="table-actions refund-actions" v-if="activeTab !== 'pending'">
                                <button class="btn btn-action-outline" type="button" @click="openDetail(o.id)">Chi tiết</button>
                                <template v-if="activeTab === 'refund'">
                                    <button class="btn btn-outline-primary" type="button" @click="approveRefund(o.id)">Duyệt hoàn tiền</button>
                                    <button class="btn btn-action-solid" type="button" @click="openDeclineModal(o.id)">Từ chối</button>
                                </template>
                            </td>
                        </tr>
                        <tr v-if="!tabRows.length">
                            <td :colspan="activeTab === 'pending' ? 5 : 7" class="order-empty-row">Không có đơn hàng trong mục này.</td>
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
                        <th>Thành tiền</th>
                    </tr>
                    </thead>
                    <tbody>
                    <tr v-for="d in (selected.details || [])" :key="d.id">
                        <td>{{ d.product?.name }}</td>
                        <td>{{ money(d.price) }} VNĐ</td>
                        <td>{{ d.quantity }}</td>
                        <td>{{ d.sizeName }}</td>
                        <td>{{ money(detailLineTotal(d)) }} VNĐ</td>
                    </tr>
                    </tbody>
                </table>
                <div class="refund-detail-total">Tổng tiền: <strong>{{ money(detailTotalAmount) }} VNĐ</strong></div>
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
                    <button class="btn btn-action-solid" type="button" @click="handleConfirmAction">Xác nhận</button>
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
        <div class="modal-backdrop" :class="{open: declineModalOpen}" v-if="declineModalOpen">
            <div class="admin-modal-panel" style="max-width: 520px;">
                <div class="modal-header">
                    <h4>Từ chối hoàn tiền</h4>
                    <button type="button" class="btn btn-outline-primary" @click="closeDeclineModal">Đóng</button>
                </div>
                <div class="form-group">
                    <label>Lý do từ chối</label>
                    <textarea
                        v-model="declineReason"
                        class="refund-reason-input"
                        rows="4"
                        placeholder="Nhập lý do để gửi cho khách hàng..."
                        :disabled="decliningRefund"
                    />
                    <div v-if="declineError" class="status-message status-error" style="margin-top:8px;">{{ declineError }}</div>
                </div>
                <div class="admin-form-actions">
                    <button class="btn btn-action-solid" type="button" @click="submitDeclineRefund" :disabled="decliningRefund">
                        {{ decliningRefund ? "Đang xử lý..." : "Xác nhận từ chối" }}
                    </button>
                    <button class="btn btn-outline-primary" type="button" @click="closeDeclineModal" :disabled="decliningRefund">Huỷ</button>
                </div>
            </div>
        </div>
        <AppToast :open="refundToastOpen" :text="refundToastText" type="success" />
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
.refund-actions{
    gap:8px;
    justify-content:flex-start;
}
.refund-actions .btn{
    white-space:nowrap;
}
.refund-reason-input{
    width:100%;
    min-height:140px;
    border:1px solid #d1d5db;
    border-radius:12px;
    padding:12px 14px;
    font-size:15px;
    line-height:1.5;
    resize:vertical;
}
.refund-reason-input:focus{
    outline:none;
    border-color:#111827;
    box-shadow:0 0 0 2px rgba(17,24,39,.08);
}
.refund-detail-total{
    margin-top:10px;
    text-align:right;
    font-size:16px;
}
.refund-status-badge{
    text-transform:uppercase;
    letter-spacing:.3px;
    font-weight:700;
    border-radius:999px;
    padding:4px 10px;
}
</style>
