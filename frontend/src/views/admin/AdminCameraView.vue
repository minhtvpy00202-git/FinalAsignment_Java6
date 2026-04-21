<script setup>
import {onMounted, onUnmounted, ref, watch} from "vue";
import {AdminCameraPage} from "@/legacy/pages";
import AdminNav from "@/components/AdminNav.vue";

const {url, load} = AdminCameraPage.setup();
const motionEnabled = ref(true);
const motionRows = ref(["Chưa phát hiện chuyển động."]);
const videoRef = ref(null);
let hls = null;

const toggleMotion = () => {
    motionEnabled.value = !motionEnabled.value;
};
const ensureHls = async () => {
    if (window.Hls) {
        return window.Hls;
    }
    await new Promise((resolve, reject) => {
        const script = document.createElement("script");
        script.src = "https://cdn.jsdelivr.net/npm/hls.js@1.5.13";
        script.onload = resolve;
        script.onerror = reject;
        document.body.appendChild(script);
    });
    return window.Hls;
};
const bindStream = async () => {
    if (!videoRef.value || !url.value) {
        return;
    }
    const Hls = await ensureHls();
    if (Hls?.isSupported()) {
        if (hls) {
            hls.destroy();
        }
        hls = new Hls({lowLatencyMode: true});
        hls.loadSource(url.value);
        hls.attachMedia(videoRef.value);
    } else if (videoRef.value.canPlayType("application/vnd.apple.mpegurl")) {
        videoRef.value.src = url.value;
    }
};

onMounted(() => {
    bindStream();
});
watch(url, () => {
    bindStream();
});
onUnmounted(() => {
    if (hls) {
        hls.destroy();
    }
});
</script>

<template>
    <main class="container admin-product-page">
        <h3 class="page-title">Camera cửa hàng</h3>
        <div class="admin-product-shell">
            <div class="admin-product-menu">
                <AdminNav/>
            </div>
            <div class="admin-product-main">
            <section class="two-column camera-grid">
                <div class="card">
                    <div class="camera-card-header">
                        <h4>Luồng trực tiếp</h4>
                        <span class="badge hot">Đang kết nối</span>
                    </div>
                    <div class="camera-stream-wrap">
                        <video ref="videoRef" controls autoplay muted playsinline class="camera-stream"></video>
                    </div>
                    <div class="status-message camera-stream-message" v-if="!url">Chưa cấu hình đường dẫn HLS cho camera.</div>
                </div>
                <div class="card">
                    <div class="camera-card-header">
                        <h4>Thông báo chuyển động</h4>
                        <button class="btn btn-outline-primary" type="button" @click="toggleMotion">{{ motionEnabled ? "Tạm dừng" : "Tiếp tục" }}</button>
                    </div>
                    <div class="review-list camera-motion-list">
                        <div class="status-message" v-for="(row, idx) in motionRows" :key="idx">{{ row }}</div>
                    </div>
                </div>
            </section>
            <div class="table-actions" style="margin-top:12px;">
                <button class="btn btn-primary" type="button" @click="load">Làm mới</button>
            </div>
        </div>
        </div>
    </main>
</template>
