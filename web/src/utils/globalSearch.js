import { compareTrains } from '../android/railway/lltList.js'

export function filterSearchTrains(trains, query) {
  const key = query.trim().toUpperCase()
  if (!key) return []
  return trains.filter(row => row.train.toUpperCase().includes(key))
    .sort((a, b) => Number(b.train.toUpperCase() === key)
      - Number(a.train.toUpperCase() === key) || compareTrains(a, b))
}

export function filterSearchStations(rows, catalogue, query) {
  const key = query.trim().toLowerCase()
  if (!key) return []
  const matches = new Set(catalogue.filter(station =>
    [station.name, station.en, station.abbr, station.telecode].some(value =>
      value?.toLowerCase().includes(key))).map(station => station.name))
  // Preserve the station list's default order (train count, then station name).
  return rows.filter(row => matches.has(row.station)
    || row.station.toLowerCase().includes(key))
}
