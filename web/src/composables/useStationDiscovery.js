import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import api, { timetableTimeout } from '@/api.js'
import {
  addStationHistory, busyStationPicks, dailyStationPicks, stationSummaryNow,
} from '@/utils/stationDiscovery.js'
import { stationLocation } from '@/utils/stationNavigation.js'

const historyKey = 'crticket-station-query-history'

export function useStationDiscovery(clock) {
  const route = useRoute()
  const router = useRouter()
  const history = ref([])
  function restoreHistory() {
    history.value = []
    try {
      const saved = JSON.parse(localStorage.getItem(historyKey) || '[]')
      if (Array.isArray(saved)) {
        history.value = saved.slice().reverse().reduce((items, name) =>
          addStationHistory(items, name), [])
      }
    } catch { /* History is optional. */ }
  }
  restoreHistory()
  window.addEventListener('crticket-backup-restored', restoreHistory)
  const stations = ref([])
  const loading = ref(false)
  const error = ref('')
  let loadedDate = ''
  let controller
  let generation = 0

  function record(name) {
    history.value = addStationHistory(history.value, name)
    try { localStorage.setItem(historyKey, JSON.stringify(history.value)) } catch { /* Optional. */ }
  }
  function clearHistory() {
    history.value = []
    try { localStorage.removeItem(historyKey) } catch { /* Optional. */ }
  }
  function open(name) { router.push(stationLocation(name)) }

  async function refresh() {
    const date = clock.value.date
    if (route.path !== '/station' || loadedDate === date) return
    controller?.abort()
    controller = new AbortController()
    const id = ++generation
    stations.value = []
    loading.value = true
    error.value = ''
    try {
      const { data } = await api.get('/station/discovery', {
        params: { date }, timeout: timetableTimeout, signal: controller.signal,
      })
      if (!data.success || data.date !== date) throw new Error('读取失败')
      if (id !== generation || clock.value.date !== date) return
      stations.value = data.stations || []
      loadedDate = date
    } catch (err) {
      if (id === generation && err.code !== 'ERR_CANCELED') error.value = '车站推荐暂时无法加载'
    } finally {
      if (id === generation) loading.value = false
    }
  }
  // Timetable events have minute precision; avoid resending unchanged cards every second.
  const minute = computed(() => clock.value.time.slice(0, 5))
  const busiest = computed(() => busyStationPicks(stations.value))
  const busy = computed(() => busiest.value.map(row => stationSummaryNow(row, minute.value)))
  const picks = computed(() => dailyStationPicks(stations.value, clock.value.date))
  const recommendations = computed(() => picks.value.map(row => stationSummaryNow(row, minute.value)))
  watch(() => [route.path, clock.value.date], refresh, { immediate: true })
  onBeforeUnmount(() => {
    window.removeEventListener('crticket-backup-restored', restoreHistory)
    generation++
    controller?.abort()
  })
  return { history, busy, recommendations, loading, error, record, clearHistory, open, refresh }
}
