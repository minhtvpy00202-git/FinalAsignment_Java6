<script setup>
import {computed, nextTick, onMounted, ref, watch} from "vue";
import {useRoute} from "vue-router";
import {api} from "@/api";
import {useSession} from "@/composables/useSession";
import {useChatSocket} from "@/composables/useChatSocket";
import AdminNav from "@/components/AdminNav.vue";
import {formatVnd} from "@/utils/format";

const {state: session, isAuthenticated, isAdmin, refreshSession} = useSession();
const {connect, subscribe, send, status} = useChatSocket();
const route = useRoute();

const conversations = ref([]);
const activeKey = ref("");
const activeHistory = ref([]);
const loadingHistory = ref(false);
const historyError = ref("");
const loadingConversations = ref(false);
const conversationError = ref("");
const subscriptionsReady = ref(false);

const lockNotice = ref(null);
const draft = ref("");
const composerError = ref("");
const scrollRef = ref(null);
const alertText = ref("");

const keyOf = (customerId, productId) => `${customerId}::${productId}`;
const activeConversation = computed(() => conversations.value.find((c) => c.key === activeKey.value) || null);
const unreadTotal = computed(() => conversations.value.reduce((sum, item) => sum + Number(item.unread || 0), 0));
const isLocked = computed(() => {
    const notice = lockNotice.value;
    const conv = activeConversation.value;
    if (!notice || !conv) return false;
    return String(notice.customerId || "") === String(conv.customerId) && Number(notice.productId || 0) === Number(conv.productId || 0);
});

const avatarText = (conv) => {
    const text = String(conv?.customerFullname || conv?.customerId || "").trim();
    if (!text) return "?";
    return text.slice(0, 1).toUpperCase();
};

const toImageUrl = (raw, fallback = "/images/product1.jpg") => {
    const value = String(raw || "").trim();
    if (!value) {
        return fallback;
    }
    if (/^https?:\/\//i.test(value)) {
        return value;
    }
    if (value.startsWith("/")) {
        return value;
    }
    return `/images/${encodeURIComponent(value)}`;
};

const formatMoney = (value) => {
    const num = Number(value || 0);
    if (!Number.isFinite(num)) {
        return "0 VND";
    }
    return `${formatVnd(num)} VND`;
};

const beep = () => {
    if (typeof window === "undefined" || !window.AudioContext) {
        return;
    }
    const context = new window.AudioContext();
    const osc = context.createOscillator();
    const gain = context.createGain();
    osc.type = "sine";
    osc.frequency.value = 880;
    gain.gain.value = 0.08;
    osc.connect(gain);
    gain.connect(context.destination);
    osc.start();
    osc.stop(context.currentTime + 0.12);
};

const showIncomingSignal = (payload) => {
    const customerName = payload?.customerFullname || payload?.customerId || "Khách hàng";
    alertText.value = `${customerName} đang cần hỗ trợ ở sản phẩm #${payload?.productId || ""}`;
    beep();
};

const scrollToBottom = async () => {
    await nextTick();
    const el = scrollRef.value;
    if (!el) return;
    el.scrollTop = el.scrollHeight;
};

const normalizeConversation = (payload = {}, unread = 0, base = null) => {
    const customerId = String(payload?.customerId || "").trim();
    const productId = Number(payload?.productId || 0);
    const key = keyOf(customerId, productId);
    return {
        key,
        customerId,
        customerFullname: payload?.customerFullname || base?.customerFullname || customerId,
        customerPhoto: payload?.customerPhoto || base?.customerPhoto || "",
        productId,
        productName: payload?.productName || base?.productName || "",
        productImage: payload?.productImage || base?.productImage || "",
        productPrice: payload?.productPrice ?? base?.productPrice ?? 0,
        productDiscount: payload?.productDiscount ?? base?.productDiscount ?? 0,
        productFinalPrice: payload?.productFinalPrice ?? base?.productFinalPrice ?? 0,
        categoryName: payload?.categoryName || base?.categoryName || "",
        adminId: payload?.adminId || null,
        assignedAdminFullname: payload?.assignedAdminFullname || "",
        lockedByOtherAdmin: !!payload?.lockedByOtherAdmin,
        lastText: payload?.lastText || (payload?.content ? String(payload.content) : (payload?.mediaUrl ? "[Ảnh]" : "")),
        lastAt: payload?.lastAt || payload?.createdAt || new Date().toISOString(),
        unread
    };
};

