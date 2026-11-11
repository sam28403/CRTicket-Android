import snapshot from './lcr-offline.json' with { type: 'json' }
import { compareTrains, listLltTrains, runsOnDate } from './lltList.js'
import { listLltStation } from './stationLlt.js'

const database = {
  version: snapshot.checkedAt,
  trains: new Map(snapshot.trains.map((item, index) => [index, item])),
}

export function combineTrainResults([first, ...alternatives]) {
  return alternatives.length ? { ...first, alternatives } : first
}

export function lcrTrainDetail(train, date) {
  const makeDetail = (stops = []) => ({
    success: true,
    train,
    date,
    source: '老中铁路（离线）',
    dataset: 'lcr',
    timeZone: 'beijing',
    defaultTimeZone: 'vientiane',
    from: stops[0]?.station || '',
    to: stops.at(-1)?.station || '',
    stops,
    warnings: [],
  })
  const matches = snapshot.trains.filter(item => item.train === train && runsOnDate(item.schedule, date))
  return matches.length ? combineTrainResults(matches.map(item => makeDetail(item.stops))) : makeDetail()
}

export function listLcrTrains(date, domestic) {
  const lcr = listLltTrains(database, date).trains.map(row => ({ ...row, dataset: 'lcr' }))
  if (!lcr.length && domestic) return domestic
  const trains = [...(domestic?.trains || []), ...lcr].sort(compareTrains)
  return { ...domestic, date, lcrVersion: database.version, total: trains.length, trains }
}

export function listLcrStation(station, date) {
  return listLltStation(database, station, date)
    .map(row => ({ ...row, id: `lcr-${row.id}`, dataset: 'lcr' }))
}
