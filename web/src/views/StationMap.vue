<script setup>
import { watchEffect } from 'vue'
import { useRoute } from 'vue-router'
import StationLocationMap from '@/components/StationLocationMap.vue'
import RecordedTrackMap from '@/components/RecordedTrackMap.vue'

const route = useRoute()
function failed() { console.error('CRTicketMap:failed') }
watchEffect(() => document.documentElement.classList.toggle('dark', route.query.dark === 'true'))
</script>

<template>
  <main class="native-station-map" :aria-label="`${route.query.station}车站地图`">
    <RecordedTrackMap
      v-if="route.query.track === 'true'"
      :key="route.query.source" :source="String(route.query.source || 'auto')"
      @failed="failed"
    />
    <StationLocationMap
      v-else
      :key="`${route.query.station}:${route.query.source}`"
      :station="String(route.query.station || '')"
      :source="String(route.query.source || 'auto')"
      @failed="failed"
    />
  </main>
</template>

<style scoped>
.native-station-map {
  position: fixed;
  inset: 0;
}
</style>
