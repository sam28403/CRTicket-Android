import { clockMinutes } from './clock.js'

const dayMs = 86400000
const categories = ['G', 'D', 'C', 'S', 'Z', 'T', 'K', 'L']

export function validListDate(date) {
  if (typeof date !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(date)) return false
  const parsed = new Date(`${date}T00:00:00Z`)
  return Number.isFinite(parsed.getTime()) && parsed.toISOString().slice(0, 10) === date
}

export function runsOnDate(schedule, date) {
  // 缺少日期元数据时不能把记录当作每天开行。
  if (!schedule?.startDate || !schedule.endDate) return false
  if (date < schedule.startDate || date > schedule.endDate) return false
  if (!schedule.rule) return true
  const days = (
    Date.parse(`${date}T00:00:00Z`) - Date.parse(`${schedule.startDate}T00:00:00Z`)
  ) / dayMs
  return Math.floor(schedule.rule.mask / 2 ** (days % schedule.rule.period)) % 2 === 1
}

function sortKey(train) {
  const first = train.split('/')[0]
  const prefix = first.match(/^[A-Z]/)?.[0]
  const category = categories.indexOf(prefix)
  return [
    category >= 0 ? category : /^\d{4}[A-Z]*$/.test(first) ? 8 : 9,
    Number(first.match(/\d+/)?.[0] || 0),
  ]
}

export function compareTrains(a, b) {
  const left = sortKey(a.train)
  const right = sortKey(b.train)
  return left[0] - right[0]
    || left[1] - right[1]
    || a.train.localeCompare(b.train, 'en', { numeric: true })
}

export function listLltTrains(database, date) {
  const rows = []
  for (const item of database.trains.values()) {
    if (!runsOnDate(item.schedule, date)) continue
    const first = item.stops[0]
    const last = item.stops.at(-1)
    const elapsed = first.departure && last.arrival
      ? (last.arrivalDay - (first.departureDay || 0)) * 1440
        + clockMinutes(last.arrival) - clockMinutes(first.departure)
      : null
    const duration = elapsed == null || elapsed < 0
      ? null
      : `${String(Math.floor(elapsed / 60)).padStart(2, '0')}:${String(elapsed % 60).padStart(2, '0')}`
    rows.push({
      train: item.train,
      from: first.station,
      to: last.station,
      duration,
      stationCount: item.stops.length,
      mileage: last.mileage,
    })
  }
  rows.sort(compareTrains)
  return { date, version: database.version, total: rows.length, trains: rows }
}