const upsertConversation = (payload, markUnread = false) => {
    const customerId = String(payload?.customerId || "").trim();
    const productId = Number(payload?.productId || 0);
    if (!customerId || !productId) {
        return null;
    }
    const key = keyOf(customerId, productId);
    const current = conversations.value.find((c) => c.key === key);
    const nextUnread = markUnread ? Number(current?.unread || 0) + 1 : Number(current?.unread || 0);
    const item = normalizeConversation(payload, nextUnread, current);
    const next = conversations.value.filter((c) => c.key !== key);
    next.unshift(item);
    conversations.value = next.slice(0, 300);
    return item;
};

const resetUnread = (key) => {
    conversations.value = conversations.value.map((item) => item.key === key ? {...item, unread: 0} : item);
};

const onAdminTopicMessage = async (payload) => {
    const conv = upsertConversation(payload, payload?.senderRole === "USER");
    const active = activeConversation.value;
    if (active && active.customerId === payload?.customerId && Number(active.productId) === Number(payload?.productId)) {
        const incomingId = Number(payload?.id || 0);
        if (!incomingId || !activeHistory.value.some((item) => Number(item?.id || 0) === incomingId)) {
            activeHistory.value = [...activeHistory.value, payload];
        }
        resetUnread(active.key);
        await scrollToBottom();
    } else if (payload?.senderRole === "USER" && conv) {
        showIncomingSignal(conv);
    }
};

const onLockNotice = (payload) => {
    lockNotice.value = payload || null;
};

const ensureSubscriptions = () => {
    if (subscriptionsReady.value) {
        return;
    }
    subscribe("/topic/admin/messages", onAdminTopicMessage);
    subscribe("/user/queue/chat-lock", onLockNotice);
    subscriptionsReady.value = true;
};

const tryConnect = () => {
    if (!isAuthenticated.value || !isAdmin.value) {
        return;
    }
    connect();
    const timer = setInterval(() => {
        if (status.value === "CONNECTED") {
            clearInterval(timer);
            ensureSubscriptions();
        }
    }, 250);
    setTimeout(() => clearInterval(timer), 10000);
};

const loadConversations = async () => {
    if (!isAdmin.value) {
        return;
    }
    loadingConversations.value = true;
    conversationError.value = "";
    try {
        const res = await api.admin.chat.conversations();
        const list = Array.isArray(res.data) ? res.data : [];
        conversations.value = list
            .map((item) => normalizeConversation(item, 0))
            .filter((item) => item.customerId && item.productId);
        const selectedByQuery = await applyQuerySelection();
        if (!selectedByQuery && !activeKey.value && conversations.value.length > 0) {
            await chooseConversation(conversations.value[0]);
        }
    } catch (e) {
        conversationError.value = e?.message || "Không thể tải danh sách hội thoại.";
    } finally {
        loadingConversations.value = false;
    }
};

const applyQuerySelection = async () => {
    const customerId = String(route.query.customerId || "").trim();
    const productId = Number(route.query.productId || 0);
    if (!customerId || !productId) {
        return false;
    }
    let conv = conversations.value.find((item) => item.customerId === customerId && Number(item.productId) === productId);
    if (!conv) {
        conv = normalizeConversation({
            customerId,
            customerFullname: customerId,
            productId,
            lastText: "",
            lastAt: new Date().toISOString()
        }, 0);
        conversations.value = [conv, ...conversations.value.filter((item) => item.key !== conv.key)];
    }
    await chooseConversation(conv);
    return true;
};

const loadHistory = async (customerId, productId) => {
    loadingHistory.value = true;
    historyError.value = "";
    activeHistory.value = [];
    lockNotice.value = null;
    try {
        const res = await api.admin.chat.messages(customerId, productId);
        activeHistory.value = Array.isArray(res.data) ? res.data : [];
        await scrollToBottom();
    } catch (e) {
        historyError.value = e?.message || "Không thể tải lịch sử.";
    } finally {
        loadingHistory.value = false;
    }
};

const chooseConversation = async (conv) => {
    activeKey.value = conv.key;
    resetUnread(conv.key);
    await loadHistory(conv.customerId, conv.productId);
};

const openFirstUnread = async () => {
    const unread = conversations.value.find((item) => Number(item.unread || 0) > 0);
    if (!unread) {
        return;
    }
    await chooseConversation(unread);
    alertText.value = "";
};

const sendText = async () => {
    composerError.value = "";
    const conv = activeConversation.value;
    if (!conv) {
        composerError.value = "Vui lòng chọn một luồng chat.";
        return;
    }
    if (isLocked.value || conv.lockedByOtherAdmin) {
        composerError.value = lockNotice.value?.message || "Luồng chat đang do admin khác phụ trách.";
        return;
    }
    const text = String(draft.value || "").trim();
    if (!text) {
        composerError.value = "Vui lòng nhập nội dung.";
        return;
    }
    const ok = send("/app/chat.send", {productId: conv.productId, customerId: conv.customerId, content: text});
    if (!ok) {
        composerError.value = "Chưa kết nối được chat.";
        return;
    }
    draft.value = "";
};

