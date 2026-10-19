import { stationQueryName } from './stationName.js'

export function addStationHistory(history, value) {
  const name = stationQueryName(value)
  return name ? [name, ...history.filter(item => item !== name)].slice(0, 10) : history
}

export function dailyStationPicks(stations, date) {
  const candidates = stations.filter(row => row.total > 0).slice()
  let seed = [...date].reduce((n, char) => Math.imul(n, 31) + char.charCodeAt(0) | 0, 0)
  for (let i = candidates.length - 1; i > 0; i--) {
    seed = Math.imul(seed, 1664525) + 1013904223 | 0
    const j = (seed >>> 0) % (i + 1)
    ;[candidates[i], candidates[j]] = [candidates[j], candidates[i]]
  }
  return candidates.slice(0, 3)
}

export function stationSummaryNow(row, time) {
  const [hours, minutes, seconds = 0] = time.split(':').map(Number)
  const current = hours * 3600 + minutes * 60 + seconds
  return {
    ...row,
    handled: row.completionTimes.filter(value => value <= time).length,
    passing: row.passingWindows.filter(window =>
      window.arrival <= current && current < window.departure).length,
  }
}
