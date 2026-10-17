import test from 'node:test'
import assert from 'node:assert/strict'
import { createBrowserTicketMonitor } from '../composables/browserTicketMonitor.js'

function fixture(get) {
  const ref = value => ({ value })
  const state = {
    interval: ref(10), monitoring: ref(false), systemNotifications: ref(true),
    loading: ref(false), rows: ref([]), queriedAt: ref(''), lastMonitorAt: ref(''), error: ref(''),
    nextAt: ref(''), events: ref([]),
  }
  const timers = new Map()
  const notifications = []
  let nextId = 0
  const browser = createBrowserTicketMonitor({
    api: { get }, state, matches: () => true,
    watchedColumns: ref([['second', '二等座']]),
    hasTicket: row => row.canBuy && Number(row.seats.second) > 0,
    publishNotification: async (...args) => {
      notifications.push(args)
      return { success: true }
    },
    message: () => {},
    schedule: (callback, delay) => {
      const id = ++nextId
      timers.set(id, { callback, delay })
      return id
    },
    cancel: id => timers.delete(id),
  })
  async function tick() {
    const [id, timer] = timers.entries().next().value
    timers.delete(id)
    await timer.callback()
  }
  return { browser, state, timers, notifications, tick }
}

const params = { from: 'BJP', to: 'SHH', date: '2026-10-13' }
const data = {
  success: true,
  queriedAt: '2026-10-13T01:00:00Z',
  trains: [{
    id: 'G1', train: 'G1', from: '北京南', to: '上海虹桥',
    canBuy: true, seats: { second: '1' },
  }],
}

test('browser monitoring retries failed queries and recovers without manual restart', async () => {
  let attempts = 0
  const f = fixture(async () => {
    if (++attempts === 1) throw Object.assign(new Error(), { code: 'ECONNABORTED' })
    return { data }
  })
  await f.browser.query(params, true)
  assert.equal(f.state.monitoring.value, true)
  assert.match(f.state.error.value, /查询超时/)
  assert.equal([...f.timers.values()][0].delay, 10000)
  await f.tick()
  assert.equal(f.state.error.value, '')
  assert.equal(f.state.rows.value[0].train, 'G1')
  assert.equal(f.notifications.length, 1)
  f.browser.stop()
})

test('unchanged tickets are deduplicated and filter updates reset reminders', async () => {
  const f = fixture(async () => ({ data }))
  await f.browser.query(params, true)
  await f.tick()
  assert.equal(f.state.events.value.length, 1)
  assert.equal(f.notifications.length, 1)
  f.browser.resetReminders()
  await f.tick()
  assert.equal(f.state.events.value.length, 1)
  assert.equal(f.notifications.length, 2)
  f.browser.stop()
})

test('explicit browser stop discards pending results and clears future queries', async () => {
  let finish
  let signal
  const f = fixture((path, options) => {
    signal = options.signal
    return new Promise(resolve => { finish = resolve })
  })
  const query = f.browser.query(params, true)
  f.browser.stop()
  finish({ data })
  await query
  assert.equal(signal.aborted, true)
  assert.equal(f.state.monitoring.value, false)
  assert.deepEqual(f.state.rows.value, [])
  assert.equal(f.timers.size, 0)
  assert.equal(f.notifications.length, 0)
})

test('one-off ticket queries remain available without starting monitoring', async () => {
  const f = fixture(async () => ({ data }))
  await f.browser.query(params)
  assert.equal(f.state.monitoring.value, false)
  assert.equal(f.state.rows.value.length, 1)
  assert.equal(f.timers.size, 0)
  assert.equal(f.notifications.length, 0)
})


test('only successful monitor queries advance the last monitor success time', async () => {
  let response = data
  const f = fixture(async () => ({ data: response }))
  await f.browser.query(params)
  assert.equal(f.state.queriedAt.value, data.queriedAt)
  assert.equal(f.state.lastMonitorAt.value, '')
  await f.browser.query(params, true)
  assert.equal(f.state.lastMonitorAt.value, data.queriedAt)
  response = { ...data, queriedAt: '2026-10-13T01:01:00Z' }
  await f.browser.query(params)
  assert.equal(f.state.monitoring.value, true)
  assert.equal(f.state.lastMonitorAt.value, response.queriedAt)
  f.browser.stop()
  response = { ...data, queriedAt: '2026-10-13T01:02:00Z' }
  await f.browser.query(params)
  assert.equal(f.state.lastMonitorAt.value, '2026-10-13T01:01:00Z')
})
