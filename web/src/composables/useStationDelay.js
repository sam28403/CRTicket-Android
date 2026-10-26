import { computed, onScopeDispose, watch } from 'vue'
import { native } from '../android/platform.js'
import { callNativeNotification } from '../android/nativeNotifications.js'
import { displayTrainCode } from '../utils/trainDisplay.js'
import { applyStationDelay } from '../utils/stationDelay.js'
import { useDailyDelayCache } from './useDailyDelayCache.js'
import { mergeDelayOffsets, stationDelayCacheKey, stationDelayRowKey } from '../utils/delayCache.js'
import { trainQueryRange } from '../utils/trainQueryDate.js'

async function queryNativeDelay(payload, signal) {
  if (typeof native?.queryStationDelay !== 'function') return { success: false }
  return callNativeNotification('queryStationDelay', [JSON.stringify(payload)], signal, 900000)
}

export function useStationDelay(schedule, queryDelay = queryNativeDelay) {
  let controller
  let generation = 0
  let timer
  const cache = useDailyDelayCache(cancel)

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
    const day = trainQueryRange().today
    const key = stationDelayCacheKey(snapshot)
    controller = new AbortController()
    const predictions = new Map(cache.get(key))
    // Show the timetable immediately, then merge small batches without replacing the list.
    for (let index = 0; index < snapshot.trains.length; index += 16) {
      if (id !== generation) return
      const trains = snapshot.trains.slice(index, index + 16).map(row => ({
        ...row, queryTrain: displayTrainCode(row.train).split('/')[0],
      }))
      try {
        const response = await queryDelay({ station: snapshot.station, date: snapshot.date, trains }, controller.signal)
        if (id !== generation || day !== trainQueryRange().today) return
        if (response.success) {
          for (const prediction of response.rows || []) {
            const row = trains.find(row => row.id === prediction.id)
            if (!row) continue
            const rowKey = stationDelayRowKey(row)
            const offsets = mergeDelayOffsets(predictions.get(rowKey), prediction)
            if (Object.keys(offsets).length) predictions.set(rowKey, offsets)
          }
          cache.set(key, new Map(predictions))
        }
      } catch (error) {
        if (id !== generation || day !== trainQueryRange().today || error?.code === 'ERR_CANCELED') return
      }
    }
    timer = setTimeout(refresh, 10 * 60 * 1000)
  }

  watch(schedule, () => {
    cancel()
    refresh()
  }, { flush: 'post' })
  onScopeDispose(cancel)
  return { result: computed(() => {
    const snapshot = schedule.value
    if (!snapshot) return snapshot
    const predictions = cache.get(stationDelayCacheKey(snapshot))
    if (!predictions?.size) return snapshot
    return {
      ...snapshot,
      trains: snapshot.trains.map(row => applyStationDelay(
        row, predictions.get(stationDelayRowKey(row)) || {},
      )),
    }
  }) }
}
