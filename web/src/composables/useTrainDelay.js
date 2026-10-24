import { computed, nextTick, onScopeDispose, ref, watch } from 'vue'
import { native } from '../android/platform.js'
import { callNativeNotification } from '../android/nativeNotifications.js'

async function queryNativeDelay(snapshot, signal) {
  if (typeof native?.queryTrainDelay !== 'function') throw new Error('Unavailable')
  return callNativeNotification('queryTrainDelay', [JSON.stringify(snapshot)], signal, 900000)
}

export function useTrainDelay(schedule, queryDelay = queryNativeDelay, available = ref(true)) {
  const enabled = ref(true)
  const corrected = ref(null)
  let generation = 0
  let timer
  let controller
  let pending = Promise.resolve()

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
    controller = new AbortController()
    try {
      const response = await queryDelay(snapshot, controller.signal)
      if (id !== generation) return
      if (!response.success || !response.result?.delayAvailable) throw new Error('Unavailable')
      corrected.value = { ...response.result, scheduledStops: snapshot.stops, delayEnabled: true }
      timer = setTimeout(refresh, 10 * 60 * 1000)
    } catch (error) {
      if (id !== generation || error.code === 'ERR_CANCELED') return
      corrected.value = null
      enabled.value = false
    }
  }

  watch([schedule, enabled, available], ([snapshot, value, allowed], [previous]) => {
    cancel()
    if (!allowed) {
      corrected.value = null
      enabled.value = false
      return
    }
    if (snapshot !== previous) {
      corrected.value = null
      if (!value) {
        enabled.value = true
        return
      }
    }
    if (value) pending = refresh()
    else corrected.value = null
  }, { flush: 'post' })
  onScopeDispose(cancel)
  return {
    enabled,
    result: computed(() => available.value && enabled.value && corrected.value ? corrected.value : schedule.value),
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
