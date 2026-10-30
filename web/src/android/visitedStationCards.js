import { loadLltDatabase } from './railway/lltLoader.js'
import { listStationSummaries } from './railway/stationDiscovery.js'
import { onBeforeUnmount } from 'vue'
import { native } from './platform.js'
import { stationClock } from '../utils/stationBoard.js'
import { stationSummaryNow } from '../utils/stationDiscovery.js'
import { stationQueryName } from '../utils/stationName.js'
import { parseStations } from '../utils/stations.js'
import { stationBureau } from '../utils/stationBureau.js'

export function useVisitedStationCards() {
  if (typeof native?.stationBoardResult !== 'function') return
  const controller = new AbortController()
  let cachedDate = ''
  let cachedRows
  async function rowsFor(date) {
    if (cachedDate !== date || !cachedRows) {
      cachedDate = date
      cachedRows = import('../api.js').then(({ default: api, timetableTimeout }) => api.get(
        '/station/discovery', {
          params: { date }, timeout: timetableTimeout, signal: controller.signal,
        },
      )).then(({ data }) => {
        if (!data.success || data.date !== date) throw new Error('Invalid station date')
        return new Map((data.stations || []).map(row => [stationQueryName(row.station), row]))
      }).catch(error => {
        if (cachedDate === date) cachedRows = null
        throw error
      })
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
      const clock = stationClock(Date.now())
      const rows = await rowsFor(clock.date)
      if (saleCatalog) {
        native.stationBoardResult(JSON.stringify({
          kind: 'saleCatalog', date: clock.date,
          names,
          stations: [...rows.values()].map(row => ({ station: row.station, total: row.total })),
        }))
        return
      }
      const now = stationClock(Date.now())
      if (now.date !== clock.date) return request(event)
      native.stationBoardResult(JSON.stringify({
        kind: 'visits', requestId,
        stations: stations.map(station => {
          // The discovery list omits stations with no services on this date.
          const row = rows.get(stationQueryName(station)) || {
            station, total: 0, origin: 0, terminal: 0,
            completionTimes: [], passingWindows: [],
          }
          return { ...stationSummaryNow(row, now.time), station }
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
