import { onScopeDispose, shallowRef } from 'vue'
import { trainQueryRange } from '../utils/trainQueryDate.js'

export function useDailyDelayCache(onExpire) {
  const entries = shallowRef(new Map())
  let day = trainQueryRange().today
  let timer

  function checkDay() {
    const today = trainQueryRange().today
    if (today === day) return
    day = today
    entries.value = new Map()
    onExpire?.()
  }

  function scheduleExpiry() {
    checkDay()
    const midnight = Date.parse(`${day}T00:00:00+08:00`) + 86400000
    timer = setTimeout(scheduleExpiry, Math.max(1, midnight - Date.now()))
  }

  scheduleExpiry()
  onScopeDispose(() => clearTimeout(timer))
  return {
    get(key) {
      checkDay()
      return entries.value.get(key)
    },
    set(key, value) {
      checkDay()
      entries.value = new Map(entries.value).set(key, value)
    },
  }
}
