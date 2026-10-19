import test from 'node:test'
import assert from 'node:assert/strict'
import { addStationHistory, dailyStationPicks, stationSummaryNow } from './stationDiscovery.js'
import { listStationSummaries } from '../android/railway/stationDiscovery.js'

test('station history normalizes, deduplicates, and limits recent queries', () => {
  assert.deepEqual(addStationHistory(['北京南', '上海虹桥'], ' 上海虹桥 '), ['上海虹桥', '北京南'])
  assert.deepEqual(addStationHistory(['北京南'], ''), ['北京南'])
  assert.equal(addStationHistory(Array.from({ length: 10 }, (_, i) => `车站${i}`), '新站').length, 10)
})

test('daily picks are stable, unique, and only contain stations with trains', () => {
  const stations = Array.from({ length: 20 }, (_, i) => ({ station: `站${i}`, total: i }))
  const picks = dailyStationPicks(stations, '2026-10-19')
  assert.equal(picks.length, 3)
  assert.equal(new Set(picks.map(row => row.station)).size, 3)
  assert.ok(picks.every(row => row.total > 0))
  assert.deepEqual(picks, dailyStationPicks(stations, '2026-10-19'))
  assert.deepEqual(dailyStationPicks([], '2026-10-19'), [])
  assert.equal(stationSummaryNow({ completionTimes: ['08:00', '12:00'], passingWindows: [] }, '08:00').handled, 1)
})

test('summaries count overnight origin dates, terminals and passing trains consistently', () => {
  const database = { trains: new Map([
    ['K1', { train: 'K1', schedule: { startDate: '2026-10-18', endDate: '2026-10-18' }, stops: [
      { station: '甲', departure: '23:00', departureDay: 0 },
      { station: '乙', arrival: '23:50', arrivalDay: 0, departure: '00:10', departureDay: 1 },
      { station: '丙', arrival: '02:00', arrivalDay: 1 },
    ] }],
    ['G2', { train: 'G2', schedule: { startDate: '2026-10-19', endDate: '2026-10-19' }, stops: [
      { station: '乙', departure: '08:00', departureDay: 0 },
      { station: '丙', arrival: '09:00', arrivalDay: 0 },
    ] }],
  ]) }
  const rows = listStationSummaries(database, '2026-10-19')
  assert.deepEqual(rows.map(row => row.total), [2, 2])
  const passing = rows.find(row => row.station === '乙')
  assert.equal(passing.origin, 1)
  assert.equal(passing.terminal, 0)
  assert.equal(stationSummaryNow(passing, '00:00').passing, 1)
  assert.equal(stationSummaryNow(passing, '00:09:59').passing, 1)
  assert.equal(stationSummaryNow(passing, '00:10').passing, 0)
  assert.equal(stationSummaryNow(passing, '00:10').handled, 1)
  const terminal = rows.find(row => row.station === '丙')
  assert.equal(terminal.terminal, 2)
  assert.equal(stationSummaryNow(terminal, '02:00').passing, 0)
  const yesterday = listStationSummaries(database, '2026-10-18')
  assert.equal(yesterday.find(row => row.station === '乙').completionTimes.length, 0)
  const yesterdayPassing = yesterday.find(row => row.station === '乙')
  assert.equal(stationSummaryNow(yesterdayPassing, '23:49:59').passing, 0)
  assert.equal(stationSummaryNow(yesterdayPassing, '23:50').passing, 1)
  assert.deepEqual(listStationSummaries(database, '2026-10-21'), [])
})
