import test from 'node:test'
import assert from 'node:assert/strict'
import { effectScope, nextTick, shallowRef } from 'vue'
import { applyStationDelay } from '../utils/stationDelay.js'
import { stationBoardRows } from '../utils/stationBoard.js'
import { useStationDelay } from '../composables/useStationDelay.js'

const row = {
  id: 'train-1', train: 'C412', trainDate: '2026-10-24',
  arrival: '19:53', departure: '19:56', arrivalLabel: '19:53', departureLabel: '19:56', stay: 3,
}

test('all station tabs share corrected clocks, annotations and actual dwell', () => {
  const actual = applyStationDelay(row, { arrivalOffset: 7, departureOffset: 9 })
  for (const tab of ['all', 'departure', 'arrival']) {
    const result = stationBoardRows([actual], tab)[0]
    assert.equal(result.arrivalLabel, "20:00 (+7')")
    assert.equal(result.departureLabel, "20:05 (+9')")
    assert.equal(result.stayLabel, '5 分钟')
    assert.equal(result.arrivalSeconds, 20 * 3600)
  }
})

test('early clocks, midnight and single-sided invalid dwell retain correct dates', () => {
  const actual = applyStationDelay({ ...row, arrival: '23:58', arrivalLabel: '23:58' }, { arrivalOffset: 7 })
  const result = stationBoardRows([actual], 'arrival')[0]
  assert.equal(result.arrivalLabel, "次日 00:05 (+7')")
  assert.equal(result.arrivalSeconds, 86400 + 300)
  assert.equal(applyStationDelay(row, { arrivalOffset: 7 }).stay, 3)
  const early = stationBoardRows([applyStationDelay(row, { departureOffset: -10 })], 'departure')[0]
  assert.equal(early.departureLabel, "19:46 (-10')")
})

test('station predictions merge in batches and late responses never replace another station', async t => {
  const scope = effectScope()
  t.after(() => scope.stop())
  const schedule = shallowRef(null)
  const requests = []
  const pending = []
  const delay = scope.run(() => useStationDelay(schedule, (payload) => {
    requests.push(payload)
    return new Promise(resolve => pending.push(resolve))
  }))
  schedule.value = { station: '嘉兴', date: '2026-10-24', trains: [row] }
  await nextTick()
  schedule.value = { station: '金华', date: '2026-10-24', trains: [{ ...row, id: 'other' }] }
  await nextTick()
  pending[0]({ success: true, rows: [{ id: row.id, arrivalOffset: 7 }] })
  await nextTick()
  assert.equal(delay.result.value.station, '金华')
  assert.equal(delay.result.value.trains[0].arrivalOffset, undefined)
  pending[1]({ success: true, rows: [{ id: 'other', arrivalOffset: 4 }] })
  await nextTick()
  assert.equal(delay.result.value.trains[0].arrivalOffset, 4)
  assert.equal(requests[1].trains[0].trainDate, '2026-10-24')
})
