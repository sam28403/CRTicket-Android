import { onBeforeUnmount, watch } from 'vue'
import { native } from '@/android/platform.js'
import { lcrOperatingDays } from '@/android/railway/lcrOperatingDays.js'
import { trainStopsInTimeZone } from '@/utils/trainTimeZoneDisplay.js'
import { buildTrainRouteData } from '@/utils/maps/trainRouteMap.js'
import { useNextRouting } from './useNextRouting.js'

export function useNativeTrainQuery(state) {
  if (typeof native?.trainQueryResult !== 'function') return false
  const { train, date, loading, error, result, offlineMode, trackingButton } = state
  const nextRouting = useNextRouting(result)
  function request(event) {
    const { action, train: code, date: day, value } = event.detail || {}
    if (action === 'clearHistory') return state.discovery.clearHistory()
    if (action === 'recommendations') return state.discovery.refresh()
    if (action === 'tracking') return trackingButton.value?.toggle()
    if (action === 'delay' && !offlineMode.value) return state.delay.toggle()
    if (action === 'refresh') return state.refresh()
    if (loading.value) return
    if (action === 'route') return state.selectRoute(Number(value))
    if (action === 'timeZone') return state.timeZone.select(value)
    if (action === 'edit') {
      train.value = code
      date.value = day
    } else if (action === 'query') state.query()
    else if (action === 'source') state.switchSource()
    else if (action === 'export') state.exportData()
  }
  window.addEventListener('crticket-train-query-request', request)
  const stop = watch([
    train, date, loading, error, result, offlineMode, state.exporting, nextRouting,
    state.delay.enabled, state.refreshing,
    () => trackingButton.value?.tracking,
    () => trackingButton.value?.busy,
    () => trackingButton.value?.ready,
    state.discovery.history, state.discovery.recommendations,
    state.discovery.recommendationDate, state.discovery.recommendationLoading,
    state.discovery.recommendationError,
    state.location, state.selectedRoute, state.timeZone.selected, state.importedTimetable,
  ], () => {
    const location = state.location()
    if (!/^\/train(?:\?|$)/.test(location)) return
    const timing = state.timing.value
    const beijingTiming = timing?.timeZone === 'vientiane'
      ? { ...timing, stops: trainStopsInTimeZone(timing.stops, 'beijing', timing.timeZone) }
      : timing
    native.trainQueryResult(JSON.stringify({
      train: train.value, date: date.value,
      loading: loading.value || state.importedTimetable.value === null,
      location,
      error: error.value, result: result.value, offline: offlineMode.value,
      imported: offlineMode.value && state.importedTimetable.value === true,
      timing: beijingTiming,
      operatingDays: lcrOperatingDays(timing),
      routeOptions: state.routes.value.length > 1 ? state.routes.value.map(item =>
        `${item.from} → ${item.to}（${item.source}）`) : [],
      selectedRoute: state.selectedRoute.value,
      timeZoneOptions: state.timeZone.options.value,
      selectedTimeZone: state.timeZone.selected.value,
      delayAvailable: state.delayAvailable.value,
      route: result.value ? buildTrainRouteData(result.value.stops, undefined, Boolean(result.value.displayTimeZone)) : null,
      nextRouting: nextRouting.value,
      tracking: Boolean(trackingButton.value?.tracking),
      delayEnabled: !offlineMode.value && state.delay.enabled.value,
      refreshing: state.refreshing.value,
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
