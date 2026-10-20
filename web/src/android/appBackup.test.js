import test from 'node:test'
import assert from 'node:assert/strict'
import { buildAppBackup, parseAppBackup } from '../utils/appBackup.js'
import { restoreDeviceBackup } from './deviceBackup.js'
import { createLocalApi } from './localApi.js'

const ticket = {
  ticket_number: 'E888888', train_no: 'G8888', departure_station: '北京南', arrival_station: '上海虹桥',
  travel_date: '2026年10月10日', departure_time: '09:00', price: 0, use_credit: 0,
  seat_type: '二等座', has_conditioner: 1, seat_no: '08车08A号', sell_place: '北京',
  gate_info: 'A1', message: '完整备份测试', theme: 'Red.jpg', distance: 1318,
  created_at: '2026-01-02T03:04:05.000Z',
}
const profile = { id: 'device', username: '备份用户', avatar: 'data:image/png;base64,YQ==' }
const preferences = {
  language: 'en', arrivalEnabled: true, importantNoticeSuppressed: true,
  monitorConfig: {
    from: 'VNP', to: 'AOH', fromName: '北京南', toName: '上海虹桥',
    date: '2026-10-10', interval: 60, filters: { trainFilters: ['G1'], timeRange: [0, 24] },
  },
}

function fixture(initial = {}) {
  const values = new Map(Object.entries(initial))
  const storage = {
    getItem: key => values.get(key) ?? null,
    setItem: (key, value) => values.set(key, value),
    removeItem: key => values.delete(key),
  }
  let native = {}
  let user
  let theme
  return {
    values, storage,
    options: {
      storage,
      validateNative: () => {},
      restoreNative: async value => { native = value },
      applyTheme: value => { theme = value; storage.setItem('crticket-theme', value) },
      updateUser: value => {
        user = value
        for (const key of ['user', 'crticket-default-owner:device']) {
          storage.setItem(key, JSON.stringify(value))
        }
        storage.setItem('login', 'true')
      },
    },
    result: () => ({ native, user, theme }),
  }
}

function backup(records = [ticket]) {
  const source = fixture({ 'crticket-train-query-history': '["G1"]' })
  return parseAppBackup(JSON.stringify(buildAppBackup(records, profile, {
    theme: 'contrast-dark', storage: source.storage, native: preferences,
  })))
}

test('full backup restores settings, zero-price tickets and creation times without copying identities', async () => {
  const target = fixture({ 'unrelated-cache': 'untouched' })
  assert.deepEqual(await restoreDeviceBackup(backup(), target.options), {
    imported: 1, skipped: 0, invalid: 0,
  })
  assert.deepEqual(target.result(), { native: preferences, user: profile, theme: 'contrast-dark' })
  const local = createLocalApi(target.storage)
  const [saved] = await local.request('GET', '/ticket/list/device')
  assert.equal(saved.created_at, ticket.created_at)
  assert.equal(saved.user_id, 'device')
  assert.equal(saved.price, 0)
  assert.ok(saved.id)
  assert.equal(target.storage.getItem('crticket-train-query-history'), '["G1"]')
  assert.equal(target.storage.getItem('unrelated-cache'), 'untouched')
  assert.deepEqual(await restoreDeviceBackup(backup(), target.options), {
    imported: 0, skipped: 1, invalid: 0,
  })
  assert.equal((await local.request('GET', '/ticket/list/device'))[0].id, saved.id)
})

test('empty-ticket full backups restore settings, while legacy backups remain ticket-only', async () => {
  const target = fixture()
  await restoreDeviceBackup(backup([]), target.options)
  assert.deepEqual(target.result().native, preferences)
  for (const value of [[ticket], { schemaVersion: 1, tickets: [ticket] }, { tickets: [] }]) {
    const parsed = parseAppBackup(JSON.stringify(value))
    assert.equal(parsed.settings, null)
  }
})

