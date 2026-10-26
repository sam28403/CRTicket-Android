import { computed, nextTick, onScopeDispose, ref, watch } from 'vue'
import { native } from '../android/platform.js'
import { callNativeNotification } from '../android/nativeNotifications.js'
import { useDailyDelayCache } from './useDailyDelayCache.js'
import { applyTrainDelay, mergeDelayOffsets, trainDelayCacheKey } from '../utils/delayCache.js'
import { trainQueryRange } from '../utils/trainQueryDate.js'

async function queryNativeDelay(snapshot, signal) {
  if (typeof native?.queryTrainDelay !== 'function') throw new Error('Unavailable')
  return callNativeNotification('queryTrainDelay', [JSON.stringify(snapshot)], signal, 900000)
}

export function useTrainDelay(schedule, queryDelay = queryNativeDelay, available = ref(true)) {
  const enabled = ref(true)
  let generation = 0
  let timer
  let controller
  let pending = Promise.resolve()
  const cache = useDailyDelayCache(() => { pending = refresh() })

  function cancel() {
    generation++
    controller?.abort()
    clearTimeout(timer)
  }

  async function refresh() {
    if (!available.value || !schedule.value?.stops.length || !enabled.value) return
    cancel()
    const id = generation
    const snapshot = schedule.value
    const key = trainDelayCacheKey(snapshot)
    const day = trainQueryRange().today
    controller = new AbortController()
    try {
      const response = await queryDelay(snapshot, controller.signal)
      if (id !== generation || day !== trainQueryRange().today) return
      if (!response.success || !response.result?.delayAvailable) throw new Error('Unavailable')
      const previous = cache.get(key) || []
      const offsets = snapshot.stops.map((row, index) => mergeDelayOffsets(
        previous[index], response.result.stops?.[index],
      ))
      if (!offsets.some(row => Object.keys(row).length)) throw new Error('Unavailable')
      cache.set(key, offsets)
      timer = setTimeout(refresh, 10 * 60 * 1000)
    } catch (error) {
      if (id !== generation || day !== trainQueryRange().today || error?.code === 'ERR_CANCELED') return
      if (cache.get(key)) timer = setTimeout(refresh, 10 * 60 * 1000)
      else enabled.value = false
    }
  }

  watch([schedule, enabled, available], ([snapshot, value, allowed], [previous]) => {
    cancel()
    if (!allowed) {
      enabled.value = false
      return
    }
    if (snapshot !== previous) {
      if (!value) {
        enabled.value = true
        return
      }
    }
    if (value) pending = refresh()
  }, { flush: 'post' })
  onScopeDispose(cancel)
  return {
    enabled,
    result: computed(() => {
      const snapshot = schedule.value
      if (!available.value || !enabled.value || !snapshot?.stops.length) return snapshot
      const offsets = cache.get(trainDelayCacheKey(snapshot))
      return offsets ? applyTrainDelay(snapshot, offsets) : snapshot
    }),
    toggle: () => { if (available.value) enabled.value = !enabled.value },
    async refresh() {
      if (!available.value) return
      if (!enabled.value) {
        enabled.value = true
        await nextTick()
      } else pending = refresh()
      await pending
    },
  }
}
