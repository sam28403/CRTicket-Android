import test from 'node:test'
import assert from 'node:assert/strict'
import { createGeneratorSaveFlow } from './generatorSaveFlow.js'
import { validateHistoryTicket } from './historyValidation.js'

const ticket = {
  number: 'CODEX_GENERATOR_TEST', trainNo: 'G25', from: '北京南', to: '上海虹桥',
  date: '2026年10月18日', time: '08:00', price: '0', seatType: '二等座',
  seatNo: '03车12A号', distance: '1318',
}

function fixture(overrides = {}) {
  const calls = { stored: 0, cleared: 0, formats: [] }
  let receipt = ''
  const save = createGeneratorSaveFlow({
    validate: validateHistoryTicket,
    store: async () => { calls.stored++; return { success: true } },
    exportFile: async format => { calls.formats.push(format); return true },
    readReceipt: () => receipt,
    writeReceipt: value => { receipt = value },
    clear: () => { calls.cleared++ },
    ...overrides,
  })
  return { save, calls }
}

test('PDF and image saves insert the ticket once and clear after successful exports, including zero fares', async () => {
  const { save, calls } = fixture()
  await save('pdf', ticket, { id: 'device' })
  await save('image', { ...ticket }, { id: 'device' })
  assert.equal(calls.stored, 1)
  assert.equal(calls.cleared, 2)
  assert.deepEqual(calls.formats, ['pdf', 'image'])
})

test('cancelled file picker preserves draft and receipt for another format', async () => {
  let attempts = 0
  const { save, calls } = fixture({ exportFile: async () => ++attempts > 1 })
  const result = await save('pdf', ticket, { id: 'device' })
  assert.equal(result.exported, false)
  assert.equal(calls.cleared, 0)
  await save('image', ticket, { id: 'device' })
  assert.equal(calls.stored, 1)
  assert.equal(calls.cleared, 1)
})

test('export failure preserves receipt and draft; retry does not insert again', async () => {
  let fail = true
  const { save, calls } = fixture({ exportFile: async () => {
    if (fail) throw new Error('disk full')
    return true
  } })
  await assert.rejects(save('pdf', ticket, { id: 'device' }), /disk full/)
  assert.equal(calls.cleared, 0)
  fail = false
  await save('pdf', ticket, { id: 'device' })
  assert.equal(calls.stored, 1)
  assert.equal(calls.cleared, 1)
})

test('invalid input or failed account storage cannot export or clear a draft', async () => {
  const { save, calls } = fixture()
  await assert.rejects(save('pdf', { ...ticket, trainNo: 'invalid' }, { id: 'device' }))
  assert.equal(calls.stored, 0)
  assert.equal(calls.cleared, 0)
  const failed = fixture({ store: async () => ({ success: false, message: 'storage full' }) })
  await assert.rejects(failed.save('image', ticket, { id: 'device' }), /storage full/)
  assert.deepEqual(failed.calls.formats, [])
  assert.equal(failed.calls.cleared, 0)
})

test('a changed ticket or a different account receives its own stored record', async () => {
  const { save, calls } = fixture()
  await save('pdf', ticket, { id: 'device' })
  await save('pdf', { ...ticket, seatNo: '03车12B号' }, { id: 'device' })
  await save('image', { ...ticket, seatNo: '03车12B号' }, { id: 'other' })
  assert.equal(calls.stored, 3)
})

test('concurrent save taps cannot start a second insert or export', async () => {
  let finish
  const { save, calls } = fixture({ exportFile: () => new Promise(resolve => { finish = resolve }) })
  const first = save('pdf', ticket, { id: 'device' })
  await new Promise(resolve => setImmediate(resolve))
  assert.deepEqual(await save('image', ticket, { id: 'device' }), { busy: true })
  finish(true)
  await first
  assert.equal(calls.stored, 1)
  assert.equal(calls.cleared, 1)
})