test('full export preserves nullable ticket fields so a round trip cannot create duplicates', async () => {
  const existing = { ...ticket, id: 'existing', use_credit: null, has_conditioner: null, distance: null }
  const target = fixture({ 'crticket-device-tickets-v1': JSON.stringify([existing]) })
  const value = backup([existing])
  assert.equal(value.tickets[0].distance, null)
  assert.equal(value.tickets[0].use_credit, null)
  assert.equal(value.tickets[0].has_conditioner, null)
  assert.deepEqual(await restoreDeviceBackup(value, target.options), {
    imported: 0, skipped: 1, invalid: 0,
  })
  assert.equal(JSON.parse(target.storage.getItem('crticket-device-tickets-v1')).length, 1)
})

test('invalid rows are counted while duplicate and existing records retain their identity', async () => {
  const existing = { ...ticket, id: 'existing', user_id: 'device' }
  const target = fixture({ 'crticket-device-tickets-v1': JSON.stringify([existing]) })
  const value = backup()
  value.tickets = [ticket, { ...ticket, ticket_number: 'new' }, null, { ...ticket, distance: -1 }]
  assert.deepEqual(await restoreDeviceBackup(value, target.options), {
    imported: 1, skipped: 1, invalid: 2,
  })
  const rows = JSON.parse(target.storage.getItem('crticket-device-tickets-v1'))
  assert.deepEqual(rows[0], existing)
  assert.equal(rows.length, 2)
})

test('unsupported schemas and malformed settings fail before any restore', () => {
  for (const change of [
    { schemaVersion: 3 }, { app: 'Other' }, { settings: null },
    { settings: { theme: 'invalid' } }, { settings: { profile: { username: '\n', avatar: null } } },
    { settings: { native: { arrivalEnabled: 'true' } } },
    { settings: { storage: { 'crticket-train-query-history': '[123]' } } },
  ]) {
    assert.throws(() => parseAppBackup(JSON.stringify({
      app: 'CRTicket', schemaVersion: 2, tickets: [], settings: {}, ...change,
    })))
  }
})

test('native validation and ticket quota failures leave all data unchanged', async () => {
  const target = fixture({ 'user': 'original', 'crticket-theme': 'classic-light' })
  const original = new Map(target.values)
  await assert.rejects(restoreDeviceBackup(backup(), {
    ...target.options, validateNative: () => { throw new Error('Invalid language') },
  }), /Invalid language/)
  assert.deepEqual(target.values, original)
  target.storage.setItem('crticket-device-tickets-v1', JSON.stringify(
    Array.from({ length: 10000 }, (_, index) => ({ ...ticket, ticket_number: String(index) })),
  ))
  const full = new Map(target.values)
  await assert.rejects(restoreDeviceBackup(backup(), target.options), /10000/)
  assert.deepEqual(target.values, full)
})

test('storage failures and native commit failures roll back tickets, profile and settings', async () => {
  for (const failure of ['storage', 'native']) {
    const target = fixture({
      'crticket-device-owner-v1': JSON.stringify({ id: 'device', username: '原用户', avatar: null }),
      'crticket-device-tickets-v1': '[]', 'crticket-theme': 'classic-light',
      'crticket-train-query-history': '["G2"]', 'unrelated-cache': 'keep',
    })
    const original = new Map(target.values)
    if (failure === 'storage') {
      let fail = true
      const set = target.storage.setItem
      target.storage.setItem = (key, value) => {
        if (key === 'crticket-train-query-history' && fail) {
          fail = false
          throw new Error('QuotaExceededError')
        }
        return set(key, value)
      }
    } else target.options.restoreNative = async () => { throw new Error('Native commit failed') }
    await assert.rejects(restoreDeviceBackup(backup(), target.options))
    assert.deepEqual(target.values, original)
    assert.equal(target.result().theme, 'classic-light')
    assert.equal(target.result().user.username, '原用户')
  }
})

test('backup import cannot overwrite unrelated keys or inject a different device identity', async () => {
  const target = fixture({ token: 'original', 'crticket-generator-receipt': 'keep' })
  const value = backup([])
  value.settings.storage.token = 'injected'
  value.settings.storage['crticket-generator-receipt'] = 'injected'
  value.settings.profile.id = 'remote-account'
  await restoreDeviceBackup(value, target.options)
  assert.equal(target.storage.getItem('token'), 'original')
  assert.equal(target.storage.getItem('crticket-generator-receipt'), 'keep')
  assert.equal(target.result().user.id, 'device')
})
