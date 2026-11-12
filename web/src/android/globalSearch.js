import { loadLltDatabase } from './railway/lltLoader.js'
import { queryLltRouting } from './railway/lltRouting.js'
import api, { timetableTimeout } from '../api.js'
import { native } from './platform.js'
import { parseStations } from '../utils/stations.js'
import { stationDiscoveryDates, stationSummaryNow } from '../utils/stationDiscovery.js'
import { stationClock } from '../utils/stationTimeZone.js'
import { filterSearchTrains, filterSearchStations } from '../utils/globalSearch.js'
import { showNonRunningTrains } from './timetableVisibility.js'

let catalogue
let catalogueKey = ''

async function loadCatalogue(dates) {
  const { date, vientianeDate } = dates
  const key = `${date}:${vientianeDate}:${loadLltDatabase.revision}:${showNonRunningTrains()}`
  if (!catalogue || catalogueKey !== key) {
    catalogueKey = key
    const task = Promise.all([
      api.get('/llt-trains', { params: { date }, timeout: timetableTimeout }),
      api.get('/station/discovery', { params: dates, timeout: timetableTimeout }),
    ]).then(([trains, stations]) => {
      if (!trains.data.success || !stations.data.success) throw new Error('读取失败')
      return { trains: trains.data.trains || [], stations: stations.data.stations || [] }
    })
    catalogue = task
    task.catch(() => { if (catalogue === task) catalogue = null })
  }
  return catalogue
}

export function initializeGlobalSearch() {
  if (typeof native?.searchResult !== 'function') return
  window.addEventListener('crticket-search-request', async event => {
    const { query, requestId } = event.detail || {}
    if (typeof query !== 'string' || !query.trim()) return
    const now = Date.now()
    const dates = stationDiscoveryDates(now)
    const { date } = dates
    try {
      const [catalogueResult, routingResult] = await Promise.allSettled([
        loadCatalogue(dates),
        /^(?:[A-Z]*|0[A-Z]+)\d+(?:\/[A-Z]*\d+)*$/i.test(query.trim())
          ? loadLltDatabase().then(database => queryLltRouting(database, query.trim().toUpperCase(), date))
          : Promise.resolve(null),
      ])
      const data = catalogueResult.status === 'fulfilled' ? catalogueResult.value : { trains: [], stations: [] }
      const routing = routingResult.status === 'fulfilled' ? routingResult.value : null
      const matchingRow = routing?.groups
        .map(group => group.rows.find(row => row.highlighted))
        .find(Boolean)
      native.searchResult(JSON.stringify({
        requestId, date,
        error: catalogueResult.status === 'rejected',
        routing: matchingRow ? [{ ...matchingRow, train: routing.train }] : [],
        trains: filterSearchTrains(data.trains, query),
        stations: filterSearchStations(data.stations, parseStations(), query)
          .map(row => stationSummaryNow(row, stationClock(now, row.station).time)),
      }))
    } catch {
      native.searchResult(JSON.stringify({ requestId, error: true }))
    }
  })
}
