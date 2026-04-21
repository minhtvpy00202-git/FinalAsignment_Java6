<script setup>
import {computed, onMounted, onUnmounted, ref, watch} from "vue";
import {onBeforeRouteLeave, useRoute, useRouter} from "vue-router";
import {BankTransferPage} from "@/legacy/pages";
import {api} from "@/api";

const {orderId, data, error, load, confirm: legacyConfirm, toCod, remove, money} = BankTransferPage.setup();
const route = useRoute();
const router = useRouter();
const paymentMessage = ref("");
const checkingStatus = ref(false);
const copiedKey = ref("");
const cancelModalOpen = ref(false);
const autoCancelSent = ref(false);
const paidSuccessModalOpen = ref(false);
const paidRedirecting = ref(false);
const paidResultType = ref("paid");
const codSwitching = ref(false);
const successTitle = computed(() => paidResultType.value === "cod" ? "Đã chuyển sang COD thành công" : "Thanh toán thành công");
const successDescription = computed(() => paidResultType.value === "cod"
    ? `Đơn #${orderId.value} đã chuyển sang hình thức thanh toán khi nhận hàng (COD).`
    : `Hệ thống đã xác nhận chuyển khoản cho đơn #${orderId.value}.`);
const successButtonText = computed(() => paidResultType.value === "cod" ? "Xem chi tiết đơn COD" : "Xem chi tiết đơn");
const transferContent = () => String(data.value?.order?.id || orderId.value || "").trim();
const copyText = async (key, text) => {
    if (!text) {
        return;
    }
    try {
        await navigator.clipboard.writeText(String(text));
        copiedKey.value = key;
        setTimeout(() => {
            if (copiedKey.value === key) {
                copiedKey.value = "";
            }
        }, 1400);
    } catch (e) {
    }
};
let pollTimer = null;
const toOrderDetail = async (resultType = "paid") => {
    if (!orderId.value || paidRedirecting.value) {
        return;
    }
    paidResultType.value = resultType;
    codSwitching.value = false;
    paidRedirecting.value = true;
    paymentMessage.value = "";
    paidSuccessModalOpen.value = true;
};
const closePaidSuccessModal = async () => {
    paidSuccessModalOpen.value = false;
    autoCancelSent.value = true;
    await router.push(`/order/order-detail?id=${orderId.value}`);
};
watch(() => data.value?.status, (newStatus) => {
    if (newStatus && newStatus !== "PENDING_PAYMENT") {
        toOrderDetail(codSwitching.value ? "cod" : "paid");
    }
});
const pollPayos = async () => {
    if (!orderId.value || !data.value?.checkoutUrl) {
        return;
    }
    try {
        const res = await api.orderWorkflow.payosStatus(orderId.value);
        if (res?.data?.paid || String(res?.data?.status || "").toUpperCase() === "PAID") {
            await toOrderDetail("paid");
        }
    } catch (e) {
    }
};
const confirm = async () => {
    checkingStatus.value = true;
    paymentMessage.value = "";
    try {
        const res = await api.orderWorkflow.confirmBankTransfer(orderId.value);
        if (res?.data?.paid) {
            await toOrderDetail("paid");
            return;
        }
        paymentMessage.value = "Hệ thống chưa ghi nhận thanh toán, vui lòng kiểm tra lại sau ít phút.";
    } catch (e) {
        paymentMessage.value = e.message || "Không kiểm tra được trạng thái thanh toán.";
    } finally {
        checkingStatus.value = false;
    }
};
const handleRemove = async () => {
    const accepted = typeof window !== "undefined"
        ? window.confirm("Bạn có chắc chắn muốn huỷ thanh toán và xoá đơn hàng này?")
        : true;
    if (!accepted) {
        return;
    }
    try {
        autoCancelSent.value = true;
        await remove();
        cancelModalOpen.value = true;
    } catch (e) {
        paymentMessage.value = e.message || "Hủy thanh toán thất bại.";
    }
};
const handleToCod = async () => {
    try {
        codSwitching.value = true;
        autoCancelSent.value = true;
        await toCod();
    } catch (e) {
        codSwitching.value = false;
        paymentMessage.value = e.message || "Không thể chuyển sang COD.";
    }
};
const sendCancelPayosOnLeave = () => {
    if (autoCancelSent.value || !orderId.value) {
        return;
    }
    autoCancelSent.value = true;
    const endpoint = "/api/order-workflow/bank-transfer/cancel/payos";
    const body = new URLSearchParams({
        orderId: String(orderId.value),
        reason: "Leave bank transfer page"
    });
    try {
        if (navigator.sendBeacon) {
            navigator.sendBeacon(endpoint, body);
            return;
        }
    } catch (e) {
    }
    try {
        fetch(endpoint, {
            method: "POST",
            credentials: "include",
            headers: {"Content-Type": "application/x-www-form-urlencoded;charset=UTF-8"},
            body: body.toString(),
            keepalive: true
        });
    } catch (e) {
    }
};
const onBeforeUnload = () => {
    sendCancelPayosOnLeave();
};

const goHome = () => {
    router.push("/");
};

onBeforeRouteLeave(() => {
    sendCancelPayosOnLeave();
    return true;
});

onMounted(() => {
    const queryId = Number(route.query.id || route.query.orderId || "");
    if (Number.isFinite(queryId) && queryId > 0) {
        orderId.value = String(queryId);
        load();
    }
    pollTimer = setInterval(pollPayos, 5000);
    window.addEventListener("beforeunload", onBeforeUnload);
});
onUnmounted(() => {
    if (pollTimer) {
        clearInterval(pollTimer);
    }
    window.removeEventListener("beforeunload", onBeforeUnload);
});
</script>

