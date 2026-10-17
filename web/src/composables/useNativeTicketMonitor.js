import { onBeforeUnmount, watch } from 'vue'
import { native } from '@/android/platform.js'
import { queryStationSearch } from './useTicketShared.js'

const fields = [
  'from', 'to', 'date', 'interval', 'monitoring', 'loading', 'queriedAt',
  'lastMonitorAt', 'error', 'restoring', 'filtered', 'ticketCount', 'departureStations',
  'arrivalStations', 'trainFilters', 'seatFilters', 'timeRange',
  'highSpeedOnly', 'ordinaryOnly', 'onlyAvailable',
  'selectedDepartureStations', 'selectedArrivalStations',
]

export function useNativeTicketMonitor(monitor) {
  if (typeof native?.ticketMonitorPageResult !== 'function') return false
  const publish = () => native.ticketMonitorPageResult(JSON.stringify({
    ...Object.fromEntries(fields.map(key => [key, monitor[key].value])),
    columns: monitor.columns,
  }))
  function request(event) {
    const { action, key, value } = event.detail || {}
    if (action === 'sync') return publish()
    if (action === 'suggest') {
      queryStationSearch(value, suggestions => native.ticketMonitorPageResult(JSON.stringify({
        kind: 'suggestions', key, query: value, suggestions,
      })))
    } else if (action === 'edit' && fields.includes(key) && monitor[key]?.__v_isRef) {
      monitor[key].value = value
      if (key === 'highSpeedOnly') monitor.selectTrainType('highSpeed', value)
      if (key === 'ordinaryOnly') monitor.selectTrainType('ordinary', value)
    } else if (action === 'swap') monitor.swapStations()
    else if (action === 'query') void monitor.query()
    else if (action === 'refresh') void monitor.refresh()
    else if (action === 'start') void monitor.query(true)
    else if (action === 'stop') monitor.stop()
    else if (action === 'reset') void monitor.resetAll()
  }
  window.addEventListener('crticket-monitor-page-request', request)
  const stop = watch(fields.map(key => monitor[key]), publish, { immediate: true, deep: true })
  onBeforeUnmount(() => {
    stop()
    window.removeEventListener('crticket-monitor-page-request', request)
  })
  return true
}
