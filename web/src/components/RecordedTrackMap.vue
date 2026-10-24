<script setup>
import { onBeforeUnmount, onMounted, ref, shallowRef } from 'vue'
import StationFallbackMap from './StationFallbackMap.vue'

defineProps({ source: { type: String, default: 'auto' } })
defineEmits(['failed'])
const segments = shallowRef(window.crticketTrackSegments || null)
const revision = ref(0)
function receiveTrack() {
  segments.value = window.crticketTrackSegments
  revision.value++
}
onMounted(() => window.addEventListener('crticket-track', receiveTrack))
onBeforeUnmount(() => window.removeEventListener('crticket-track', receiveTrack))
</script>

<template>
  <StationFallbackMap
    v-if="segments" :key="revision" :source="source" :segments="segments"
    @failed="$emit('failed')"
  />
</template>
