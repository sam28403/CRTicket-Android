import test from 'node:test'
import assert from 'node:assert/strict'
import { effectScope, nextTick, shallowRef } from 'vue'
import { useStationDelay } from '../composables/useStationDelay.js'

const original = {
  station: '嘉兴', date: '2026-10-26',
  trains: Array.from({ length: 17 }, (_, index) => ({
    id: `train-${index}`, train: `C${index + 1}`, trainDate: '2026-10-26',
    arrival: '19:53', departure: '19:56', stay: 3,
  })),
}
const settle = async () => { await nextTick(); await nextTick() }

function fixture(t, query) {
  const scope = effectScope()
  t.after(() => scope.stop())
  const schedule = shallowRef(null)
  const delay = scope.run(() => useStationDelay(schedule, query))
  return { schedule, delay }
}

test('station refresh retains all batches and missing fields while accepting zero offsets', async t => {
  t.mock.timers.enable({ apis: ['Date', 'setTimeout'], now: Date.parse('2026-10-26T12:00:00+08:00') })
  let requests = 0
  const { schedule, delay } = fixture(t, async payload => {
    requests++
    if (requests <= 2) return {
      success: true,
      rows: payload.trains.map(row => ({ id: row.id, arrivalOffset: 7, departureOffset: 9 })),
    }
    if (requests === 3) return { success: true, rows: [{ id: 'train-0', arrivalOffset: 0 }] }
    if (requests === 4) throw new Error('network')
    return { success: true, rows: [{ id: 'train-0', departureOffset: null }] }
  })
  schedule.value = original
  await settle()
  schedule.value = { ...original, trains: original.trains.map(row => ({ ...row })) }
  await settle()
  assert.equal(delay.result.value.trains[0].arrivalOffset, 0)
  assert.equal(delay.result.value.trains[0].departureOffset, 9)
  assert.equal(delay.result.value.trains[16].arrivalOffset, 7)
  t.mock.timers.tick(600000)
  await settle()
  assert.equal(requests, 6)
  assert.equal(delay.result.value.trains[0].departureOffset, 9)
  assert.equal(delay.result.value.trains[16].arrival, '20:00')
})

test('station cache survives switching stations and failed or empty refresh responses', async t => {
  let requests = 0
  const { schedule, delay } = fixture(t, async payload => ++requests <= 2 ? {
    success: true, rows: payload.trains.map(row => ({ id: row.id, arrivalOffset: 7 })),
  } : { success: false })
  schedule.value = original
  await settle()
  schedule.value = { ...original, station: '金华' }
  await settle()
  assert.equal(delay.result.value.trains[0].arrivalOffset, undefined)
  schedule.value = { ...original }
  await settle()
  assert.equal(delay.result.value.trains[0].arrivalOffset, 7)
  schedule.value = { ...original, date: '2026-10-27' }
  await settle()
  assert.equal(delay.result.value.trains[0].arrivalOffset, undefined)
})

test('station cache expires at Beijing midnight and ignores late refresh responses', async t => {
  t.mock.timers.enable({ apis: ['Date', 'setTimeout'], now: Date.parse('2026-10-26T23:59:59+08:00') })
  let requests = 0
  let finish
  const { schedule, delay } = fixture(t, async payload => {
    if (++requests <= 2) return {
      success: true, rows: payload.trains.map(row => ({ id: row.id, arrivalOffset: 7 })),
    }
    return new Promise(resolve => { finish = resolve })
  })
  schedule.value = original
  await settle()
  schedule.value = { ...original }
  await settle()
  t.mock.timers.tick(1000)
  assert.equal(delay.result.value.trains[0].arrivalOffset, undefined)
  finish({ success: true, rows: [{ id: 'train-0', arrivalOffset: 10 }] })
  await settle()
  assert.equal(delay.result.value.trains[0].arrivalOffset, undefined)
})
