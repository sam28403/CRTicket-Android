<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { circleMarker, latLngBounds, map as createMap, polyline, tileLayer } from 'leaflet'
import 'leaflet/dist/leaflet.css'
import { stationMapLocation } from '@/utils/maps/stationLocationMap.js'
import { rasterCandidates } from '@/utils/maps/mapSources.js'
import { watchTileLayer } from '@/utils/maps/tileLayerHealth.js'
import { transportBaseLayer } from '@/utils/maps/transportBaseLayer.js'
import { validTrackSegments } from '@/utils/maps/recordedTrack.js'
import { coloredTrackSections, sectionSpeed, speedColor } from '@/utils/maps/trackSpeed.js'

const props = defineProps({
  station: { type: String, default: '' },
  source: { type: String, default: 'auto' },
  segments: { type: Array, default: null },
})
const emit = defineEmits(['failed'])
const container = ref(null)
let map
let observer
let disposeHealth
let layer
let attempt = 0

function loadSource() {
  disposeHealth?.()
  if (layer) map.removeLayer(layer)
  const source = rasterCandidates(props.source)[attempt++]
  if (!source) {
    emit('failed')
    return
  }
  layer = tileLayer(source.url, {
    maxZoom: source.maxZoom,
    attribution: source.attribution,
  })
  disposeHealth = watchTileLayer(layer, () => {
    queueMicrotask(() => { if (map) loadSource() })
  })
  layer.addTo(map)
}

onMounted(() => {
  const segments = validTrackSegments(props.segments)
  const points = segments.flat()
  const location = props.segments ? points[0] : stationMapLocation(props.station)
  if (!location) {
    emit('failed')
    return
  }
  map = createMap(container.value, {
    zoomAnimation: false, preferCanvas: Boolean(props.segments),
  }).setView(location, 16)
  map.attributionControl.setPrefix(false)
  if (props.segments) {
    polyline(segments.map(segment => segment.map(point => point.slice(0, 2))), {
      color: '#f8fafc', weight: 9, opacity: 0.9,
    }).addTo(map)
    const colors = new Map()
    coloredTrackSections(segments).forEach(section => {
      if (!colors.has(section.color)) colors.set(section.color, [])
      colors.get(section.color).push(section.points)
    })
    colors.forEach((paths, color) => polyline(paths, { color, weight: 5, opacity: 1 }).addTo(map))
    circleMarker(points[0], {
      radius: 7, color: '#fff',
      fillColor: speedColor(segments[0].length > 1 ? sectionSpeed(...segments[0].slice(0, 2)) : null),
      fillOpacity: 1,
    }).addTo(map)
    if (points.length > 1) map.fitBounds(latLngBounds(points), { padding: [32, 32], maxZoom: 16 })
    else map.setView(points[0], 13)
  }
  circleMarker(props.segments ? points.at(-1) : location, {
    radius: 9,
    color: '#fff',
    weight: 3,
    fillColor: props.segments
      ? speedColor(segments.at(-1).length > 1 ? sectionSpeed(...segments.at(-1).slice(-2)) : null)
      : '#e53935',
    fillOpacity: 1,
    interactive: false,
  }).addTo(map)

  if (props.source === 'transport') {
    layer = transportBaseLayer(() => emit('failed')).addTo(map)
  } else loadSource()
  observer = new ResizeObserver(() => map.invalidateSize({ animate: false }))
  observer.observe(container.value)
})

onBeforeUnmount(() => {
  observer?.disconnect()
  disposeHealth?.()
  map?.remove()
  map = null
})
</script>

<template>
  <div id="map" ref="container" class="fallback-map" />
</template>

<style scoped>
.fallback-map {
  width: 100%;
  height: 100%;
}
</style>
