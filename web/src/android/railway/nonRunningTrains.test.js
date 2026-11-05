import test from 'node:test'
import assert from 'node:assert/strict'
import { listLltTrains } from './lltList.js'
import { listLltStation } from './stationLlt.js'
import { lltTrainDetail } from './lltDetail.js'
import { createStationQuery } from './station.js'
import { showNonRunningTrains } from '../timetableVisibility.js'
import { stationTrainLocation } from '../../utils/stationNavigation.js'

const date = '2026-11-04'
function database() {
  const stops = [
    { station: '甲', departure: '23:00', arrivalDay: 0, departureDay: 0, mileage: 0 },
    { station: '乙', arrival: '23:58', departure: '00:03', stay: 5,
      arrivalDay: 1, departureDay: 2, mileage: 200 },
    { station: '丙', arrival: '01:00', arrivalDay: 2, departureDay: 2, mileage: 300 },
  ]
  const schedule = { startDate: '2026-11-01', endDate: '2026-11-30', rule: null }
  const records = [
    { train: 'G1', schedule, stops },
    { train: 'G2', schedule: { ...schedule, rule: { period: 2, mask: 1 } }, stops },
    { train: 'G3', schedule: { ...schedule, endDate: '2026-11-02' }, stops },
    { train: 'G4', schedule: { ...schedule, startDate: '2026-11-05' }, stops },
  ]
  return { version: '20261104', trains: new Map(records.map(item => [item.train, item])) }
}

test('the switch defaults off; opt-in includes cyclic, expired and not-yet-active records', () => {
  assert.equal(showNonRunningTrains(), false)
  const data = database()
  assert.deepEqual(listLltTrains(data, date).trains.map(row => row.train), ['G1'])
  const rows = listLltTrains(data, date, true).trains
  assert.deepEqual(rows.map(row => row.train), ['G1', 'G2', 'G3', 'G4'])
  assert.equal(rows[0].notRunning, undefined)
  assert.ok(rows.slice(1).every(row => row.notRunning === true))
  assert.deepEqual(lltTrainDetail(data, 'G2', date).stops, [])
  assert.equal(lltTrainDetail(data, 'G2', date, true).stops.length, 3)
})

test('station availability follows each origin date when arrival and departure cross midnight', () => {
  const data = database()
  const usual = listLltStation(data, '乙', date)
  const all = listLltStation(data, '乙', date, true)
  assert.equal(all.length, 8)
  assert.ok(usual.every(row => !row.notRunning))
  const arrival = all.find(row => row.train === 'G2' && row.arrival)
  const departure = all.find(row => row.train === 'G2' && row.departure)
  assert.equal(arrival.trainDate, '2026-11-03')
  assert.equal(arrival.notRunning, undefined)
  assert.equal(departure.trainDate, '2026-11-02')
  assert.equal(departure.notRunning, true)
  assert.equal(stationTrainLocation(departure, '路路通', date).query.source, 'llt')
  assert.equal(new Set(all.map(row => row.id)).size, all.length)
})

test('station cache cannot reuse the opposite visibility setting after a toggle', async () => {
  let enabled = false
  let calls = 0
  const original = globalThis.CRTicketDeveloper
  globalThis.CRTicketDeveloper = { showNonRunningTrains: () => enabled }
  try {
    const request = createStationQuery({
      now: () => Date.parse(`${date}T12:00:00+08:00`),
      loadDatabase: async () => { calls++; return database() },
    })
    const active = await request({ station: '乙' })
    enabled = true
    const all = await request({ station: '乙' })
    assert.ok(all.total > active.total)
    enabled = false
    assert.equal((await request({ station: '乙' })).total, active.total)
    assert.equal(calls, 2)
  } finally {
    if (original === undefined) delete globalThis.CRTicketDeveloper
    else globalThis.CRTicketDeveloper = original
  }
})
