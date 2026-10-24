import { computed, onScopeDispose, shallowRef, watch } from 'vue'
import { native } from '../android/platform.js'
import { callNativeNotification } from '../android/nativeNotifications.js'
import { displayTrainCode } from '../utils/trainDisplay.js'
import { applyStationDelay } from '../utils/stationDelay.js'

async function queryNativeDelay(payload, signal) {
  if (typeof native?.queryStationDelay !== 'function') return { success: false }
  return callNativeNotification('queryStationDelay', [JSON.stringify(payload)], signal, 900000)
}

export function useStationDelay(schedule, queryDelay = queryNativeDelay) {
  const corrected = shallowRef(null)
  let controller
  let generation = 0
  let timer

  function cancel() {
    generation++
    controller?.abort()
    clearTimeout(timer)
  }

  async function refresh() {
    cancel()
    const snapshot = schedule.value
    if (!snapshot?.trains.length) return
    const id = generation
    controller = new AbortController()
    const predictions = new Map()
    // Show the timetable immediately, then merge small batches without replacing the list.
    for (let index = 0; index < snapshot.trains.length; index += 16) {
      if (id !== generation) return
      const trains = snapshot.trains.slice(index, index + 16).map(row => ({
        ...row, queryTrain: displayTrainCode(row.train).split('/')[0],
      }))
      try {
        const response = await queryDelay({ station: snapshot.station, date: snapshot.date, trains }, controller.signal)
        if (id !== generation) return
        if (response.success) {
          for (const row of response.rows || []) predictions.set(row.id, row)
          corrected.value = {
            ...snapshot,
            trains: snapshot.trains.map(row => predictions.has(row.id)
              ? applyStationDelay(row, predictions.get(row.id)) : row),
          }
        }
      } catch (error) {
        if (id !== generation || error.code === 'ERR_CANCELED') return
      }
    }
    timer = setTimeout(refresh, 10 * 60 * 1000)
  }

  watch(schedule, () => {
    cancel()
    corrected.value = null
    refresh()
  }, { flush: 'post' })
  onScopeDispose(cancel)
  return { result: computed(() => corrected.value || schedule.value) }
}
