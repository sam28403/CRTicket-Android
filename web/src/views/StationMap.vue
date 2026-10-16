<script setup>
import { watchEffect } from 'vue'
import { useRoute } from 'vue-router'
import StationFallbackMap from '@/components/StationFallbackMap.vue'

const route = useRoute()
watchEffect(() => document.documentElement.classList.toggle('dark', route.query.dark === 'true'))
</script>

<template>
  <main class="native-station-map" :aria-label="`${route.query.station}车站地图`">
    <StationFallbackMap :station="String(route.query.station || '')" />
  </main>
</template>

<style scoped>
.native-station-map {
  position: fixed;
  inset: 0;
}
</style>
