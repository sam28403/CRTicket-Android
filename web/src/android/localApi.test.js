import test from 'node:test'
import assert from 'node:assert/strict'
import { createLocalApi } from './localApi.js'

function fixture() {
  const values = new Map()
  const storage = { getItem: key => values.get(key) ?? null, setItem: (key, value) => values.set(key, value) }
  return { storage, api: createLocalApi(storage) }
}
const ticket = {
  ticket_number: 'E351822734', train_no: 'G1', departure_station: '北京南', arrival_station: '上海虹桥',
  travel_date: '2026年10月10日', departure_time: '09:00', price: 553, use_credit: 0,
  seat_type: '二等座', has_conditioner: 1, seat_no: '01车01A号', sell_place: '北京', gate_info: 'A1',
  message: '测试车票', theme: 'Red.jpg', distance: 1318,
}

test('device history survives reload, preserves identity on edits and supports deletion', async () => {
  const { api, storage } = fixture()
  assert.equal((await api.request('GET', '/user/session')).user.id, 'device')
  await api.request('POST', '/ticket/add', ticket)
  const restored = createLocalApi(storage)
  const [saved] = await restored.request('GET', '/ticket/list/device')
  assert.equal(saved.train_no, 'G1')
  await restored.request('POST', `/ticket/update/${saved.id}`, { ...ticket, price: 600 })
  const [updated] = await restored.request('GET', '/ticket/list/device')
  assert.equal(updated.id, saved.id)
  assert.equal(updated.created_at, saved.created_at)
  assert.equal(updated.price, 600)
  await restored.request('DELETE', `/ticket/delete/${saved.id}`)
  assert.deepEqual(await restored.request('GET', '/ticket/list/device'), [])
})

test('backup deduplication, validation, statistics and calendar agree with saved records', async () => {
  const { api } = fixture()
  await api.request('POST', '/ticket/add', ticket)
  const imported = await api.request('POST', '/user/import-backup', {
    backup: [ticket, { ...ticket, ticket_number: 'E2' }, { ...ticket, distance: -1 }],
  })
  assert.deepEqual(imported.data, { imported: 1, skipped: 1, invalid: 1, total: 3 })
  const stats = await api.request('GET', '/user/statistics')
  assert.deepEqual(stats.data.departures, [{ station: '北京南', count: 2 }])
  const calendar = await api.request('GET', '/user/monthly-tickets?year=2026&month=10')
  assert.deepEqual(calendar.data.tickets, [{ travel_date: '2026年10月10日', count: 2 }])
  const history = await api.request('GET', '/user/ticket-history')
  assert.equal(history.data[0].departureStation, '北京南')
  await assert.rejects(api.request('POST', '/ticket/add', { ...ticket, price: -1 }), /字段格式/)
  assert.equal((await api.request('GET', '/ticket/list/device')).length, 2)
})

test('storage failure is surfaced without pretending the record was saved', async () => {
  const api = createLocalApi({ getItem: () => null, setItem: () => { throw new Error('QuotaExceededError') } })
  await assert.rejects(api.request('POST', '/ticket/add', ticket), /QuotaExceededError/)
})

test('renaming the device owner preserves identity, avatar and all ticket history after reload', async () => {
  const { api, storage } = fixture()
  await api.request('POST', '/ticket/add', ticket)
  await api.request('POST', '/user/update-avatar', { avatar: 'data:image/png;base64,AAAA' })
  const result = await api.request('POST', '/user/update-name', { username: '  铁路旅客  ' })
  assert.equal(result.user.username, '铁路旅客')
  assert.equal(result.user.id, 'device')
  const restored = createLocalApi(storage)
  assert.equal(restored.owner().username, '铁路旅客')
  assert.equal(restored.owner().avatar, 'data:image/png;base64,AAAA')
  assert.equal((await restored.request('GET', '/ticket/list/device'))[0].ticket_number, ticket.ticket_number)
  for (const username of ['', '   ', null, '甲\n乙', 'x'.repeat(65)]) {
    await assert.rejects(api.request('POST', '/user/update-name', { username }))
  }
  assert.equal(restored.owner().username, '铁路旅客')
})

test('railway routes belong to the phone network module; server identities cannot access device history', async () => {
  const { api } = fixture()
  await assert.rejects(api.request('GET', '/left-ticket'), /未知的本机/)
  await assert.rejects(api.request('GET', '/ticket/list/1'), /未知的本机/)
})