<template>
    <main class="container page-shell">
        <h3 class="page-title">Thanh toán chuyển khoản ngân hàng</h3>
        <div v-if="error" class="status-message status-error">{{ error }}</div>
        <div v-if="paymentMessage" class="status-message">{{ paymentMessage }}</div>
        <div v-if="data" class="card transfer-sheet">
            <div class="transfer-sheet-top">
                Mở App Ngân hàng để <strong>quét mã QR</strong> hoặc <strong>chuyển khoản</strong> chính xác thông tin bên dưới.
            </div>
            <div class="transfer-sheet-body">
                <div class="transfer-qr-panel">
                    <div class="transfer-qr-head">QR Thanh Toán</div>
                    <img v-if="data.qrImageSrc" :src="data.qrImageSrc" alt="QR chuyển khoản" class="transfer-qr-image">
                    <div v-else class="status-message">Không thể tải mã QR, vui lòng chuyển khoản theo thông tin bên dưới.</div>
                    <div class="transfer-qr-bank">{{ data.bankName || "Ngân hàng" }}</div>
                </div>
                <div class="transfer-info-panel">
                    <div class="transfer-info-row">
                        <div class="transfer-info-label">Ngân hàng</div>
                        <div class="transfer-info-value">{{ data.bankName }}</div>
                    </div>
                    <div class="transfer-info-row">
                        <div class="transfer-info-label">Chủ tài khoản</div>
                        <div class="transfer-info-value transfer-copy-line">
                            <span>{{ data.accountName }}</span>
                            <button class="btn btn-outline transfer-copy-btn" type="button" @click="copyText('accountName', data.accountName)">{{ copiedKey === "accountName" ? "Đã chép" : "Sao chép" }}</button>
                        </div>
                    </div>
                    <div class="transfer-info-row">
                        <div class="transfer-info-label">Số tài khoản</div>
                        <div class="transfer-info-value transfer-copy-line">
                            <span>{{ data.accountNumber }}</span>
                            <button class="btn btn-outline transfer-copy-btn" type="button" @click="copyText('accountNumber', data.accountNumber)">{{ copiedKey === "accountNumber" ? "Đã chép" : "Sao chép" }}</button>
                        </div>
                    </div>
                    <div class="transfer-info-row">
                        <div class="transfer-info-label">Số tiền</div>
                        <div class="transfer-info-value transfer-copy-line">
                            <span>{{ money(data.totalPrice) }} VND</span>
                            <button class="btn btn-outline transfer-copy-btn" type="button" @click="copyText('amount', `${money(data.totalPrice)} VND`)">{{ copiedKey === "amount" ? "Đã chép" : "Sao chép" }}</button>
                        </div>
                    </div>
                    <div class="transfer-info-row">
                        <div class="transfer-info-label">Nội dung chuyển khoản</div>
                        <div class="transfer-info-value transfer-copy-line">
                            <span>{{ transferContent() }}</span>
                            <button class="btn btn-outline transfer-copy-btn" type="button" @click="copyText('content', transferContent())">{{ copiedKey === "content" ? "Đã chép" : "Sao chép" }}</button>
                        </div>
                    </div>
                    <div class="transfer-note">
                        Lưu ý: chuyển đúng số tiền <strong>{{ money(data.totalPrice) }} VND</strong> và nội dung <strong>{{ transferContent() }}</strong>.
                    </div>
                </div>
            </div>
            <div class="transfer-actions">
                <button class="btn btn-outline" type="button" :disabled="checkingStatus" @click="confirm">{{ checkingStatus ? "Đang kiểm tra..." : "Kiểm tra trạng thái" }}</button>
                <button class="btn btn-outline" type="button" @click="handleToCod">Chuyển sang COD</button>
                <button class="btn btn-outline" type="button" @click="handleRemove">Hủy thanh toán</button>
            </div>
        </div>

        <!-- Cancel Success Modal -->
        <div class="modal-backdrop" :class="{open: cancelModalOpen}" v-if="cancelModalOpen">
            <div class="admin-modal-panel" style="max-width: 400px; text-align: center;">
                <div class="modal-header" style="justify-content: center; border-bottom: none; padding-bottom: 0;">
                    <div style="width: 60px; height: 60px; background: #e8f5e9; color: #4caf50; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-size: 32px; margin: 0 auto;">
                        ✓
                    </div>
                </div>
                <div style="padding: 20px 0;">
                    <h4 style="margin-bottom: 10px;">Đã hủy thanh toán</h4>
                    <p style="color: #666; font-size: 15px;">Đơn hàng của bạn đã được hủy thành công.</p>
                </div>
                <div class="admin-form-actions" style="justify-content: center;">
                    <button class="btn btn-primary" type="button" @click="goHome">Về trang chủ</button>
                </div>
            </div>
        </div>
        <div class="modal-backdrop" :class="{open: paidSuccessModalOpen}" v-if="paidSuccessModalOpen">
            <div class="admin-modal-panel" style="max-width: 420px; text-align: center;">
                <div class="modal-header" style="justify-content: center; border-bottom: none; padding-bottom: 0;">
                    <div style="width: 60px; height: 60px; background: #e8f5e9; color: #4caf50; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-size: 32px; margin: 0 auto;">
                        ✓
                    </div>
                </div>
                <div style="padding: 20px 0;">
                    <h4 style="margin-bottom: 10px;">{{ successTitle }}</h4>
                    <p style="color: #666; font-size: 15px;">{{ successDescription }}</p>
                </div>
                <div class="admin-form-actions" style="justify-content: center;">
                    <button class="btn btn-primary" type="button" @click="closePaidSuccessModal">{{ successButtonText }}</button>
                </div>
            </div>
        </div>
    </main>
</template>
