import api, { timetableTimeout } from '../api.js'
import { native } from './platform.js'
import { parseStations } from '../utils/stations.js'
import { stationSummaryNow } from '../utils/stationDiscovery.js'
import { trainQueryRange } from '../utils/trainQueryDate.js'
import { filterSearchTrains, filterSearchStations } from '../utils/globalSearch.js'

let catalogue
let catalogueDate = ''

async function loadCatalogue(date) {
  if (!catalogue || catalogueDate !== date) {
    catalogueDate = date
    const task = Promise.all([
      api.get('/llt-trains', { params: { date }, timeout: timetableTimeout }),
      api.get('/station/discovery', { params: { date }, timeout: timetableTimeout }),
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
    const date = trainQueryRange().today
    try {
      const data = await loadCatalogue(date)
      const time = new Intl.DateTimeFormat('en-GB', {
        timeZone: 'Asia/Shanghai', hour: '2-digit', minute: '2-digit', second: '2-digit',
      }).format(new Date())
      native.searchResult(JSON.stringify({
        requestId, date,
        trains: filterSearchTrains(data.trains, query),
        stations: filterSearchStations(data.stations, parseStations(), query)
          .map(row => stationSummaryNow(row, time)),
      }))
    } catch {
      native.searchResult(JSON.stringify({ requestId, error: true }))
    }
  })
}
