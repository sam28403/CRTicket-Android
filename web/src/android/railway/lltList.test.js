import test from 'node:test'
import assert from 'node:assert/strict'
import { findLltTrainCandidates, listLltTrains, lltTrainSummary } from './lltList.js'

test('vehicle cards and train list use the same summary for overnight services', () => {
  const item = {
    train: 'G101/G104',
    schedule: { startDate: '2026-10-01', endDate: '2026-10-31' },
    stops: [
      { station: '甲', departure: '23:30', departureDay: 0, mileage: 0 },
      { station: '乙', arrival: '01:00', arrivalDay: 1, mileage: 300 },
    ],
  }
  const database = { version: '20261021', trains: new Map([[item.train, item]]) }
  const found = findLltTrainCandidates(database, 'G104', '2026-10-21')[0]
  assert.deepEqual(lltTrainSummary(found), listLltTrains(database, '2026-10-21').trains[0])
  assert.deepEqual(lltTrainSummary(found), {
    train: 'G101/G104', from: '甲', to: '乙', duration: '01:30', stationCount: 2, mileage: 300,
  })
  assert.equal(findLltTrainCandidates(database, 'G104', '2026-11-01').length, 0)
})
