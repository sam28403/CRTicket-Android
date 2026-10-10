import test from 'node:test'
import assert from 'node:assert/strict'

globalThis.window = {}
const {
  requestMonitorNotificationPermission,
  publishMonitorNotification,
} = await import('./monitorNotifications.js')

test('Android permission uses the native bridge even without browser notifications', async () => {
  window.CRTicketAndroid = {
    requestNotificationPermission(id) {
      queueMicrotask(() => window.crticketNotificationResult(id, {
        granted: true,
        message: '系统通知已开启',
      }))
    },
  }
  assert.equal((await requestMonitorNotificationPermission()).granted, true)
})

test('denied Android permission preserves its settings guidance', async () => {
  window.CRTicketAndroid = {
    requestNotificationPermission(id) {
      window.crticketNotificationResult(id, { granted: false, message: '请在系统设置中开启' })
    },
  }
  assert.deepEqual(await requestMonitorNotificationPermission(), {
    granted: false,
    message: '请在系统设置中开启',
  })
})

test('stopping while permission is pending discards a late grant', async () => {
  let pendingId
  window.CRTicketAndroid = {
    requestNotificationPermission(id) { pendingId = id },
  }
  const controller = new AbortController()
  const request = requestMonitorNotificationPermission(controller.signal)
  controller.abort()
  await assert.rejects(request, error => error.code === 'ERR_CANCELED')
  assert.doesNotThrow(() => window.crticketNotificationResult(pendingId, { granted: true }))
})

test('overlapping permission requests resolve only their matching callback', async () => {
  const ids = []
  window.CRTicketAndroid = {
    requestNotificationPermission(id) { ids.push(id) },
  }
  const first = requestMonitorNotificationPermission()
  const second = requestMonitorNotificationPermission()
  window.crticketNotificationResult(ids[1], { granted: false })
  window.crticketNotificationResult(ids[0], { granted: true })
  assert.equal((await first).granted, true)
  assert.equal((await second).granted, false)
})

test('ticket notices use Android delivery and report permission revocation', async () => {
  let sent
  window.CRTicketAndroid = {
    notifyTicketAvailability(id, title, body) {
      sent = { title, body }
      window.crticketNotificationResult(id, { success: false, message: '系统通知已关闭' })
    },
  }
  const result = await publishMonitorNotification(
    ['G1', 'G1', 'G2', 'G3', 'G4'],
    ['G1 二等座：有', 'G2 一等座：2', 'G3 二等座：1', 'G4 无座：5'],
  )
  assert.deepEqual(sent, {
    title: '余票提醒：G1、G2、G3 等 4 趟车',
    body: 'G1 二等座：有\nG2 一等座：2\nG3 二等座：1',
  })
  assert.equal(result.success, false)
  assert.equal(result.message, '系统通知已关闭')
})

test('native failures and old app versions return actionable permission results', async () => {
  window.CRTicketAndroid = {
    requestNotificationPermission() { throw new Error('bridge unavailable') },
  }
  assert.equal((await requestMonitorNotificationPermission()).granted, false)
  window.CRTicketAndroid = {}
  assert.match((await requestMonitorNotificationPermission()).message, /更新应用/)
})

test('browser fallback requests permission and publishes a clickable notification', async () => {
  delete window.CRTicketAndroid
  let sent
  let focused = false
  let closed = false
  window.Notification = class {
    static permission = 'default'
    static async requestPermission() {
      this.permission = 'granted'
      return this.permission
    }
    constructor(title, options) { sent = { title, options, notification: this } }
    close() { closed = true }
  }
  window.focus = () => { focused = true }
  assert.equal((await requestMonitorNotificationPermission()).granted, true)
  assert.equal((await publishMonitorNotification(['G1'], ['二等座：有'])).success, true)
  assert.equal(sent.title, '余票提醒：G1')
  assert.equal(sent.options.tag, 'cr-ticket-availability')
  sent.notification.onclick()
  assert.equal(focused, true)
  assert.equal(closed, true)
})

test('blocked and unsupported browsers keep page reminders available', async () => {
  window.Notification.permission = 'denied'
  assert.equal((await requestMonitorNotificationPermission()).granted, false)
  assert.equal((await publishMonitorNotification(['G1'], ['有票'])).success, false)
  delete window.Notification
  assert.match((await requestMonitorNotificationPermission()).message, /不支持系统通知/)
})
