import { ref, watch } from 'vue'
import { normalizeMapSource } from '../utils/maps/mapSources.js'

const source = ref(normalizeMapSource(localStorage.getItem('mapSource')))
watch(source, value => localStorage.setItem('mapSource', normalizeMapSource(value)))
const reloadSource = () => { source.value = normalizeMapSource(localStorage.getItem('mapSource')) }
window.addEventListener('crticket-backup-restored', reloadSource)
window.addEventListener('storage', event => {
  if (event.key === 'mapSource' || event.key === null) reloadSource()
})

export function useMapSource() {
  return source
}
