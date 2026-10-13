import test from 'node:test'
import assert from 'node:assert/strict'
import { createTicketMonitorConnection } from './ticketMonitorConnection.js'

function fixture(overrides = {}) {
  let nextId = 0
  const timers = new Map()
  const statuses = []
  const errors = []
  let stops = 0
  const active = { success: true, active: true, config: { fromName: '北京' } }
  const connection = createTicketMonitorConnection({
    readStatus: async () => active,
    start: async () => active,
    update: async () => active,
    stop: async () => { stops++; return { success: true, active: false } },
    onStatus: (status, reason) => statuses.push({ status, reason }),
    onError: error => errors.push(error.message),
    schedule: (callback, delay) => {
      const id = ++nextId
      timers.set(id, { callback, delay })
      return id
    },
    cancel: id => timers.delete(id),
    ...overrides,
  })
  async function tick() {
    const [id, timer] = timers.entries().next().value
    timers.delete(id)
    await timer.callback()
  }
  return { connection, timers, statuses, errors, tick, stops: () => stops }
}

test('reopening the page restores service configuration and polls current results', async () => {
  const f = fixture()
  await f.connection.restore()
  assert.equal(f.statuses[0].reason, 'restore')
  assert.equal(f.statuses[0].status.config.fromName, '北京')
  assert.equal([...f.timers.values()][0].delay, 1500)
  await f.tick()
  assert.equal(f.statuses[1].reason, 'status')
  f.connection.detach()
})

test('leaving the page clears polling while the native service remains running', async () => {
  const f = fixture()
  await f.connection.start({ date: '2026-10-13' })
  f.connection.detach()
  assert.equal(f.timers.size, 0)
  assert.equal(f.stops(), 0)
})

test('a late status response after page disposal cannot alter the detached page', async () => {
  let finish
  let signal
  const f = fixture({
    readStatus: value => {
      signal = value
      return new Promise(resolve => { finish = resolve })
    },
  })
  const restore = f.connection.restore()
  f.connection.detach()
  finish({ success: true, active: true })
  await restore
  assert.equal(signal.aborted, true)
  assert.equal(f.statuses.length, 0)
  assert.equal(f.timers.size, 0)
  assert.equal(f.stops(), 0)
})

test('temporary bridge failures retry without stopping the background service', async () => {
  let reads = 0
  const f = fixture({
    readStatus: async () => {
      if (++reads === 1) throw new Error('状态查询超时')
      return {
        success: true, active: true,
        config: { fromName: '北京', filters: { trainFilters: ['G1'] } },
        error: '铁路服务超时',
      }
    },
  })
  await f.connection.restore()
  assert.deepEqual(f.errors, ['状态查询超时'])
  await f.tick()
  assert.equal(f.statuses[0].reason, 'restore')
  assert.equal(f.statuses[0].status.config.fromName, '北京')
  assert.equal(f.statuses[0].status.active, true)
  assert.equal(f.timers.size, 1)
  assert.equal(f.stops(), 0)
  f.connection.detach()
})

test('editing a query after failed restoration cancels retries before stopping the old session', async () => {
  const f = fixture({ readStatus: async () => { throw new Error('状态查询超时') } })
  await f.connection.restore()
  assert.equal(f.timers.size, 1)
  await f.connection.stop()
  assert.equal(f.stops(), 1)
  assert.equal(f.timers.size, 0)
  assert.equal(f.statuses[0].reason, 'stop')
})

test('only an explicit user stop stops native monitoring and ends status polling', async () => {
  const f = fixture()
  await f.connection.restore()
  await f.connection.stop()
  assert.equal(f.stops(), 1)
  assert.equal(f.statuses.at(-1).reason, 'stop')
  assert.equal(f.statuses.at(-1).status.active, false)
  assert.equal(f.timers.size, 0)
})

test('updating filters discards an older pending status response', async () => {
  let finish
  const f = fixture({
    readStatus: () => new Promise(resolve => { finish = resolve }),
    update: async payload => ({ success: true, active: true, config: payload, events: [] }),
  })
  const restore = f.connection.restore()
  await f.connection.update({ filters: { seatFilters: ['first'] } })
  finish({ success: true, active: true, events: [{ text: '旧筛选提醒' }] })
  await restore
  assert.equal(f.statuses.length, 1)
  assert.equal(f.statuses[0].reason, 'update')
  assert.deepEqual(f.statuses[0].status.events, [])
  f.connection.detach()
})
