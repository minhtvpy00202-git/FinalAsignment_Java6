<script setup>
defineProps({
    open: {type: Boolean, default: false},
    text: {type: String, default: ""},
    type: {type: String, default: "success"}
});
</script>

<template>
    <div v-if="open && text" class="app-toast" :class="'is-' + type">{{ text }}</div>
</template>

<style scoped>
.app-toast{
    position:fixed;
    right:24px;
    top:88px;
    z-index:2100;
    border-radius:10px;
    padding:10px 14px;
    font-size:14px;
    font-weight:600;
    box-shadow:0 10px 24px rgba(0,0,0,.12);
    border:1px solid transparent;
}
.app-toast.is-success{
    background:#ecfdf5;
    color:#166534;
    border-color:#86efac;
}
.app-toast.is-error{
    background:#fee2e2;
    color:#991b1b;
    border-color:#fca5a5;
}
.app-toast.is-info{
    background:#eff6ff;
    color:#1e3a8a;
    border-color:#93c5fd;
}
</style>
