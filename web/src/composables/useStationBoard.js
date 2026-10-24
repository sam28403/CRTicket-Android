import { computed, onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import api, { timetableTimeout } from '@/api.js'
import { stationQueryName } from '@/utils/stationName.js'
import { stationClock } from '@/utils/stationBoard.js'
import { stationLocation } from '@/utils/stationNavigation.js'
import { useStationDiscovery } from './useStationDiscovery.js'
import { useStationDelay } from './useStationDelay.js'

export function useStationBoard() {
  const route = useRoute()
  const router = useRouter()
  const station = ref('')
  const now = ref(Date.now())
  const clock = computed(() => stationClock(now.value))
  const discovery = useStationDiscovery(clock)
  const loading = ref(false)
  const schedule = shallowRef(null)
  const { result } = useStationDelay(schedule)
  const error = ref('')
  let controller
  let requestId = 0
  const snapshots = new Map()
  const timer = setInterval(() => { now.value = Date.now() }, 1000)

  async function query(name = station.value, preserve = false) {
    const selected = stationQueryName(name)
    if (!selected) {
      error.value = '请输入车站名称（不超过 30 个字）'
      return
    }
    if (route.query.station !== selected) {
      await router.replace(stationLocation(selected))
      return
    }
    controller?.abort()
    controller = new AbortController()
    const id = ++requestId
    now.value = Date.now()
    const date = clock.value.date
    station.value = selected
    loading.value = true
    if (!preserve) schedule.value = null
    error.value = ''
    try {
      const { data } = await api.get('/station', {
        params: { station: selected, date },
        timeout: timetableTimeout,
        signal: controller.signal,
      })
      if (!data.success) throw new Error(data.message || '查询失败')
      if (id !== requestId) return
      // 防止挂起标签页或长请求在午夜后仍展示昨天的数据。
      now.value = Date.now()
      if (data.date !== clock.value.date) {
        query(selected)
        return
      }
      schedule.value = data
      discovery.record(selected)
      snapshots.delete(selected)
      snapshots.set(selected, data)
      if (snapshots.size > 12) snapshots.delete(snapshots.keys().next().value)
    } catch (err) {
      if (id === requestId && err.code !== 'ERR_CANCELED') {
        error.value = err.response?.data?.message || (
          err.code === 'ECONNABORTED'
            ? '查询超时，请稍后重试'
            : err.message === '查询失败'
              ? err.message
              : '查询失败，请检查网络后重试'
        )
      }
    } finally {
      if (id === requestId) loading.value = false
    }
  }

  watch(() => [route.path, route.query.station], ([path, name]) => {
    if (path !== '/station') return
    const selected = typeof name === 'string' ? stationLocation(name).query.station : ''
    if (result.value?.station === selected && result.value.date === clock.value.date) return
    controller?.abort()
    requestId++
    loading.value = false
    schedule.value = null
    error.value = ''
    station.value = selected
    const previous = snapshots.get(selected)
    if (previous?.date === clock.value.date) {
      schedule.value = previous
      return
    }
    if (station.value) query()
  }, { immediate: true })

  watch(() => clock.value.date, () => {
    if (route.path !== '/station') return
    const selected = result.value?.station || station.value
    schedule.value = null
    controller?.abort()
    requestId++
    loading.value = false
    if (stationQueryName(selected)) query(selected)
  })

  onBeforeUnmount(() => {
    clearInterval(timer)
    requestId++
    controller?.abort()
  })

  const refresh = () => query(result.value?.station || station.value, true)
  return { station, clock, loading, result, error, query, refresh, discovery }
}
