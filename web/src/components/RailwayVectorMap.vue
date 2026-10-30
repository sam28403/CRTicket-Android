<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { Map, Marker, NavigationControl, AttributionControl, setWorkerUrl } from 'maplibre-gl'
import workerUrl from 'maplibre-gl/dist/maplibre-gl-worker.mjs?worker&url'
import 'maplibre-gl/dist/maplibre-gl.css'
import { stationMapLocation } from '@/utils/maps/stationLocationMap.js'
import { validTrackSegments } from '@/utils/maps/recordedTrack.js'
import { railwayVectorStyle } from '@/utils/maps/railwayVectorStyle.js'
import { railwayProviders, railwayMapResource } from '@/utils/maps/railwayMapResource.js'
import { addVectorTrack } from '@/utils/maps/vectorTrack.js'

setWorkerUrl(workerUrl)

const props = defineProps({
  station: { type: String, default: '' },
  source: { type: String, default: 'openrailway' },
  segments: { type: Array, default: null },
})
const emit = defineEmits(['failed'])
const container = ref(null)
const controller = new AbortController()
let map
let timer
let observer
let reported = false
function fail(error) {
  if (controller.signal.aborted) return
  const message = error?.error?.message || error?.message
  if (message) console.error(`RailwayMap: ${message}`)
  if (reported) return
  reported = true
  emit('failed')
}

onMounted(async () => {
  const segments = validTrackSegments(props.segments)
  const points = segments.flat()
  const location = props.segments ? points[0] : stationMapLocation(props.station)
  if (!location) return fail()
  timer = setTimeout(fail, 20000)
  try {
    const provider = railwayProviders[props.source]
    const response = await fetch(railwayMapResource(provider.style, props.source), { signal: controller.signal })
    if (!response.ok) throw new Error('Map style unavailable')
    const raw = await response.json()
    const style = railwayVectorStyle(raw)
    if (controller.signal.aborted) return
    map = new Map({
      container: container.value, center: [location[1], location[0]], zoom: provider.initialZoom,
      attributionControl: false, renderWorldCopies: false,
      transformRequest: url => ({ url: railwayMapResource(url, props.source) }),
    })
    map.addControl(new NavigationControl({ showCompass: false }))
    map.addControl(new AttributionControl({
      compact: false,
      customAttribution: provider.attribution,
    }))
    map.on('error', fail)
    map.on('idle', () => {
      const features = map.queryRenderedFeatures().filter(feature => feature.source !== 'recorded-track')
      container.value.dataset.features = String(features.filter(feature =>
        /rail/i.test(feature.sourceLayer || '')).length)

    })
    map.setStyle(style, {
      validate: false,
      globalState: {
        date: new Date().getFullYear(), theme: 'light', openHistoricalMap: false,
        showAbandonedInfrastructure: false, showRazedInfrastructure: false,
      },
    })
    map.once('load', () => {
      clearTimeout(timer)
      container.value.dataset.ready = 'true'
      if (props.segments) {
        addVectorTrack(map, segments)
        const bounds = points.reduce((box, [lat, lon]) => [
          [Math.min(box[0][0], lon), Math.min(box[0][1], lat)],
          [Math.max(box[1][0], lon), Math.max(box[1][1], lat)],
        ], [[location[1], location[0]], [location[1], location[0]]])
        map.fitBounds(bounds, { padding: 32, maxZoom: provider.initialZoom, animate: false })
        new Marker({ color: '#38bdf8' }).setLngLat([location[1], location[0]]).addTo(map)
      }
      const last = props.segments ? points.at(-1) : location
      new Marker({ color: '#e53935' }).setLngLat([last[1], last[0]]).addTo(map)
    })
    observer = new ResizeObserver(() => map?.resize())
    observer.observe(container.value)
  } catch (error) { fail(error) }
})

onBeforeUnmount(() => {
  controller.abort()
  clearTimeout(timer)
  observer?.disconnect()
  map?.remove()
  map = null
})
</script>

<template>
  <div ref="container" class="railway-vector-map" />
</template>

<style scoped>
.railway-vector-map { width: 100%; height: 100%; min-height: 240px; }
</style>
