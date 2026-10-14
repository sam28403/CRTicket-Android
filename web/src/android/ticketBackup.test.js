import test from 'node:test'
import assert from 'node:assert/strict'
import { createLocalApi } from './localApi.js'
import { buildBackupPayload, importTicketBackup, parseTicketBackup } from '../utils/ticketBackup.js'

const ticket = {
  ticket_number: 'E888888', train_no: 'G8888', departure_station: '北京南', arrival_station: '上海虹桥',
  travel_date: '2026年10月10日', departure_time: '09:00', price: 553, use_credit: 0,
  seat_type: '二等座', has_conditioner: 1, seat_no: '08车08A号', sell_place: '北京',
  gate_info: 'A1', message: '备份迁移测试', theme: 'Red.jpg', distance: 1318,
}

test('settings backup round trip preserves records, skips duplicates and rejects invalid rows', async () => {
  const values = new Map()
  const local = createLocalApi({ getItem: key => values.get(key), setItem: (key, value) => values.set(key, value) })
  const api = { post: async (path, data) => ({ data: await local.request('POST', path, data) }) }
  const user = local.owner()
  const payload = buildBackupPayload([ticket], user)
  const records = parseTicketBackup(JSON.stringify(payload))
  assert.equal(records[0].train_no, 'G8888')
  let message = await importTicketBackup([...records, ...records, null], [], user, api)
  assert.match(message, /新增 1 条，略过 1 条，未处理 1 条/)
  const saved = await local.request('GET', '/ticket/list/device')
  assert.equal(saved.length, 1)
  assert.equal(saved[0].distance, 1318)
  message = await importTicketBackup(records, saved, user, api)
  assert.match(message, /新增 0 条，略过 1 条/)
  assert.equal((await local.request('GET', '/ticket/list/device')).length, 1)
})

test('backup parsing supports legacy arrays and rejects invalid or oversized structures', () => {
  assert.deepEqual(parseTicketBackup(JSON.stringify([ticket])), [ticket])
  assert.throws(() => parseTicketBackup('{"user":{}}'), /没有找到/)
  assert.throws(() => parseTicketBackup('not json'))
  assert.throws(() => parseTicketBackup(JSON.stringify(Array(10001).fill(ticket))), /10000/)
})
