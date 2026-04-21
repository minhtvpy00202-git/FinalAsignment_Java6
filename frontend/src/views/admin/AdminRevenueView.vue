<script setup>
import {computed, nextTick, onMounted, reactive, ref, watch} from "vue";
import {useRoute, useRouter} from "vue-router";
import {api} from "@/api";
import AdminNav from "@/components/AdminNav.vue";
import {formatVnd} from "@/utils/format";

const route = useRoute();
const router = useRouter();
const revenueTableRef = ref(null);
const rows = ref([]);
const loading = ref(false);
const exporting = ref(false);
const error = ref("");
const now = new Date();
const currentYear = now.getFullYear();
const currentMonth = now.getMonth() + 1;
const currentQuarter = Math.floor((currentMonth - 1) / 3) + 1;
const summaryParams = reactive({
    fromDate: "",
    toDate: "",
    sortField: "orderId",
    sortDir: "asc"
});
const periodParams = reactive({
    day: formatDateInput(now),
    week: formatWeekInput(now),
    month: currentMonth,
    quarter: currentQuarter,
    year: currentYear,
    sortField: "orderId",
    sortDir: "asc"
});
const months = Array.from({length: 12}, (_, i) => i + 1);
const quarters = [1, 2, 3, 4];
const money = formatVnd;
const viewMode = computed(() => route.meta.revenueView || "summary");
const isSummaryMode = computed(() => viewMode.value === "summary");
const isDayMode = computed(() => viewMode.value === "day");
const showLineChart = computed(() => !isSummaryMode.value && !isDayMode.value);
const total = computed(() => rows.value.reduce((sum, row) => sum + Number(row.lineTotal || 0), 0));
const activeRange = computed(() => {
    if (isSummaryMode.value) {
        return {fromDate: summaryParams.fromDate, toDate: summaryParams.toDate};
    }
    return buildRange(viewMode.value, periodParams);
});
const chartPalette = ["#2563eb", "#dc2626", "#16a34a", "#d97706", "#7c3aed", "#db2777", "#0891b2", "#65a30d"];
const categoryBreakdown = computed(() => {
    const map = new Map();
    for (const row of rows.value) {
        const key = (row.categoryName || "Khác").trim() || "Khác";
        map.set(key, (map.get(key) || 0) + Number(row.lineTotal || 0));
    }
    const list = Array.from(map.entries()).map(([name, amount], index) => ({
        name,
        amount,
        color: chartPalette[index % chartPalette.length]
    }));
    return list.sort((a, b) => b.amount - a.amount);
});
const pieTotal = computed(() => categoryBreakdown.value.reduce((sum, item) => sum + item.amount, 0));
const hoveredSliceName = ref("");
const pieTooltip = reactive({
    visible: false,
    text: "",
    x: 0,
    y: 0
});
const lineTooltip = reactive({
    visible: false,
    text: "",
    x: 0,
    y: 0
});
const pieSlices = computed(() => {
    const totalValue = pieTotal.value;
    if (!totalValue) {
        return [];
    }
    let acc = 0;
    return categoryBreakdown.value.map((item) => {
        const ratio = item.amount / totalValue;
        const startAngle = acc * 360;
        const endAngle = (acc + ratio) * 360;
        acc += ratio;
        return {
            ...item,
            ratio,
            path: describePieArc(100, 100, 86, startAngle, endAngle)
        };
    });
});
const lineSeries = computed(() => {
    if (!showLineChart.value) {
        return [];
    }
    const map = new Map();
    for (const row of rows.value) {
        const date = parseDate(row.orderCreateDate);
        if (!date) {
            continue;
        }
        if (viewMode.value === "quarter" || viewMode.value === "year") {
            const monthKey = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}`;
            map.set(monthKey, (map.get(monthKey) || 0) + Number(row.lineTotal || 0));
        } else {
            const dayKey = formatDateInput(date);
            map.set(dayKey, (map.get(dayKey) || 0) + Number(row.lineTotal || 0));
        }
    }
    const labels = buildLineLabels(viewMode.value, activeRange.value);
    return labels.map((item) => ({
        key: item.key,
        label: item.label,
        value: map.get(item.key) || 0
    }));
});
const lineChart = computed(() => {
    const data = lineSeries.value;
    if (!data.length) {
        return {points: "", circles: [], labels: []};
    }
    const width = 760;
    const height = 280;
    const paddingLeft = 56;
    const paddingRight = 24;
    const paddingTop = 24;
    const paddingBottom = 48;
    const innerWidth = width - paddingLeft - paddingRight;
    const innerHeight = height - paddingTop - paddingBottom;
    const maxValue = Math.max(...data.map((item) => item.value), 1);
    const stepX = data.length > 1 ? innerWidth / (data.length - 1) : 0;
    const circles = data.map((item, index) => {
        const x = paddingLeft + stepX * index;
        const y = paddingTop + (1 - item.value / maxValue) * innerHeight;
        return {x, y, ...item};
    });
    const points = circles.map((item) => `${item.x},${item.y}`).join(" ");
    const labels = circles.map((item, index) => ({
        x: item.x,
        y: height - 22,
        text: shouldShowLineLabel(index, circles.length) ? item.label : ""
    }));
    const yTicks = buildYAxisTicks(maxValue, 5).map((tick) => ({
        value: tick,
        y: paddingTop + (1 - tick / maxValue) * innerHeight
    }));
    const xAxisTitle = axisLabelByMode(viewMode.value);
    return {points, circles, labels, yTicks, xAxisTitle};
});
const chartTitle = computed(() => {
    if (viewMode.value === "week") {
        return "Biểu đồ doanh thu theo ngày trong tuần";
    }
    if (viewMode.value === "month") {
        return "Biểu đồ doanh thu theo ngày trong tháng";
    }
    if (viewMode.value === "quarter") {
        return "Biểu đồ doanh thu theo tháng trong quý";
    }
    return "Biểu đồ doanh thu theo tháng trong năm";
});
const modeTitle = computed(() => {
    if (viewMode.value === "day") {
        return "Doanh thu theo ngày";
    }
    if (viewMode.value === "week") {
        return "Doanh thu theo tuần";
    }
    if (viewMode.value === "month") {
        return "Doanh thu theo tháng";
    }
    if (viewMode.value === "quarter") {
        return "Doanh thu theo quý";
    }
    if (viewMode.value === "year") {
        return "Doanh thu theo năm";
    }
    return "Doanh thu theo đơn hàng";
});
const revenueTabs = [
    {mode: "summary", label: "Doanh thu tổng", to: "/admin/revenue"},
    {mode: "day", label: "Doanh thu ngày", to: "/admin/revenue/day"},
    {mode: "week", label: "Doanh thu tuần", to: "/admin/revenue/week"},
    {mode: "month", label: "Doanh thu tháng", to: "/admin/revenue/month"},
    {mode: "quarter", label: "Doanh thu quý", to: "/admin/revenue/quarter"},
    {mode: "year", label: "Doanh thu năm", to: "/admin/revenue/year"}
];
const openRevenueTab = async (to) => {
    if (!to || route.path === to) {
        return;
    }
    await router.push(to);
};
const scrollToRevenueTable = async () => {
    await nextTick();
    revenueTableRef.value?.scrollIntoView({behavior: "smooth", block: "start"});
};
const load = async () => {
    loading.value = true;
    error.value = "";
    try {
        const payload = (await api.admin.reports.revenue({
            fromDate: activeRange.value.fromDate,
            toDate: activeRange.value.toDate,
            sortField: isSummaryMode.value ? summaryParams.sortField : periodParams.sortField,
            sortDir: isSummaryMode.value ? summaryParams.sortDir : periodParams.sortDir
        })).data || {};
        rows.value = payload.rows || [];
    } catch (e) {
        rows.value = [];
        error.value = e.message || "Không tải được dữ liệu doanh thu";
    } finally {
        loading.value = false;
    }
};
const applyFilters = async () => {
    await load();
    await scrollToRevenueTable();
};
const exportExcel = async () => {
    exporting.value = true;
    try {
        const params = new URLSearchParams();
        if (activeRange.value.fromDate) params.append("fromDate", activeRange.value.fromDate);
        if (activeRange.value.toDate) params.append("toDate", activeRange.value.toDate);
        params.append("sortField", isSummaryMode.value ? summaryParams.sortField : periodParams.sortField);
        params.append("sortDir", isSummaryMode.value ? summaryParams.sortDir : periodParams.sortDir);
        params.append("mode", viewMode.value);
        params.append("format", "xlsx");
        const response = await fetch(`/api/admin/reports/revenue/export?${params.toString()}`, {
            method: "GET",
            credentials: "include"
        });
        if (!response.ok) {
            let message = "Không thể xuất file Excel";
            try {
                const payload = await response.json();
                message = payload?.message || message;
            } catch (e) {
            }
            throw new Error(message);
        }
        const blob = await response.blob();
        const url = window.URL.createObjectURL(blob);
        const link = document.createElement("a");
        link.href = url;
        const cd = response.headers.get("content-disposition") || "";
        const match = cd.match(/filename=\"?([^\";]+)\"?/i);
        link.download = match?.[1] || `doanh-thu-${viewMode.value}.xlsx`;
        document.body.appendChild(link);
        link.click();
        link.remove();
        window.URL.revokeObjectURL(url);
    } catch (e) {
        error.value = e.message || "Không thể xuất file Excel";
    } finally {
        exporting.value = false;
    }
};
const clearSummaryFilters = async () => {
    summaryParams.fromDate = "";
    summaryParams.toDate = "";
    summaryParams.sortField = "orderId";
    summaryParams.sortDir = "asc";
    await applyFilters();
};
onMounted(load);
watch(() => route.fullPath, load);

function formatDateInput(date) {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, "0");
    const day = String(date.getDate()).padStart(2, "0");
    return `${year}-${month}-${day}`;
}
function parseDate(text) {
    if (!text) {
        return null;
    }
    const date = new Date(text);
    if (Number.isNaN(date.getTime())) {
        return null;
    }
    return date;
}
function buildRange(periodValue, state) {
    const today = new Date();
    if (periodValue === "day") {
        return {fromDate: state.day, toDate: state.day};
    }
    if (periodValue === "week") {
        const {start, end} = getWeekRangeFromInput(state.week);
        return {fromDate: formatDateInput(start), toDate: formatDateInput(end)};
    }
    if (periodValue === "month") {
        const from = new Date(state.year, Number(state.month) - 1, 1);
        const to = new Date(state.year, Number(state.month), 0);
        return {fromDate: formatDateInput(from), toDate: formatDateInput(to)};
    }
    if (periodValue === "quarter") {
        const startMonth = (Number(state.quarter) - 1) * 3;
        const from = new Date(state.year, startMonth, 1);
        const to = new Date(state.year, startMonth + 3, 0);
        return {fromDate: formatDateInput(from), toDate: formatDateInput(to)};
    }
    return {
        fromDate: `${state.year}-01-01`,
        toDate: `${state.year}-12-31`
    };
}
function startOfWeek(date) {
    const clone = new Date(date);
    const day = clone.getDay();
    const diff = day === 0 ? -6 : 1 - day;
    clone.setDate(clone.getDate() + diff);
    clone.setHours(0, 0, 0, 0);
    return clone;
}
function formatWeekInput(date) {
    const monday = startOfWeek(date);
    const thursday = new Date(monday);
    thursday.setDate(monday.getDate() + 3);
    const weekYear = thursday.getFullYear();
    const jan4 = new Date(weekYear, 0, 4);
    const firstWeekMonday = startOfWeek(jan4);
    const diffDays = Math.round((monday.getTime() - firstWeekMonday.getTime()) / 86400000);
    const weekNumber = 1 + Math.floor(diffDays / 7);
    return `${weekYear}-W${String(weekNumber).padStart(2, "0")}`;
}
function getWeekRangeFromInput(value) {
    const match = /^(\d{4})-W(\d{2})$/.exec(value || "");
    if (!match) {
        const start = startOfWeek(new Date());
        const end = new Date(start);
        end.setDate(start.getDate() + 6);
        return {start, end};
    }
    const year = Number(match[1]);
    const week = Number(match[2]);
    const jan4 = new Date(year, 0, 4);
    const firstWeekMonday = startOfWeek(jan4);
    const start = new Date(firstWeekMonday);
    start.setDate(firstWeekMonday.getDate() + (week - 1) * 7);
    const end = new Date(start);
    end.setDate(start.getDate() + 6);
    return {start, end};
}
function buildLineLabels(periodValue, range) {
    const fromDate = parseDate(range.fromDate);
    const toDate = parseDate(range.toDate);
    if (!fromDate || !toDate) {
        return [];
    }
    if (periodValue === "quarter" || periodValue === "year") {
        const labels = [];
        const current = new Date(fromDate.getFullYear(), fromDate.getMonth(), 1);
        const end = new Date(toDate.getFullYear(), toDate.getMonth(), 1);
        while (current <= end) {
            labels.push({
                key: `${current.getFullYear()}-${String(current.getMonth() + 1).padStart(2, "0")}`,
                label: `T${current.getMonth() + 1}`
            });
            current.setMonth(current.getMonth() + 1);
        }
        return labels;
    }
    const labels = [];
    const current = new Date(fromDate);
    while (current <= toDate) {
        labels.push({
            key: formatDateInput(current),
            label: `${String(current.getDate()).padStart(2, "0")}/${String(current.getMonth() + 1).padStart(2, "0")}`
        });
        current.setDate(current.getDate() + 1);
    }
    return labels;
}
function shouldShowLineLabel(index, totalCount) {
    if (totalCount <= 10) {
        return true;
    }
    const every = Math.ceil(totalCount / 8);
    return index === 0 || index === totalCount - 1 || index % every === 0;
}
function polarToCartesian(cx, cy, r, angle) {
    const rad = ((angle - 90) * Math.PI) / 180;
    return {x: cx + r * Math.cos(rad), y: cy + r * Math.sin(rad)};
}
function describePieArc(cx, cy, r, startAngle, endAngle) {
    const start = polarToCartesian(cx, cy, r, endAngle);
    const end = polarToCartesian(cx, cy, r, startAngle);
    const largeArcFlag = endAngle - startAngle <= 180 ? "0" : "1";
    return `M ${cx} ${cy} L ${start.x} ${start.y} A ${r} ${r} 0 ${largeArcFlag} 0 ${end.x} ${end.y} Z`;
}
function buildYAxisTicks(maxValue, steps = 5) {
    const safeMax = Math.max(1, Number(maxValue || 0));
    const list = [];
    for (let i = 0; i <= steps; i++) {
        list.push((safeMax / steps) * i);
    }
    return list;
}
function axisLabelByMode(mode) {
    if (mode === "week" || mode === "month") {
        return "Ngày";
    }
    if (mode === "quarter" || mode === "year") {
        return "Tháng";
    }
    return "Thời gian";
}
function showPieTooltip(slice, event) {
    hoveredSliceName.value = slice?.name || "";
    pieTooltip.text = `${slice?.name || ""}: ${money(slice?.amount || 0)} VND (${((slice?.ratio || 0) * 100).toFixed(1)}%)`;
    pieTooltip.visible = true;
    movePieTooltip(event);
}
function movePieTooltip(event) {
    if (!event) return;
    const gap = 14;
    pieTooltip.x = Number(event.clientX || 0) + gap;
    pieTooltip.y = Number(event.clientY || 0) + gap;
}
function hidePieTooltip() {
    hoveredSliceName.value = "";
    pieTooltip.visible = false;
    pieTooltip.text = "";
}
function showLineTooltip(point, event) {
    lineTooltip.text = money(point?.value || 0);
    lineTooltip.visible = true;
    moveLineTooltip(event);
}
function moveLineTooltip(event) {
    const gap = 14;
    lineTooltip.x = Number(event.clientX || 0) + gap;
    lineTooltip.y = Number(event.clientY || 0) + gap;
}
function hideLineTooltip() {
    lineTooltip.visible = false;
    lineTooltip.text = "";
}
</script>

<template>
    <main class="container admin-product-page">
        <h3 class="page-title">Báo cáo doanh thu</h3>
        <div class="admin-product-shell">
            <div class="admin-product-menu">
                <AdminNav/>
            </div>
            <div class="admin-product-main">
                <div class="revenue-title">BẢNG DOANH THU BÁN HÀNG</div>
            <div class="revenue-tabs">
                <button
                    v-for="tab in revenueTabs"
                    :key="tab.mode"
                    class="revenue-tab-btn"
                    :class="{active: viewMode === tab.mode}"
                    type="button"
                    @click="openRevenueTab(tab.to)"
                >
                    {{ tab.label }}
                </button>
            </div>
            <h4 class="revenue-subtitle">{{ modeTitle }}</h4>
            <form class="card revenue-filter" @submit.prevent="applyFilters" v-if="isSummaryMode">
                <div class="form-group">
                    <label>Từ ngày</label>
                    <input type="date" v-model="summaryParams.fromDate" class="form-control">
                </div>
                <div class="form-group">
                    <label>Đến ngày</label>
                    <input type="date" v-model="summaryParams.toDate" class="form-control">
                </div>
                <div class="form-group">
                    <label>Sắp xếp theo</label>
                    <select v-model="summaryParams.sortField" class="form-control">
                        <option value="orderId">Mã đơn hàng</option>
                        <option value="productName">Tên sản phẩm</option>
                        <option value="quantity">Số lượng</option>
                        <option value="unitPrice">Đơn giá</option>
                        <option value="discountAmount">Giảm giá</option>
                        <option value="lineTotal">Thành tiền</option>
                    </select>
                </div>
                <div class="form-group">
                    <label>Chiều sắp xếp</label>
                    <select v-model="summaryParams.sortDir" class="form-control">
                        <option value="asc">Tăng dần</option>
                        <option value="desc">Giảm dần</option>
                    </select>
                </div>
                <div class="table-actions">
                    <button class="btn btn-primary" type="submit" :disabled="loading">Lọc</button>
                    <button class="btn btn-outline-primary" type="button" @click="clearSummaryFilters" :disabled="loading">Xoá lọc</button>
                    <button class="btn btn-outline-primary" type="button" @click="exportExcel" :disabled="exporting || loading">{{ exporting ? "Đang xuất..." : "Xuất Excel" }}</button>
                </div>
            </form>
            <form class="card revenue-filter" @submit.prevent="applyFilters" v-else>
                <div class="form-group" v-if="viewMode === 'day'">
                    <label>Ngày</label>
                    <input type="date" v-model="periodParams.day" class="form-control">
                </div>
                <div class="form-group" v-if="viewMode === 'week'">
                    <label>Chọn tuần</label>
                    <input type="week" v-model="periodParams.week" class="form-control">
                </div>
                <div class="form-group" v-if="viewMode === 'month'">
                    <label>Tháng</label>
                    <select v-model.number="periodParams.month" class="form-control">
                        <option v-for="m in months" :key="`m-${m}`" :value="m">Tháng {{ m }}</option>
                    </select>
                </div>
                <div class="form-group" v-if="viewMode === 'quarter'">
                    <label>Quý</label>
                    <select v-model.number="periodParams.quarter" class="form-control">
                        <option v-for="q in quarters" :key="`q-${q}`" :value="q">Quý {{ q }}</option>
                    </select>
                </div>
                <div class="form-group" v-if="viewMode === 'month' || viewMode === 'quarter' || viewMode === 'year'">
                    <label>Năm</label>
                    <input type="number" min="2000" max="2100" v-model.number="periodParams.year" class="form-control">
                </div>
                <div class="form-group">
                    <label>Sắp xếp theo</label>
                    <select v-model="periodParams.sortField" class="form-control">
                        <option value="orderId">Mã đơn hàng</option>
                        <option value="productName">Tên sản phẩm</option>
                        <option value="quantity">Số lượng</option>
                        <option value="unitPrice">Đơn giá</option>
                        <option value="discountAmount">Giảm giá</option>
                        <option value="lineTotal">Thành tiền</option>
                    </select>
                </div>
                <div class="form-group">
                    <label>Chiều sắp xếp</label>
                    <select v-model="periodParams.sortDir" class="form-control">
                        <option value="asc">Tăng dần</option>
                        <option value="desc">Giảm dần</option>
                    </select>
                </div>
                <div class="table-actions">
                    <button class="btn btn-primary" type="submit" :disabled="loading">Lọc dữ liệu</button>
                    <button class="btn btn-outline-primary" type="button" @click="exportExcel" :disabled="exporting || loading">{{ exporting ? "Đang xuất..." : "Xuất Excel" }}</button>
                </div>
            </form>
            <div v-if="error" class="status-message status-error">{{ error }}</div>
            <div class="revenue-chart-grid" v-if="!isSummaryMode">
                <div class="card revenue-chart-card">
                    <h4>Tỷ lệ  % doanh thu theo thể loại</h4>
                    <div v-if="pieSlices.length" class="revenue-pie-wrap">
                        <svg viewBox="0 0 200 200" class="revenue-pie-chart">
                            <path
                                v-for="slice in pieSlices"
                                :key="slice.name"
                                :d="slice.path"
                                :fill="slice.color"
                                :class="{active: hoveredSliceName === slice.name}"
                                @mouseenter="showPieTooltip(slice, $event)"
                                @mousemove="movePieTooltip($event)"
                                @mouseleave="hidePieTooltip"
                            />
                        </svg>
                        <div
                            v-if="pieTooltip.visible"
                            class="revenue-pie-tooltip"
                            :style="{ left: `${pieTooltip.x}px`, top: `${pieTooltip.y}px` }"
                        >
                            {{ pieTooltip.text }}
                        </div>
                    </div>
                    <div v-else class="status-message">Chưa có dữ liệu doanh thu theo thể loại.</div>
                </div>
                <div class="card revenue-chart-card" v-if="showLineChart">
                    <h4>{{ chartTitle }}</h4>
                    <div v-if="lineChart.circles.length" class="revenue-line-wrap">
                        <svg viewBox="0 0 760 280" class="revenue-line-chart">
                            <line x1="56" y1="232" x2="736" y2="232" stroke="#d1d5db" stroke-width="1"></line>
                            <line x1="56" y1="24" x2="56" y2="232" stroke="#d1d5db" stroke-width="1"></line>
                            <line
                                v-for="(tick, index) in lineChart.yTicks"
                                :key="'ytick-' + index"
                                x1="56"
                                :y1="tick.y"
                                x2="736"
                                :y2="tick.y"
                                stroke="#f3f4f6"
                                stroke-width="1"
                            />
                            <text
                                v-for="(tick, index) in lineChart.yTicks"
                                :key="'ylabel-' + index"
                                x="44"
                                :y="tick.y + 4"
                                text-anchor="end"
                                class="revenue-line-axis-value"
                            >
                                {{ money(tick.value) }}
                            </text>
                            <polyline :points="lineChart.points" fill="none" stroke="#2563eb" stroke-width="3"></polyline>
                            <circle
                                v-for="(point, index) in lineChart.circles"
                                :key="`pt-${index}`"
                                :cx="point.x"
                                :cy="point.y"
                                r="5"
                                fill="#1d4ed8"
                                @mouseenter="showLineTooltip(point, $event)"
                                @mousemove="moveLineTooltip($event)"
                                @mouseleave="hideLineTooltip"
                            ></circle>
                            <text v-for="(item, index) in lineChart.labels" :key="`lb-${index}`" :x="item.x" :y="item.y" text-anchor="middle" class="revenue-line-label">
                                {{ item.text }}
                            </text>
                            <text x="396" y="274" text-anchor="middle" class="revenue-line-axis-title">{{ lineChart.xAxisTitle }}</text>
                            <text x="56" y="16" text-anchor="start" class="revenue-line-axis-title">D. Thu</text>
                        </svg>
                        <div
                            v-if="lineTooltip.visible"
                            class="revenue-pie-tooltip"
                            :style="{ left: `${lineTooltip.x}px`, top: `${lineTooltip.y}px` }"
                        >
                            {{ lineTooltip.text }}
                        </div>
                    </div>
                    <div v-else class="status-message">Chưa có dữ liệu chuỗi thời gian.</div>
                </div>
            </div>
            <div class="card revenue-table-wrap" ref="revenueTableRef">
                <table class="revenue-table revenue-table-head">
                    <colgroup>
                        <col style="width: 6%;">
                        <col style="width: 15%;">
                        <col style="width: 29%;">
                        <col style="width: 10%;">
                        <col style="width: 13%;">
                        <col style="width: 12%;">
                        <col style="width: 15%;">
                    </colgroup>
                    <thead>
                    <tr>
                        <th>STT</th>
                        <th>Mã đơn hàng</th>
                        <th>Tên sản phẩm</th>
                        <th>Số lượng</th>
                        <th>Đơn giá</th>
                        <th>Giảm giá</th>
                        <th>Thành tiền</th>
                    </tr>
                    </thead>
                </table>
                <div class="revenue-table-scroll">
                    <table class="revenue-table revenue-table-body">
                        <colgroup>
                            <col style="width: 6%;">
                            <col style="width: 15%;">
                            <col style="width: 29%;">
                            <col style="width: 10%;">
                            <col style="width: 13%;">
                            <col style="width: 12%;">
                            <col style="width: 15%;">
                        </colgroup>
                        <tbody>
                        <tr v-for="(r, i) in rows" :key="`${r.orderId}-${i}`">
                            <td class="cell-center">{{ i + 1 }}</td>
                            <td class="cell-center">#{{ r.orderId }}</td>
                            <td>{{ r.productName }}</td>
                            <td class="cell-center">{{ r.quantity }}</td>
                            <td class="cell-center">{{ money(r.unitPrice) }}</td>
                            <td class="cell-center">{{ money(r.discountAmount) }}</td>
                            <td class="cell-center">{{ money(r.lineTotal) }}</td>
                        </tr>
                        </tbody>
                    </table>
                </div>
                <table class="revenue-table revenue-table-foot">
                    <colgroup>
                        <col style="width: 6%;">
                        <col style="width: 15%;">
                        <col style="width: 29%;">
                        <col style="width: 10%;">
                        <col style="width: 13%;">
                        <col style="width: 12%;">
                        <col style="width: 15%;">
                    </colgroup>
                    <tfoot>
                    <tr class="revenue-total-row">
                        <td colspan="6" class="total-label">Tổng cộng</td>
                        <td class="total-value">{{ money(total) }}</td>
                    </tr>
                    </tfoot>
                </table>
            </div>
        </div>
        </div>
    </main>
</template>

<style scoped>
.revenue-tabs {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    margin-bottom: 12px;
}

.revenue-tab-btn {
    border: 1px solid #d1d5db;
    border-radius: 10px;
    background: #fff;
    color: #111827;
    font-weight: 600;
    padding: 8px 12px;
}

.revenue-tab-btn.active {
    background: #111827;
    color: #fff;
    border-color: #111827;
}

.admin-product-page .page-title {
    margin-bottom: 6px;
}

.admin-product-shell {
    margin-top: 0;
}

.revenue-title {
    margin-top: 0;
}

.revenue-pie-chart path{
    transition: opacity .2s ease, transform .2s ease;
}
.revenue-pie-chart path:hover,
.revenue-pie-chart path.active{
    opacity:.88;
    transform-origin:100px 100px;
    transform:scale(1.02);
}
.revenue-line-axis-value{
    font-size:10px;
    fill:#6b7280;
}
.revenue-line-axis-title{
    font-size:12px;
    font-weight:700;
    fill:#374151;
}
.revenue-pie-tooltip{
    position:fixed;
    z-index:9999;
    pointer-events:none;
    max-width:320px;
    padding:8px 10px;
    border-radius:8px;
    background:rgba(15,23,42,.92);
    color:#fff;
    border:1px solid rgba(148,163,184,.45);
    font-size:12px;
    font-weight:600;
    line-height:1.35;
    box-shadow:0 8px 24px rgba(0,0,0,.25);
}
</style>
