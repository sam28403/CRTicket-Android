import test from 'node:test'
import assert from 'node:assert/strict'
import {
  addStationHistory, busyStationPicks, dailyStationPicks, stationSummaryNow,
} from './stationDiscovery.js'
import { bureauForTelecode, stationBureau } from './stationBureau.js'
import { listStationSummaries } from '../android/railway/stationDiscovery.js'

test('station history normalizes, deduplicates, and limits recent queries', () => {
  assert.deepEqual(addStationHistory(['北京南', '上海虹桥'], ' 上海虹桥 '), ['上海虹桥', '北京南'])
  assert.deepEqual(addStationHistory(['北京南'], ''), ['北京南'])
  assert.equal(addStationHistory(Array.from({ length: 10 }, (_, i) => `车站${i}`), '新站').length, 10)
})

test('busy stations rank the top ten by daily trains without changing the source', () => {
  const stations = Array.from({ length: 15 }, (_, i) => ({ station: `站${i}`, total: i }))
  const original = structuredClone(stations)
  assert.deepEqual(busyStationPicks(stations).map(row => row.total), [14, 13, 12, 11, 10, 9, 8, 7, 6, 5])
  assert.deepEqual(stations, original)
  assert.deepEqual(busyStationPicks([{ station: '空站', total: 0 }]), [])
  assert.equal(busyStationPicks(stations.slice(0, 3)).length, 2)
})

test('bureau labels follow rail.re telecodes and do not guess unknown stations', () => {
  assert.equal(stationBureau(' 北京南 '), '京局')
  assert.equal(stationBureau('上海虹桥'), '上局')
  assert.equal(stationBureau('广州南'), '广铁')
  assert.equal(bureauForTelecode('COE'), '成局')
  assert.equal(bureauForTelecode('AAX'), '哈局')
  assert.equal(bureauForTelecode('AAS'), '南局')
  assert.equal(bureauForTelecode('AAO'), '青藏')
  assert.equal(stationBureau('拉萨'), '青藏')
  for (const code of ['', 'INVALID', 'AAI', 'AAA?']) assert.equal(bureauForTelecode(code), '')
  assert.equal(stationBureau('未收录车站'), '')
})

test('card progress handles zero, partial and fully handled days', () => {
  const row = { station: '北京南', total: 2, completionTimes: ['08:00', '12:00'], passingWindows: [] }
  assert.equal(stationSummaryNow(row, '07:59').handledRatio, 0)
  assert.equal(stationSummaryNow(row, '08:00').handledRatio, 0.5)
  assert.equal(stationSummaryNow(row, '12:00').handledRatio, 1)
  assert.equal(stationSummaryNow(row, '23:59').bureau, '京局')
  assert.equal(stationSummaryNow({ ...row, total: 0 }, '12:00').handledRatio, 0)
  assert.equal(stationSummaryNow({ ...row, total: 1 }, '12:00').handledRatio, 1)
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
