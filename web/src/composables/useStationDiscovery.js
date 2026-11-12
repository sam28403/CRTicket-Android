import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import api, { timetableTimeout } from '@/api.js'
import {
  addStationHistory, busyStationPicks, dailyStationPicks, stationDiscoveryDates, stationSummaryNow,
} from '@/utils/stationDiscovery.js'
import { stationClock } from '@/utils/stationTimeZone.js'
import { stationLocation } from '@/utils/stationNavigation.js'

const historyKey = 'crticket-station-query-history'

export function useStationDiscovery(now) {
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
  const minute = computed(() => Math.floor(now.value / 60000) * 60000)
  const dates = computed(() => stationDiscoveryDates(minute.value))
  const dateKey = computed(() => `${dates.value.date}:${dates.value.vientianeDate}`)
  let loadedKey = ''
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
    const requestDates = dates.value
    const key = dateKey.value
    if (route.path !== '/station' || loadedKey === key) return
    controller?.abort()
    controller = new AbortController()
    const id = ++generation
    stations.value = []
    loading.value = true
    error.value = ''
    try {
      const { data } = await api.get('/station/discovery', {
        params: requestDates, timeout: timetableTimeout, signal: controller.signal,
      })
      if (!data.success || data.date !== requestDates.date) throw new Error('读取失败')
      if (id !== generation || dateKey.value !== key) return
      stations.value = data.stations || []
      loadedKey = key
    } catch (err) {
      if (id === generation && err.code !== 'ERR_CANCELED') error.value = '车站推荐暂时无法加载'
    } finally {
      if (id === generation) loading.value = false
    }
  }
  function summarize(row) {
    return stationSummaryNow(row, stationClock(minute.value, row.station).time)
  }
  const busiest = computed(() => busyStationPicks(stations.value))
  const busy = computed(() => busiest.value.map(summarize))
  const picks = computed(() => dailyStationPicks(stations.value, dates.value.date))
  const recommendations = computed(() => picks.value.map(summarize))
  watch([() => route.path, dateKey], refresh, { immediate: true })
  function invalidateTimetable() {
    loadedKey = ''
    refresh()
  }
  window.addEventListener('crticket-timetable-replaced', invalidateTimetable)
  onBeforeUnmount(() => {
    window.removeEventListener('crticket-backup-restored', restoreHistory)
    window.removeEventListener('crticket-timetable-replaced', invalidateTimetable)
    generation++
    controller?.abort()
  })
  return { history, busy, recommendations, loading, error, record, clearHistory, open, refresh }
}
