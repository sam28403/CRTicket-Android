import test from 'node:test'
import assert from 'node:assert/strict'
import { effectScope, nextTick, ref } from 'vue'
import { useTrainDelay } from '../composables/useTrainDelay.js'

const original = {
  train: 'C412', date: '2026-10-26',
  stops: [{ station: '嘉兴', arrival: '23:58', departure: '00:02', stay: 4 }],
}
const response = offsets => ({
  success: true,
  result: { ...original, delayAvailable: true, stops: [offsets] },
})
const settle = async () => { await nextTick(); await nextTick() }

function fixture(t, query) {
  const scope = effectScope()
  t.after(() => scope.stop())
  const schedule = ref(null)
  const delay = scope.run(() => useTrainDelay(schedule, query))
  return { schedule, delay }
}

test('train cache survives failures, empty results and exceptions, and keeps retrying', async t => {
  t.mock.timers.enable({ apis: ['Date', 'setTimeout'], now: Date.parse('2026-10-26T12:00:00+08:00') })
  const replies = [
    response({ arrivalOffset: 5, departureOffset: 7 }),
    { success: false }, { success: true, result: { delayAvailable: false } },
    new Error('network'), response({ arrivalOffset: 0 }),
  ]
  let requests = 0
  const { schedule, delay } = fixture(t, async () => {
    const reply = replies[requests++]
    if (reply instanceof Error) throw reply
    return reply
  })
  schedule.value = original
  await settle()
  for (let i = 0; i < 3; i++) {
    t.mock.timers.tick(600000)
    await settle()
    assert.equal(delay.enabled.value, true)
    assert.equal(delay.result.value.stops[0].arrival, '00:03')
    assert.equal(delay.result.value.stops[0].arrivalDay, 1)
  }
  await delay.refresh()
  const stop = delay.result.value.stops[0]
  assert.equal(requests, 5)
  assert.equal(stop.arrivalOffset, 0)
  assert.equal(stop.arrival, '23:58')
  assert.equal(stop.departureOffset, 7)
  assert.equal(stop.departure, '00:09')
  assert.equal(stop.departureDay, 1)
  assert.equal(stop.stay, 11)
})

test('train cache survives replacement timetables, switching queries and toggles', async t => {
  let requests = 0
  const { schedule, delay } = fixture(t, async () => ++requests === 1
    ? response({ arrivalOffset: 5 }) : { success: false })
  schedule.value = original
  await settle()
  delay.toggle()
  await settle()
  assert.equal(delay.result.value.stops[0].arrival, '23:58')
  delay.toggle()
  await settle()
  assert.equal(delay.result.value.stops[0].arrivalOffset, 5)
  schedule.value = { ...original, train: 'C413' }
  await settle()
  assert.equal(delay.result.value.stops[0].arrivalOffset, undefined)
  schedule.value = { ...original, stops: original.stops.map(row => ({ ...row })) }
  await settle()
  assert.equal(delay.result.value.stops[0].arrivalOffset, 5)
  schedule.value = { ...original, date: '2026-10-27' }
  await settle()
  assert.equal(delay.result.value.stops[0].arrivalOffset, undefined)
})

test('train cache expires at Beijing midnight and ignores yesterday pending responses', async t => {
  t.mock.timers.enable({ apis: ['Date', 'setTimeout'], now: Date.parse('2026-10-26T23:59:59+08:00') })
  let requests = 0
  let finish
  const { schedule, delay } = fixture(t, () => {
    requests++
    if (requests === 1) return Promise.resolve(response({ arrivalOffset: 5 }))
    if (requests === 2) return new Promise(resolve => { finish = resolve })
    return Promise.resolve({ success: false })
  })
  schedule.value = original
  await settle()
  const pending = delay.refresh()
  t.mock.timers.tick(1000)
  await settle()
  assert.equal(delay.result.value.stops[0].arrival, '23:58')
  finish(response({ arrivalOffset: 10 }))
  await pending
  await settle()
  assert.equal(delay.result.value.stops[0].arrivalOffset, undefined)
})
