import test from 'node:test'
import assert from 'node:assert/strict'
import { generateDebugTickets } from './debugGeneration.js'
import { createLocalApi } from './localApi.js'
import { trainQueryRange } from '../utils/trainQueryDate.js'

const date = trainQueryRange().today
const sample = async () => ({ sample: {
  trainNo: 'G1', from: '北京南', to: '上海虹桥', date, time: '09:00', distance: 1318,
  fares: [{ seatType: '二等座', price: 553 }],
} })
function fixture() {
  const values = new Map()
  const storage = { getItem: key => values.get(key) ?? null,
    setItem: (key, value) => values.set(key, value) }
  return { storage, api: createLocalApi(storage) }
}
test('generated batch persists with unique identities and real interval fares', async () => {
  const { api, storage } = fixture()
  const result = await generateDebugTickets(date, 12, { sample, api })
  assert.equal(result.success, true)
  assert.equal(result.tickets.length, 12)
  assert.equal(new Set(result.tickets.map(row => row.id)).size, 12)
  const saved = await createLocalApi(storage).request('GET', '/ticket/list/device')
  assert.equal(saved.length, 12)
  assert.ok(saved.every(row => row.price === 553 && row.distance === 1318 && row.train_no === 'G1'))
})
test('invalid input and absent samples leave history intact', async () => {
  const { api } = fixture()
  for (const count of [0, 1001, 1.5, NaN]) {
    await assert.rejects(generateDebugTickets(date, count, { sample, api }))
  }
  await assert.rejects(generateDebugTickets(trainQueryRange().max, 1, { sample, api }))
  await assert.rejects(generateDebugTickets(date, 1, { sample: async () => ({}), api }))
  assert.equal((await api.request('GET', '/ticket/list/device')).length, 0)
})
test('batch validation is atomic and disk failures propagate', async () => {
  const { api } = fixture()
  const generated = await generateDebugTickets(date, 1, { sample, api })
  const row = generated.tickets[0]
  await assert.rejects(api.request('POST', '/ticket/add-batch', {
    tickets: [row, { ...row, price: -1 }],
  }))
  assert.equal((await api.request('GET', '/ticket/list/device')).length, 1)
  const failing = createLocalApi({ getItem: () => null, setItem: () => { throw Error('disk full') } })
  await assert.rejects(generateDebugTickets(date, 1, { sample, api: failing }), /disk full/)
})
