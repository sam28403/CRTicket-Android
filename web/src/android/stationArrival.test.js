import test from 'node:test'
import assert from 'node:assert/strict'

const state = { success: true, supported: true, enabled: false, active: false }
let allow = true
let delayed
globalThis.window = {
  CRTicketAndroid: {
    stationArrivalState(id) {
      queueMicrotask(() => window.crticketNotificationResult(id, { ...state }))
    },
    enableStationArrival(id) {
      if (delayed) { delayed.id = id; return }
      if (allow) Object.assign(state, { enabled: true, active: true })
      queueMicrotask(() => window.crticketNotificationResult(id, {
        ...state, success: allow, message: allow ? '已开启' : '请允许始终定位',
      }))
    },
    disableStationArrival(id) {
      Object.assign(state, { enabled: false, active: false })
      queueMicrotask(() => window.crticketNotificationResult(id, { ...state }))
    },
  },
}
const arrival = await import('./stationArrival.js')

test('到站提醒仅在完整本机接口存在时显示', () => {
  assert.equal(arrival.supportsStationArrival(), true)
  const method = window.CRTicketAndroid.disableStationArrival
  delete window.CRTicketAndroid.disableStationArrival
  assert.equal(arrival.supportsStationArrival(), false)
  window.CRTicketAndroid.disableStationArrival = method
})

test('本机到站提醒的读取、开启和关闭不依赖浏览器定位或后台服务器', async () => {
  assert.equal((await arrival.getStationArrivalState()).enabled, false)
  assert.equal((await arrival.enableStationArrival()).active, true)
  assert.equal((await arrival.getStationArrivalState()).enabled, true)
  assert.equal((await arrival.disableStationArrival()).active, false)
})

test('定位或通知授权拒绝保持关闭并提供系统设置提示', async () => {
  allow = false
  const result = await arrival.enableStationArrival()
  assert.equal(result.success, false)
  assert.equal(result.enabled, false)
  assert.equal(result.message, '请允许始终定位')
  allow = true
})

test('页面离开后取消等待并丢弃迟到的权限结果', async () => {
  delayed = {}
  const controller = new AbortController()
  const pending = arrival.enableStationArrival(controller.signal)
  controller.abort()
  await assert.rejects(pending, error => error.code === 'ERR_CANCELED')
  window.crticketNotificationResult(delayed.id, { success: true, enabled: true })
  delayed = null
  assert.equal((await arrival.getStationArrivalState()).enabled, false)
})
