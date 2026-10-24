<script setup>
import { computed, onBeforeUnmount, onMounted } from 'vue'
import StationFallbackMap from './StationFallbackMap.vue'
import { stationMapEmbedUrl } from '@/utils/maps/stationLocationMap.js'
import { normalizeMapSource } from '@/utils/maps/mapSources.js'
import { canConnectTransport } from '@/utils/maps/transportConnection.js'

const props = defineProps({
  station: { type: String, required: true },
  source: { type: String, default: 'auto' },
})
const emit = defineEmits(['failed'])
const selected = computed(() => normalizeMapSource(props.source))
const mapUrl = computed(() => stationMapEmbedUrl(props.station))
const controller = new AbortController()
let timer
let reported = false

function fail() {
  if (controller.signal.aborted || reported) return
  reported = true
  emit('failed')
}

onMounted(async () => {
  if (selected.value !== 'transport') return
  timer = setTimeout(fail, 15000)
  if (!navigator.userAgent.includes('CRTicket')) {
    const connected = await canConnectTransport(mapUrl.value, { signal: controller.signal })
    if (!connected) fail()
  }
})

function loaded() { clearTimeout(timer) }

onBeforeUnmount(() => {
  controller.abort()
  clearTimeout(timer)
})
</script>

<template>
  <div class="station-location-map" role="region" :aria-label="`${station}车站地图`">
    <iframe
      v-if="selected === 'transport' && mapUrl"
      :src="mapUrl"
      :title="`${station}交通地图`"
      @load="loaded"
      @error="fail"
    />
    <StationFallbackMap v-else :station="station" :source="selected" @failed="fail" />
  </div>
</template>

<style scoped>
.station-location-map {
  width: 100%;
  height: 100%;
  min-height: 240px;
  isolation: isolate;
  overflow: hidden;
  background: var(--el-fill-color-light);
}
iframe {
  display: block;
  width: 100%;
  height: 100%;
  border: 0;
}
</style>
