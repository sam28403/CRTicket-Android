import { onBeforeUnmount, watch } from 'vue'
import { native } from '@/android/platform.js'
import { buildTrainRouteData } from '@/utils/maps/trainRouteMap.js'

export function useNativeTrainQuery(state) {
  if (typeof native?.trainQueryResult !== 'function') return false
  const { train, date, loading, error, result, offlineMode, trackingButton } = state
  function request(event) {
    const { action, train: code, date: day } = event.detail || {}
    if (action === 'clearHistory') return state.discovery.clearHistory()
    if (action === 'recommendations') return state.discovery.refresh()
    if (action === 'tracking') return trackingButton.value?.toggle()
    if (loading.value) return
    if (action === 'edit') {
      train.value = code
      date.value = day
    } else if (action === 'query') state.query()
    else if (action === 'source') state.switchSource()
    else if (action === 'export') state.exportData()
  }
  window.addEventListener('crticket-train-query-request', request)
  const stop = watch([
    train, date, loading, error, result, offlineMode, state.exporting,
    () => trackingButton.value?.tracking,
    () => trackingButton.value?.busy,
    () => trackingButton.value?.ready,
    state.discovery.history, state.discovery.recommendations,
    state.discovery.recommendationDate, state.discovery.recommendationLoading,
    state.discovery.recommendationError,
    state.location,
  ], () => {
    const location = state.location()
    if (!/^\/train(?:\?|$)/.test(location)) return
    native.trainQueryResult(JSON.stringify({
      train: train.value, date: date.value, loading: loading.value,
      location,
      error: error.value, result: result.value, offline: offlineMode.value,
      route: result.value ? buildTrainRouteData(result.value.stops) : null,
      tracking: Boolean(trackingButton.value?.tracking),
      trackingReady: Boolean(trackingButton.value?.ready && !trackingButton.value?.busy),
      exporting: state.exporting.value,
      history: state.discovery.history.value,
      recommendations: state.discovery.recommendations.value,
      recommendationDate: state.discovery.recommendationDate.value,
      recommendationLoading: state.discovery.recommendationLoading.value,
      recommendationError: state.discovery.recommendationError.value,
    }))
  }, { immediate: true, flush: 'post' })
  onBeforeUnmount(() => {
    stop()
    window.removeEventListener('crticket-train-query-request', request)
  })
  return true
}

export function trainQueryToast(message) {
  if (typeof native?.trainQueryToast === 'function') {
    native.trainQueryToast(message)
    return true
  }
  return false
}
