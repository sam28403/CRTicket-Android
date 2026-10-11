import test from 'node:test'
import assert from 'node:assert/strict'

globalThis.window = {}
const {
  supportsTrainTracking, trackingPayload, requestTrainTrackingPermission,
  getTrainTrackingState, startTrainTracking, stopTrainTracking,
} = await import('./trainTracking.js')
const { callNativeNotification } = await import('./monitorNotifications.js')

const schedule = {
  train: 'G1/G2', date: '2026-10-10',
  stops: [
    { station: '北京', train: 'G1', arrival: null, departure: '23:00', arrivalDay: 0, departureDay: 0, mileage: 0 },
    { station: '上海', train: 'G2', arrival: '09:00', departure: null, arrivalDay: 1, departureDay: 1 },
  ],
}

test('tracking payload preserves day offsets and original offline query identity', () => {
  const parsed = JSON.parse(trackingPayload(schedule, 'G1A/G2A', 'llt'))
  assert.equal(parsed.queryTrain, 'G1A/G2A')
  assert.equal(parsed.querySource, 'llt')
  assert.equal(parsed.train, 'G1/G2')
  assert.equal(parsed.stops[1].arrivalDay, 1)
  assert.deepEqual(parsed.stops.map(stop => stop.train), ['G1', 'G2'])
  assert.equal(parsed.stops[1].departure, null)
  assert.equal(parsed.stops[0].mileage, undefined)
  assert.equal(JSON.parse(trackingPayload(schedule)).queryTrain, 'G1/G2')
})

test('only a complete native bridge enables train tracking', () => {
  assert.equal(supportsTrainTracking(), false)
  window.CRTicketAndroid = { startTrainTracking() {} }
  assert.equal(supportsTrainTracking(), false)
  window.CRTicketAndroid = {
    requestTrainTrackingPermission() {}, trainTrackingState() {},
    startTrainTracking() {}, stopTrainTracking() {},
  }
  assert.equal(supportsTrainTracking(), true)
})

test('permission, start, restored state and stop use matching native callbacks', async () => {
  let active = { active: false }
  window.CRTicketAndroid = {
    requestTrainTrackingPermission(id) {
      window.crticketNotificationResult(id, { granted: true })
    },
    startTrainTracking(id, payload) {
      const data = JSON.parse(payload)
      active = { active: true, train: data.train, date: data.date, queryTrain: data.queryTrain }
      queueMicrotask(() => window.crticketNotificationResult(id, { success: true }))
    },
    trainTrackingState(id) { window.crticketNotificationResult(id, active) },
    stopTrainTracking(id) {
      active = { active: false }
      window.crticketNotificationResult(id, { success: true })
    },
  }
  assert.equal((await requestTrainTrackingPermission()).granted, true)
  assert.equal((await startTrainTracking(trackingPayload(schedule))).success, true)
  assert.equal((await getTrainTrackingState()).active, true)
  assert.equal((await stopTrainTracking()).success, true)
  assert.equal((await getTrainTrackingState()).active, false)
})

test('native startup failures and notification denial remain actionable', async () => {
  window.CRTicketAndroid = {
    requestTrainTrackingPermission(id) {
      window.crticketNotificationResult(id, { granted: false, message: '请开启列车追踪通知' })
    },
    startTrainTracking(id) {
      window.crticketNotificationResult(id, { success: false, message: '请开启列车追踪通知' })
    },
  }
  assert.equal((await requestTrainTrackingPermission()).granted, false)
  assert.equal((await startTrainTracking(trackingPayload(schedule))).message, '请开启列车追踪通知')
})

test('unmount cancels pending calls and ignores late callbacks', async () => {
  let pendingId
  window.CRTicketAndroid = { trainTrackingState(id) { pendingId = id } }
  const controller = new AbortController()
  const state = getTrainTrackingState(controller.signal)
  controller.abort()
  await assert.rejects(state, error => error.code === 'ERR_CANCELED')
  assert.doesNotThrow(() => window.crticketNotificationResult(pendingId, { active: true }))
})

test('already arrived trains can successfully publish a terminal notice', async () => {
  const historical = { ...schedule, date: '2026-01-01' }
  window.CRTicketAndroid = {
    startTrainTracking(id, payload) {
      assert.equal(JSON.parse(payload).date, historical.date)
      window.crticketNotificationResult(id, { success: true, finished: true })
    },
  }
  assert.deepEqual(await startTrainTracking(trackingPayload(historical)), {
    success: true,
    finished: true,
  })
})

test('an unresponsive bridge times out and safely discards late responses', async () => {
  let pendingId
  window.CRTicketAndroid = { trainTrackingState(id) { pendingId = id } }
  await assert.rejects(callNativeNotification('trainTrackingState', [], undefined, 5), /超时/)
  assert.doesNotThrow(() => window.crticketNotificationResult(pendingId, { active: true }))
})
