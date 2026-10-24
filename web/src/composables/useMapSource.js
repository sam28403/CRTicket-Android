import { ref, watch } from 'vue'
import { normalizeMapSource } from '@/utils/maps/mapSources.js'

const source = ref(normalizeMapSource(localStorage.getItem('mapSource')))
watch(source, value => localStorage.setItem('mapSource', normalizeMapSource(value)))

export function useMapSource() {
  return source
}
