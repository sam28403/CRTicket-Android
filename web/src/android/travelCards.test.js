import test from 'node:test'
import assert from 'node:assert/strict'
import { summarizeTravelCards } from './travelCards.js'

const train = {
  train: 'V24',
  schedule: {
    startDate: '2026-11-01',
    endDate: '2026-11-30',
    rule: { period: 2, mask: 1 },
  },
  stops: [
    { station: '甲', departure: '21:00', departureDay: 0, mileage: 0 },
    { station: '二连', departure: '23:00', departureDay: 0, mileage: 120 },
    { station: '北京', arrival: '02:15', arrivalDay: 1, mileage: 550 },
  ],
}
const database = { trains: new Map([[train.train, train]]) }

test('a train appears without strike-through even between operating days', () => {
  const { trains } = summarizeTravelCards(
    database, [{ key: 0, train: 'V24' }], [], Date.parse('2026-11-20T04:00:00Z'), false,
  )
  assert.equal(trains[0].notRunning, undefined)
  assert.equal(trains[0].stationCount, 3)
})

test('an article section uses matching stops, elapsed time and distance from the local timetable', () => {
  const { trains } = summarizeTravelCards(
    database, [{ key: 0, train: 'V24', from: '二连', to: '北京' }], [],
    Date.parse('2026-12-01T04:00:00Z'), false,
  )
  assert.deepEqual(trains[0], {
    key: 0, train: 'V24',
    from: '二连', to: '北京', duration: '03:15', stationCount: 2, mileage: 430,
  })
})