const onComposerKeydown = async (event) => {
    if (event?.isComposing || event?.keyCode === 229) {
        return;
    }
    if (event.key === "Enter" && !event.shiftKey) {
        event.preventDefault();
        await sendText();
    }
};

onMounted(async () => {
    await refreshSession();
    tryConnect();
    await loadConversations();
});

watch(() => isAdmin.value, async () => {
    tryConnect();
    await loadConversations();
});

watch(() => [route.query.customerId, route.query.productId], async () => {
    await applyQuerySelection();
});
</script>

<template>
    <main class="container admin-product-page">
        <h3 class="page-title">Chat hỗ trợ khách hàng</h3>
        <div class="admin-product-shell">
            <div class="admin-product-menu">
                <AdminNav/>
            </div>
            <div class="admin-product-main">
                <div class="admin-chat-head">
                    <h1 class="admin-chat-title">Khung chat hỗ trợ</h1>
                    <div class="admin-chat-meta">
                        <span class="pill">{{ session.me?.fullname || session.me?.username || "Admin" }}</span>
                        <span class="pill">WS: {{ status }}</span>
                        <span class="pill" v-if="unreadTotal > 0">Mới: {{ unreadTotal }}</span>
                    </div>
                </div>

                <div v-if="alertText" class="incoming-alert">
                    <span>{{ alertText }}</span>
                    <button class="alert-btn" type="button" @click="openFirstUnread">Mở chat</button>
                </div>

                <section class="admin-chat-shell">
                    <aside class="admin-chat-left">
                        <section class="contact-panel">
                <div class="left-title">Chat hỗ trợ khách hàng</div>
                <div v-if="loadingConversations" class="left-empty">Đang tải danh sách…</div>
                <div v-else-if="conversationError" class="left-empty left-error">{{ conversationError }}</div>
                <div v-else-if="conversations.length === 0" class="left-empty">Chưa có hội thoại nào.</div>
                <button
                    v-for="c in conversations"
                    :key="c.key"
                    class="contact"
                    type="button"
                    :class="{active: c.key === activeKey}"
                    @click="chooseConversation(c)"
                >
                    <img v-if="c.customerPhoto" class="avatar avatar-img" :src="toImageUrl(c.customerPhoto, '/images/logo.png')" alt="user">
                    <div v-else class="avatar">{{ avatarText(c) }}</div>
                    <div class="contact-body">
                        <div class="contact-top">
                            <div class="contact-main">{{ c.customerFullname || c.customerId }}</div>
                            <div class="contact-time">{{ new Date(c.lastAt).toLocaleString() }}</div>
                        </div>
                        <div class="contact-sub">{{ c.customerId }} · SP #{{ c.productId }} · {{ c.categoryName || "Danh mục" }}</div>
                        <div class="contact-last">{{ c.lastText }}</div>
                    </div>
                    <div v-if="c.unread > 0" class="unread-dot">{{ c.unread }}</div>
                </button>
                        </section>
                    </aside>
                    <section class="admin-chat-right">
                <div v-if="!activeConversation" class="right-empty">Chọn một người liên hệ ở cột trái để bắt đầu.</div>
                <template v-else>
                    <div class="right-head">
                        <div class="right-profile">
                            <img v-if="activeConversation.customerPhoto" class="right-user-photo" :src="toImageUrl(activeConversation.customerPhoto, '/images/logo.png')" alt="user">
                            <div v-else class="right-user-avatar">{{ avatarText(activeConversation) }}</div>
                            <div class="right-user-meta">
                                <div class="right-title">{{ activeConversation.customerFullname || activeConversation.customerId }} · {{ activeConversation.customerId }}</div>
                                <div class="right-sub">Admin phụ trách: {{ activeHistory[0]?.assignedAdminFullname || activeConversation.assignedAdminFullname || "(chưa có)" }}</div>
                            </div>
                        </div>
                        <div class="right-product">
                            <img class="right-product-image" :src="toImageUrl(activeConversation.productImage)" alt="product">
                            <div class="right-product-meta">
                                <div class="right-product-name">{{ activeConversation.productName || ("Sản phẩm #" + activeConversation.productId) }}</div>
                                <div class="right-product-sub">Mã SP: #{{ activeConversation.productId }} · {{ activeConversation.categoryName || "Danh mục chưa rõ" }}</div>
                                <div class="right-product-price">
                                    <span class="price-final">{{ formatMoney(activeConversation.productFinalPrice) }}</span>
                                    <span v-if="Number(activeConversation.productDiscount || 0) > 0" class="price-origin">{{ formatMoney(activeConversation.productPrice) }}</span>
                                    <span v-if="Number(activeConversation.productDiscount || 0) > 0" class="price-discount-badge">-{{ Number(activeConversation.productDiscount || 0) }}%</span>
                                </div>
                            </div>
                        </div>
                    </div>

                    <div class="right-history" ref="scrollRef">
                        <div v-if="loadingHistory" class="status">Đang tải lịch sử…</div>
                        <div v-else-if="historyError" class="status status-error">{{ historyError }}</div>
                        <template v-else>
                            <div v-for="m in activeHistory" :key="m.id || (m.createdAt + '_' + m.content)" class="msg" :class="{me: m.senderRole === 'ADMIN'}">
                                <div class="bubble">
                                    <div v-if="m.content" class="text">{{ m.content }}</div>
                                    <img v-if="m.mediaUrl" class="img" :src="m.mediaUrl" alt="chat" />
                                </div>
                                <div class="time">{{ new Date(m.createdAt || Date.now()).toLocaleString() }}</div>
                            </div>
                        </template>
                    </div>

                    <div v-if="isLocked || activeConversation.lockedByOtherAdmin" class="lock-banner">{{ lockNotice?.message || "Luồng chat đang do admin khác phụ trách." }}</div>
                    <div class="composer">
                        <div v-if="composerError" class="composer-error">{{ composerError }}</div>
                        <textarea
                            v-model="draft"
                            class="input"
                            rows="2"
                            placeholder="Nhập tin nhắn…"
                            :disabled="isLocked || activeConversation.lockedByOtherAdmin"
                            @keydown="onComposerKeydown"
                        />
                        <div class="composer-actions">
                            <button class="btn" type="button" @click="sendText" :disabled="isLocked || activeConversation.lockedByOtherAdmin">Gửi</button>
                        </div>
                    </div>
                </template>
                    </section>
                </section>
            </div>
        </div>
    </main>
