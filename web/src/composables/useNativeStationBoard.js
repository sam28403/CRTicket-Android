import { onBeforeUnmount, watch } from 'vue'
import { native } from '@/android/platform.js'
import { queryStationSearch } from './useTicketShared.js'
import { boardTabs, stationBoardRows } from '@/utils/stationBoard.js'
import { stationTrainLocation } from '@/utils/stationNavigation.js'
import { displayTrainCode } from '@/utils/trainDisplay.js'

export function useNativeStationBoard(board) {
  if (typeof native?.stationBoardResult !== 'function') return false
  const { station, loading, error, result, query, discovery } = board
  function request(event) {
    const { action, station: name } = event.detail || {}
    if (action === 'suggest') {
      queryStationSearch(name, suggestions => native.stationBoardResult(JSON.stringify({
        kind: 'suggestions', query: name, suggestions: suggestions.slice(0, 8),
      })))
    } else if (action === 'clearHistory') discovery.clearHistory()
    else if (action === 'recommendations') discovery.refresh()
    else if (!loading.value) query(name)
  }
  window.addEventListener('crticket-station-request', request)
  const stopDiscovery = watch([
    discovery.history, discovery.busy, discovery.recommendations, discovery.loading, discovery.error,
  ], () => {
    native.stationBoardResult(JSON.stringify({
      kind: 'discovery', history: discovery.history.value, busy: discovery.busy.value,
      recommendations: discovery.recommendations.value,
      loading: discovery.loading.value, error: discovery.error.value,
    }))
  }, { immediate: true, flush: 'post' })
  const stop = watch([station, loading, error, result], () => {
    const data = result.value
    const tabs = data ? Object.fromEntries(boardTabs.map(tab => [tab.name,
      stationBoardRows(data.trains, tab.name).map(row => {
        const location = stationTrainLocation(row, data.source, data.date)
        return {
          ...row,
          displayTrain: displayTrainCode(row.train),
          route: `${location.path}?${new URLSearchParams(location.query)}`,
        }
      }),
    ])) : null
    native.stationBoardResult(JSON.stringify({
      kind: 'board', station: station.value, loading: loading.value,
      error: error.value, date: data?.date, resultStation: data?.station, tabs,
    }))
  }, { immediate: true, flush: 'post' })
  onBeforeUnmount(() => {
    stop()
    stopDiscovery()
    window.removeEventListener('crticket-station-request', request)
  })
  return true
}
