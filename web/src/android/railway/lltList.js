import { clockMinutes } from './clock.js'

const dayMs = 86400000
const categories = ['G', 'D', 'C', 'S', 'Z', 'T', 'K', 'L']
// 新规则 1：启用时替换上方 categories 定义。
// const categories = ['G', 'D', 'C', 'S', 'Z', 'N', 'V', 'Y']
// 新规则 2：启用时替换上方 categories 定义。
// const categories = ['X', 'G', 'T', 'K', 'D', 'C', 'S', 'Z', 'N', 'V', 'Y']

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

export function findLltTrainCandidates(database, train, date) {
  const exact = database.trains.get(train)
  if (exact && runsOnDate(exact.schedule, date)) return [exact]
  // 完整记录号和方案后缀仍须精确匹配，单车次才按沿途车次号查找。
  if (!/^[A-Z]?\d{1,5}$/.test(train)) return []
  return [...database.trains.values()].filter(item => {
    if (!runsOnDate(item.schedule, date)) return false
    if (!/^[A-Z]?\d+(?:\/[A-Z]?\d+)+$/.test(item.train)) return false
    const parts = item.train.split('/')
    const prefix = parts[0].match(/^[A-Z]/)?.[0] || ''
    return parts.some(part => (/^\d/.test(part) ? `${prefix}${part}` : part) === train)
  })
}

function sortKey(train) {
  const first = train.split('/')[0]
  const prefix = first.match(/^[A-Z]/)?.[0]
  const category = categories.indexOf(prefix)
  return [
    category >= 0
      ? category
      : categories.length + (/^\d{4}[A-Z]*$/.test(first) ? 0 : 1),
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
    rows.push(lltTrainSummary(item))
  }
  rows.sort(compareTrains)
  return { date, version: database.version, total: rows.length, trains: rows }
}

export function lltTrainSummary(item) {
  const first = item.stops[0]
  const last = item.stops.at(-1)
  const elapsed = first.departure && last.arrival
    ? (last.arrivalDay - (first.departureDay || 0)) * 1440
      + clockMinutes(last.arrival) - clockMinutes(first.departure)
    : null
  const duration = elapsed == null || elapsed < 0
    ? null
    : `${String(Math.floor(elapsed / 60)).padStart(2, '0')}:${String(elapsed % 60).padStart(2, '0')}`
  return {
    train: item.train,
    from: first.station,
    to: last.station,
    duration,
    stationCount: item.stops.length,
    mileage: last.mileage,
  }
}

