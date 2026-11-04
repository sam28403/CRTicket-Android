import test from 'node:test'
import assert from 'node:assert/strict'
import { quizLiveSources } from './quizLiveSources.js'

test('live quiz exports current schedules, overnight stop times and routing without asset records', () => {
  const schedule = { startDate: '2026-11-04', endDate: '2026-11-05' }
  const routing = [{ start: 20261104, end: 20261105, trains: ['K1', 'K2'] }]
  const source = quizLiveSources({
    version: '20261104', routing,
    trains: new Map([['K1', {
      train: 'K1', schedule,
      stops: [
        { station: 'A', arrival: null, departure: '23:50', departureDay: 0, mileage: 0 },
        { station: 'B', arrival: '01:05', arrivalDay: 1, departure: null, mileage: 100 },
      ],
    }]]),
  })
  assert.equal(source.version, '20261104')
  assert.deepEqual(source.trains[0], {
    train: 'K1', schedule, stops: [['A', null, 1430, 0], ['B', 1505, null, 100]],
  })
  assert.deepEqual(source.routes, routing)
  assert.deepEqual(source.lines, [])
  assert.deepEqual(source.stations, [])
  assert.deepEqual(quizLiveSources({ trains: new Map() }).routes, [])
})
