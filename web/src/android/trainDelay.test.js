import test from 'node:test'
import assert from 'node:assert/strict'
import { effectScope, nextTick, ref } from 'vue'
import { useTrainDelay } from '../composables/useTrainDelay.js'
import { trackingPayload } from './trainTracking.js'

const original = {
  train: 'C412', date: '2026-10-24',
  stops: [{ station: '嘉兴', arrival: '19:18', departure: '19:21', stay: 3 }],
}
const prediction = {
  ...original, delayAvailable: true,
  stops: [{ ...original.stops[0], arrival: '19:23', arrivalOffset: 5, stay: 3 }],
}
const settle = async () => { await nextTick(); await nextTick() }

function fixture(query, available = ref(true)) {
  const scope = effectScope()
  const schedule = ref(null)
  const delay = scope.run(() => useTrainDelay(schedule, query, available))
  return { scope, schedule, delay }
}

test('offline mode makes no delay requests, including toggles and manual refresh', async t => {
  let requests = 0
  const available = ref(false)
  const { scope, schedule, delay } = fixture(async () => {
    requests++
    return { success: true, result: prediction }
  }, available)
  t.after(() => scope.stop())
  schedule.value = original
  await settle()
  delay.toggle()
  await delay.refresh()
  await settle()
  assert.equal(requests, 0)
  assert.equal(delay.enabled.value, false)
  assert.equal(delay.result.value.stops[0].arrival, '19:18')
})

test('switching offline restores scheduled times and cancels periodic requests', async t => {
  t.mock.timers.enable({ apis: ['setTimeout'] })
  let requests = 0
  const available = ref(true)
  const { scope, schedule, delay } = fixture(async () => {
    requests++
    return { success: true, result: prediction }
  }, available)
  t.after(() => scope.stop())
  schedule.value = original
  await settle()
  available.value = false
  await settle()
  assert.equal(delay.result.value.stops[0].arrival, '19:18')
  t.mock.timers.tick(600000)
  assert.equal(requests, 1)
})

test('manual refresh retries a closed switch and waits for the latest prediction', async t => {
  let requests = 0
  const { scope, schedule, delay } = fixture(async () => {
    requests++
    return requests === 1 ? { success: false } : { success: true, result: prediction }
  })
  t.after(() => scope.stop())
  schedule.value = original
  await settle()
  assert.equal(delay.enabled.value, false)
  await delay.refresh()
  assert.equal(requests, 2)
  assert.equal(delay.enabled.value, true)
  assert.equal(delay.result.value.stops[0].arrival, '19:23')
  await delay.refresh()
  assert.equal(requests, 3)
})

test('predictions refresh every ten minutes and disabling restores the schedule', async t => {
  t.mock.timers.enable({ apis: ['setTimeout'] })
  let requests = 0
  const { scope, schedule, delay } = fixture(async () => {
    requests++
    return { success: true, result: prediction }
  })
  t.after(() => scope.stop())
  schedule.value = original
  await settle()
  assert.equal(delay.enabled.value, true)
  assert.equal(delay.result.value.stops[0].arrival, '19:23')
  t.mock.timers.tick(599999)
  assert.equal(requests, 1)
  t.mock.timers.tick(1)
  await settle()
  assert.equal(requests, 2)
  delay.toggle()
  await settle()
  assert.equal(delay.result.value.stops[0].arrival, '19:18')
  t.mock.timers.tick(600000)
  assert.equal(requests, 2)
})

test('no usable prediction closes the switch and keeps the original schedule', async t => {
  const { scope, schedule, delay } = fixture(async () => ({ success: true, result: original }))
  t.after(() => scope.stop())
  schedule.value = original
  await settle()
  assert.equal(delay.enabled.value, false)
  assert.equal(delay.result.value.stops[0].arrival, '19:18')
})

test('late predictions cannot override a disabled switch or a different query', async t => {
  const pending = []
  const { scope, schedule, delay } = fixture(() => new Promise(resolve => pending.push(resolve)))
  t.after(() => scope.stop())
  schedule.value = original
  await settle()
  delay.toggle()
  await settle()
  pending[0]({ success: true, result: prediction })
  await settle()
  assert.equal(delay.result.value.stops[0].arrival, '19:18')
  schedule.value = { ...original, train: 'C413' }
  await settle()
  assert.equal(delay.enabled.value, true)
  assert.equal(delay.result.value.train, 'C413')
})

test('tracking keeps corrected times, offsets and the original timetable for future updates', () => {
  const payload = JSON.parse(trackingPayload({
    ...prediction, delayEnabled: true, scheduledStops: original.stops,
  }))
  assert.equal(payload.delayEnabled, true)
  assert.equal(payload.stops[0].arrival, '19:23')
  assert.equal(payload.stops[0].arrivalOffset, 5)
  assert.equal(payload.scheduledStops[0].arrival, '19:18')
})
