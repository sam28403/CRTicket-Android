import { onBeforeUnmount, watch } from 'vue'
import { native } from '@/android/platform.js'

// Transfer a snapshot only when data changes; scrolling stays entirely in Compose.
export function useNativeTrainList(date, loading, error, result, query) {
  const enabled = typeof native?.trainListResult === 'function'
  if (!enabled) return false

  function request(event) {
    const selected = event.detail?.date
    if (selected && selected !== date.value) date.value = selected
    else if (!loading.value) query()
  }
  window.addEventListener('crticket-train-list-request', request)
  const stop = watch([date, loading, error, result], () => {
    native.trainListResult(JSON.stringify({
      date: date.value,
      loading: loading.value,
      error: error.value,
      result: result.value,
    }))
  }, { immediate: true, flush: 'post' })
  onBeforeUnmount(() => {
    stop()
    window.removeEventListener('crticket-train-list-request', request)
  })
  return true
}
