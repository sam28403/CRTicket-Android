import { quizLiveSources } from './railway/quizLiveSources.js'
import { loadLltDatabase } from './railway/lltLoader.js'
import { listStationSummaries } from './railway/stationDiscovery.js'
import { onBeforeUnmount } from 'vue'
import { native } from './platform.js'
import { stationClock } from '../utils/stationBoard.js'
import { stationDiscoveryDates, stationSummaryNow } from '../utils/stationDiscovery.js'
import { stationQueryName } from '../utils/stationName.js'
import { parseStations } from '../utils/stations.js'
import { stationBureau } from '../utils/stationBureau.js'

export function useVisitedStationCards() {
  if (typeof native?.stationBoardResult !== 'function') return
  const controller = new AbortController()
  let cachedKey = ''
  let cachedRows
  async function rowsFor(dates) {
    const { date, vientianeDate } = dates
    const key = `${date}:${vientianeDate}:${loadLltDatabase.revision}`
    if (cachedKey !== key || !cachedRows) {
      cachedKey = key
      const task = import('../api.js').then(({ default: api, timetableTimeout }) => api.get(
        '/station/discovery', {
          params: dates, timeout: timetableTimeout, signal: controller.signal,
        },
      )).then(({ data }) => {
        if (!data.success || data.date !== date) throw new Error('Invalid station date')
        return new Map((data.stations || []).map(row => [stationQueryName(row.station), row]))
      })
      cachedRows = task
      task.catch(() => { if (cachedRows === task) cachedRows = null })
    }
    return cachedRows
  }
  async function request(event) {
    const { stations, requestId, saleCatalog, quizDate } = event.detail || {}
    if (quizDate) {
      try {
        if (quizDate !== stationClock().date) throw new Error('Invalid quiz date')
        const database = await loadLltDatabase.fresh()
        if (quizDate !== stationClock().date) throw new Error('Date changed')
        native.stationBoardResult(JSON.stringify({
          kind: 'quizCounts', requestId, date: quizDate,
          stations: listStationSummaries(database, quizDate),
          sources: quizLiveSources(database),
        }))
      } catch {
        native.stationBoardResult(JSON.stringify({ kind: 'quizCounts', requestId, error: true }))
      }
      return
    }
    if ((!saleCatalog && !Array.isArray(stations)) || !Number.isInteger(requestId)) return
    const names = saleCatalog ? parseStations().map(row => ({
      ...row, bureau: stationBureau(row.name),
    })) : undefined
    if (saleCatalog) native.stationBoardResult(JSON.stringify({
      kind: 'saleCatalog', names, loading: true,
    }))
    try {
      const dates = stationDiscoveryDates()
      const rows = await rowsFor(dates)
      const now = Date.now()
      const currentDates = stationDiscoveryDates(now)
      if (dates.date !== currentDates.date || dates.vientianeDate !== currentDates.vientianeDate) {
        return request(event)
      }
      if (saleCatalog) {
        native.stationBoardResult(JSON.stringify({
          kind: 'saleCatalog', date: dates.date,
          names,
          stations: [...rows.values()].map(row => ({ station: row.station, total: row.total })),
        }))
        return
      }
      native.stationBoardResult(JSON.stringify({
        kind: 'visits', requestId,
        stations: stations.map(station => {
          // The discovery list omits stations with no services on this date.
          const row = rows.get(stationQueryName(station)) || {
            station, total: 0, origin: 0, terminal: 0,
            completionTimes: [], passingWindows: [],
          }
          return { ...stationSummaryNow(row, stationClock(now, station).time), station }
        }),
      }))
    } catch (error) {
      if (error.code !== 'ERR_CANCELED') native.stationBoardResult(JSON.stringify({
        kind: saleCatalog ? 'saleCatalog' : 'visits', names, requestId, error: true, stations: [],
      }))
    }
  }
  window.addEventListener('crticket-visited-stations-request', request)
  onBeforeUnmount(() => {
    controller.abort()
    window.removeEventListener('crticket-visited-stations-request', request)
  })
}
