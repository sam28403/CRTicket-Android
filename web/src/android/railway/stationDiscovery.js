import { listLltStation } from './stationLlt.js'
import { normalizeStationName } from '../../utils/stationName.js'
import { stationRowTiming } from '../../utils/stationBoardTime.js'

const caches = new WeakMap()

// Reuse board rows so overnight trains follow exactly the same service-date rules.
export function listStationSummaries(database, date) {
  let cached = caches.get(database)
  if (cached?.date === date) return cached.stations
  const names = new Set()
  for (const train of database.trains.values()) {
    for (const stop of train.stops) names.add(normalizeStationName(stop.station))
  }
  const stations = []
  for (const station of names) {
    const rows = listLltStation(database, station, date)
    if (!rows.length) continue
    let origin = 0
    let terminal = 0
    const completionTimes = []
    const passingWindows = []
    for (const row of rows) {
      if (row.arrivalLabel === '始发站') origin++
      if (row.departureLabel === '终到站') terminal++
      const { arrivalSeconds, departureSeconds } = stationRowTiming(row, 'all')
      if (Number.isFinite(arrivalSeconds) && Number.isFinite(departureSeconds)) {
        passingWindows.push({ arrival: arrivalSeconds, departure: departureSeconds })
      }
      // A train is handled after its departure, or its arrival at a terminal.
      if (!row.departure && row.departureLabel.startsWith('次日')) continue
      completionTimes.push(row.departure || row.arrival)
    }
    stations.push({ station, total: rows.length, origin, terminal,
      passingWindows, completionTimes })
  }
  stations.sort((a, b) => b.total - a.total || a.station.localeCompare(b.station, 'zh-CN'))
  caches.set(database, { date, stations })
  return stations
}
