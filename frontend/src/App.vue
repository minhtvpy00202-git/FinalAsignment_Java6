<script setup>
import AppHeader from "@/components/AppHeader.vue";
import AppFooter from "@/components/AppFooter.vue";
import ChatBox from "@/components/chat/ChatBox.vue";
</script>

<template>
    <AppHeader/>
    <div class="app-main">
        <router-view/>
    </div>
    <ChatBox/>
    <AppFooter/>
</template>