</template>

<style scoped>
.admin-chat-shell{display:grid;grid-template-columns:340px 1fr;gap:20px}
.admin-chat-left{align-self:start;display:flex;flex-direction:column;gap:12px}
.admin-chat-head{display:flex;align-items:flex-end;justify-content:space-between;gap:12px;margin-bottom:14px}
.admin-chat-title{font-size:22px;font-weight:800;margin:0}
.admin-chat-meta{display:flex;gap:8px;align-items:center}
.pill{border:1px solid #e5e7eb;background:#fff;border-radius:999px;padding:6px 10px;font-size:12px;color:#111827}
.incoming-alert{margin-bottom:12px;border:1px solid #bfdbfe;background:#eff6ff;color:#1e3a8a;border-radius:12px;padding:10px 12px;display:flex;align-items:center;justify-content:space-between;gap:10px}
.alert-btn{border:1px solid #1d4ed8;background:#2563eb;color:#fff;border-radius:8px;padding:6px 10px;cursor:pointer;font-size:12px;font-weight:700}
.contact-panel{border:1px solid #e5e7eb;border-radius:12px;background:#fff;overflow:auto;display:flex;flex-direction:column;height:680px}
.left-title{padding:14px 14px 12px;font-size:18px;font-weight:800;border-bottom:1px solid #e5e7eb}
.left-empty{padding:14px;color:#6b7280;font-size:13px}
.left-error{color:#991b1b}
.contact{border:0;border-bottom:1px solid #f3f4f6;background:#fff;text-align:left;padding:12px 14px;cursor:pointer;display:flex;gap:12px;align-items:flex-start;position:relative}
.contact:hover{background:#f9fafb}
.contact.active{background:#eff6ff}
.avatar{width:42px;height:42px;border-radius:50%;background:#dbeafe;color:#1e3a8a;font-size:16px;font-weight:800;display:flex;align-items:center;justify-content:center;flex:0 0 auto}
.avatar-img{object-fit:cover;border:1px solid #dbeafe}
.contact-body{min-width:0;flex:1}
.contact-top{display:flex;align-items:baseline;justify-content:space-between;gap:10px}
.contact-main{font-size:16px;font-weight:800;color:#111827;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.contact-time{font-size:11px;color:#6b7280;white-space:nowrap}
.contact-sub{margin-top:3px;font-size:12px;color:#6b7280}
.contact-last{margin-top:5px;font-size:13px;color:#1f2937;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.unread-dot{position:absolute;right:12px;top:50%;transform:translateY(-50%);min-width:20px;height:20px;border-radius:999px;background:#ef4444;color:#fff;font-size:11px;font-weight:700;display:flex;align-items:center;justify-content:center;padding:0 6px}
.admin-chat-right{border:1px solid #e5e7eb;border-radius:12px;background:#fff;overflow:hidden;display:flex;flex-direction:column;min-height:680px}
.right-empty{padding:18px;color:#6b7280}
.right-head{padding:12px;border-bottom:1px solid #e5e7eb}
.right-title{font-size:14px;font-weight:800;color:#111827}
.right-sub{margin-top:4px;font-size:12px;color:#6b7280}
.right-profile{display:flex;align-items:center;gap:10px}
.right-user-photo{width:44px;height:44px;border-radius:50%;object-fit:cover;border:1px solid #e5e7eb;background:#fff}
.right-user-avatar{width:44px;height:44px;border-radius:50%;display:flex;align-items:center;justify-content:center;background:#dbeafe;color:#1e3a8a;font-size:16px;font-weight:800}
.right-user-meta{min-width:0}
.right-product{margin-top:10px;display:flex;align-items:flex-start;gap:10px;padding:10px;border:1px solid #e5e7eb;background:#f8fafc;border-radius:10px}
.right-product-image{width:58px;height:58px;border-radius:10px;object-fit:cover;border:1px solid #e5e7eb;background:#fff;flex:0 0 auto}
.right-product-meta{min-width:0}
.right-product-name{font-size:13px;font-weight:800;color:#111827;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;max-width:420px}
.right-product-sub{margin-top:2px;font-size:12px;color:#6b7280}
.right-product-price{margin-top:5px;display:flex;align-items:center;gap:8px;flex-wrap:wrap}
.price-final{font-size:15px;font-weight:900;color:#dc2626}
.price-origin{font-size:12px;color:#9ca3af;text-decoration:line-through}
.price-discount-badge{font-size:11px;font-weight:800;color:#fff;background:#ef4444;border-radius:999px;padding:3px 8px;line-height:1}
.right-history{flex:1;min-height:0;overflow-y:scroll;overflow-x:hidden;padding:12px;background:#f8fafc;display:flex;flex-direction:column;gap:10px}
.right-history::-webkit-scrollbar{width:10px}
.right-history::-webkit-scrollbar-track{background:#e5e7eb;border-radius:999px}
.right-history::-webkit-scrollbar-thumb{background:#94a3b8;border-radius:999px}
.right-history::-webkit-scrollbar-thumb:hover{background:#64748b}
.status{padding:10px;border:1px dashed #e5e7eb;border-radius:10px;background:#fff;color:#374151;font-size:13px}
.status-error{border-color:#fecaca;background:#fff1f2;color:#991b1b}
.msg{display:flex;flex-direction:column;align-items:flex-start;gap:3px}
.msg.me{align-items:flex-end}
.bubble{max-width:78%;background:#f3f4f6;border:1px solid #e5e7eb;border-radius:12px;padding:8px 10px}
.msg.me .bubble{background:#dbeafe;border-color:#bfdbfe}
.text{white-space:pre-wrap;word-break:break-word;font-size:13px;color:#111827}
.img{margin-top:6px;max-width:100%;border-radius:10px;border:1px solid #e5e7eb;background:#fff}
.time{font-size:11px;color:#6b7280}
.lock-banner{padding:10px 12px;border-top:1px solid #e5e7eb;background:#fff7ed;color:#9a3412;font-size:13px}
.composer{border-top:1px solid #e5e7eb;padding:12px}
.composer-error{margin-bottom:8px;padding:8px;border-radius:10px;border:1px solid #fecaca;background:#fff1f2;color:#991b1b;font-size:12px}
.input{width:100%;resize:none;border:1px solid #e5e7eb;border-radius:10px;padding:8px 10px;font-size:13px;outline:none}
.input:focus{border-color:#93c5fd;box-shadow:0 0 0 3px rgba(59,130,246,.15)}
.composer-actions{margin-top:8px;display:flex;justify-content:flex-end}
.btn{border:1px solid #2563eb;background:#2563eb;color:#fff;border-radius:10px;padding:8px 12px;font-size:13px;font-weight:800;cursor:pointer}
.btn:disabled{opacity:.5;cursor:not-allowed}
@media (max-width:1200px){.admin-chat-shell{grid-template-columns:1fr}.contact-panel{height:auto;max-height:none}}
</style>
