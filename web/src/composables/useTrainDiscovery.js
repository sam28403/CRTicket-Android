import { onBeforeUnmount, ref, watch } from 'vue'
import api, { timetableTimeout } from '@/api.js'
import { trainQueryRange } from '@/utils/trainQueryDate.js'
import { addTrainHistory, dailyTrainRecommendations } from '@/utils/trainDiscovery.js'

const historyKey = 'crticket-train-query-history'

export function useTrainDiscovery(route, router) {
  const history = ref([])
  function restoreHistory() {
    history.value = []
    try {
      const saved = JSON.parse(localStorage.getItem(historyKey) || '[]')
      if (Array.isArray(saved)) {
        history.value = saved.slice().reverse().reduce((items, code) =>
          typeof code === 'string' ? addTrainHistory(items, code) : items, [])
      }
    } catch { /* Storage may be unavailable. Queries still work. */ }
  }
  restoreHistory()
  window.addEventListener('crticket-backup-restored', restoreHistory)
  const recommendations = ref([])
  const recommendationDate = ref('')
  const recommendationLoading = ref(false)
  const recommendationError = ref('')
  let controller
  let timer
  let loadedDate = ''

  function record(code) {
    history.value = addTrainHistory(history.value, code)
    try { localStorage.setItem(historyKey, JSON.stringify(history.value)) } catch { /* Optional storage. */ }
  }

  function clearHistory() {
    history.value = []
    try { localStorage.removeItem(historyKey) } catch { /* Optional storage. */ }
  }

  function open(code) {
    router.push({ path: '/train', query: { train: code, date: trainQueryRange().today } })
  }

  async function refresh() {
    const today = trainQueryRange().today
    if (route.path !== '/train' || loadedDate === today || recommendationLoading.value) return
    controller = new AbortController()
    recommendationDate.value = today
    recommendations.value = []
    recommendationLoading.value = true
    recommendationError.value = ''
    try {
      const { data } = await api.get('/llt-trains', {
        params: { date: today }, timeout: timetableTimeout, signal: controller.signal,
      })
      if (!data.success) throw new Error('读取失败')
      if (today !== trainQueryRange().today) return
      recommendations.value = dailyTrainRecommendations(data.trains || [], today)
      loadedDate = today
    } catch (err) {
      if (err.code !== 'ERR_CANCELED') recommendationError.value = '推荐暂时无法加载，请重试'
    } finally {
      recommendationLoading.value = false
      if (today !== trainQueryRange().today) schedule()
    }
  }

  function schedule() {
    clearTimeout(timer)
    const today = trainQueryRange().today
    if (recommendationDate.value !== today) {
      recommendations.value = []
      recommendationDate.value = today
    }
    refresh()
    const midnight = Date.parse(`${today}T00:00:00+08:00`) + 86400000
    timer = setTimeout(schedule, Math.max(1, midnight - Date.now()))
  }
  watch(() => route.path, schedule, { immediate: true })
  document.addEventListener('visibilitychange', schedule)
  onBeforeUnmount(() => {
    window.removeEventListener('crticket-backup-restored', restoreHistory)
    clearTimeout(timer)
    controller?.abort()
    document.removeEventListener('visibilitychange', schedule)
  })
  return {
    history, recommendations, recommendationDate, recommendationLoading,
    recommendationError, record, clearHistory, open, refresh,
  }
}
