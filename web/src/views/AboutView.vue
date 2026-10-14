<script setup>
import { useRouter } from 'vue-router'
const router = useRouter()
const nativeAbout = typeof window.CRTicketAndroid?.setNativePage === 'function'
const back = () => window.history.state?.back ? router.back() : router.replace('/home')
</script>

<template>
  <main v-if="!nativeAbout" class="about-fallback">
    <button type="button" @click="back">返回</button>
    <section>
      <img :src="'/Picture1.png'" alt="CRTicket 图标" />
      <h1>CRTicket</h1>
      <p>车票制作与铁路出行工具</p>
      <a href="https://github.com/sam28403/CRTicket" target="_blank" rel="noopener noreferrer">GitHub</a>
    </section>
  </main>
</template>

<style scoped>
.about-fallback {
  max-width: 640px;
  margin: 0 auto;
  padding: 24px;
  color: var(--app-text, var(--el-text-color-primary));
}

section {
  display: grid;
  justify-items: center;
  gap: 20px;
  padding: 64px 0;
  text-align: center;
}

img {
  width: 128px;
  height: 128px;
  border-radius: 50%;
}

h1, p {
  margin: 0;
}
</style>
