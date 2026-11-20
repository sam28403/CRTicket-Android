import { onBeforeUnmount } from 'vue'
import { native } from './platform.js'
import { loadLltDatabase } from './railway/lltLoader.js'
import { findLltTrainCandidates, lltTrainSummary, runsOnDate } from './railway/lltList.js'
import { summarizeStation } from './railway/stationDiscovery.js'
import { stationSummaryNow } from '../utils/stationDiscovery.js'
import { normalizeStationName, stationQueryName } from '../utils/stationName.js'
import { stationClock } from '../utils/stationTimeZone.js'

function sectionStops(stops, from, to) {
  const fromName = normalizeStationName(from)
  const toName = normalizeStationName(to)
  const start = stops.findIndex(stop => normalizeStationName(stop.station) === fromName)
  const end = stops.findIndex((stop, index) =>
    index > start && normalizeStationName(stop.station) === toName)
  if (start < 0 || end < 0) return null
  const first = stops[start]
  const last = stops[end]
  const mileage = Number.isFinite(first.mileage) && Number.isFinite(last.mileage)
    ? last.mileage - first.mileage : null
  return [{ ...first, station: from }, ...stops.slice(start + 1, end), { ...last, station: to, mileage }]
}

function queryDate(schedule, date) {
  if (!schedule?.startDate || !schedule.endDate || runsOnDate(schedule, date)) return date
  const anchor = date < schedule.startDate ? schedule.startDate
    : date > schedule.endDate ? schedule.endDate : date
  const midnight = Date.parse(`${anchor}T00:00:00Z`)
  for (let offset = 0; offset < (schedule.rule?.period || 1); offset++) {
    const following = new Date(midnight + offset * 86400000).toISOString().slice(0, 10)
    if (runsOnDate(schedule, following)) return following
    const preceding = new Date(midnight - offset * 86400000).toISOString().slice(0, 10)
    if (runsOnDate(schedule, preceding)) return preceding
  }
  return date
}

function trainCard(database, marker, date) {
  const { key, train, from, to } = marker
  const candidates = findLltTrainCandidates(database, train, date)
  if (!candidates.length || from) {
    for (const candidate of findLltTrainCandidates(database, train, date, true)) {
      if (!candidates.includes(candidate)) candidates.push(candidate)
    }
  }
  for (const candidate of candidates) {
    const stops = from ? sectionStops(candidate.stops, from, to) : candidate.stops
    if (!stops?.length) continue
    return {
      ...lltTrainSummary({ ...candidate, stops }),
      key, train, queryTrain: candidate.train, queryDate: queryDate(candidate.schedule, date),
    }
  }
}

export function summarizeTravelCards(database, trains, stations, now, includeLcr) {
  const { date } = stationClock(now)
  return {
    date,
    trains: database ? trains.map(marker => trainCard(database, marker, date)).filter(Boolean) : [],
    stations: [...new Set(stations)].flatMap(input => {
      const station = stationQueryName(input)
      if (!station) return []
      const clock = stationClock(now, station)
      const summary = summarizeStation(database, station, clock.date, includeLcr)
      return summary.total ? [{ ...stationSummaryNow(summary, clock.time), key: input }] : []
    }),
  }
}

export function useTravelCards() {
  if (typeof native?.trainListResult !== 'function') return
  async function request(event) {
    const { requestId, trains, stations } = event.detail || {}
    if (!Number.isInteger(requestId) || !Array.isArray(trains) || !Array.isArray(stations)) return
    try {
      const database = await loadLltDatabase.local()
      const cards = summarizeTravelCards(database, trains, stations, Date.now(), !loadLltDatabase.usingImported)
      native.trainListResult(JSON.stringify({ kind: 'travelCards', requestId, ...cards }))
    } catch {
      native.trainListResult(JSON.stringify({ kind: 'travelCards', requestId, error: true }))
    }
  }
  window.addEventListener('crticket-travel-cards-request', request)
  onBeforeUnmount(() => window.removeEventListener('crticket-travel-cards-request', request))
}
