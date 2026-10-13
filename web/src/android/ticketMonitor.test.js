import test from 'node:test'
import assert from 'node:assert/strict'

globalThis.window = {}
const {
  supportsTicketMonitoring,
  startTicketMonitor,
  getTicketMonitorStatus,
  stopTicketMonitor,
  updateTicketMonitor,
} = await import('./ticketMonitor.js')

const payload = {
  from: 'BJP', to: 'SHH', fromName: '北京', toName: '上海',
  date: '2026-10-13', interval: 30,
  filters: {
    trainFilters: ['G1'], seatFilters: ['second'], timeRange: [6, 12],
    highSpeedOnly: true, ordinaryOnly: false,
    selectedDepartureStations: ['北京南'], selectedArrivalStations: ['上海虹桥'],
  },
}

test('background ticket monitoring requires the complete native bridge', () => {
  assert.equal(supportsTicketMonitoring(), false)
  window.CRTicketAndroid = { startTicketMonitor() {} }
  assert.equal(supportsTicketMonitoring(), false)
  window.CRTicketAndroid = {
    startTicketMonitor() {}, ticketMonitorStatus() {},
    stopTicketMonitor() {}, updateTicketMonitor() {},
  }
  assert.equal(supportsTicketMonitoring(), true)
})

test('start, restoration, filter updates and explicit stop retain native state', async () => {
  let status = { success: true, active: false, config: null, trains: [], events: [] }
  const reply = id => window.crticketTicketMonitorResult(id, status)
  window.CRTicketAndroid = {
    startTicketMonitor(id, json) {
      status = { ...status, active: true, config: JSON.parse(json), loading: true }
      reply(id)
    },
    ticketMonitorStatus: reply,
    updateTicketMonitor(id, json) {
      status = { ...status, config: JSON.parse(json), events: [] }
      reply(id)
    },
    stopTicketMonitor(id) {
      status = { ...status, active: false }
      reply(id)
    },
  }
  assert.equal((await startTicketMonitor(payload)).active, true)
  assert.deepEqual((await getTicketMonitorStatus()).config, payload)
  const changed = { ...payload, filters: { ...payload.filters, seatFilters: ['first'] } }
  assert.deepEqual((await updateTicketMonitor(changed)).config.filters.seatFilters, ['first'])
  assert.equal((await stopTicketMonitor()).active, false)
})

test('a railway query failure preserves the active background monitor', async () => {
  window.CRTicketAndroid = {
    ticketMonitorStatus(id) {
      window.crticketTicketMonitorResult(id, {
        success: true, active: true, error: '查询超时', nextAt: '2026-10-13T01:00:30Z',
      })
    },
  }
  const status = await getTicketMonitorStatus()
  assert.equal(status.active, true)
  assert.equal(status.error, '查询超时')
})

test('detaching a pending status call never invokes the native stop command', async () => {
  let pendingId
  let stops = 0
  window.CRTicketAndroid = {
    ticketMonitorStatus(id) { pendingId = id },
    stopTicketMonitor() { stops++ },
  }
  const controller = new AbortController()
  const pending = getTicketMonitorStatus(controller.signal)
  controller.abort()
  await assert.rejects(pending, error => error.code === 'ERR_CANCELED')
  assert.doesNotThrow(() => window.crticketTicketMonitorResult(pendingId, { active: true }))
  assert.equal(stops, 0)
})

test('bridge errors remain actionable and overlapping calls use their own callback', async () => {
  const ids = []
  window.CRTicketAndroid = { ticketMonitorStatus(id) { ids.push(id) } }
  const first = getTicketMonitorStatus()
  const second = getTicketMonitorStatus()
  window.crticketTicketMonitorResult(ids[1], { success: true, active: true })
  window.crticketTicketMonitorResult(ids[0], { success: false, message: '后台服务启动失败' })
  assert.equal((await second).active, true)
  await assert.rejects(first, /后台服务启动失败/)
})
