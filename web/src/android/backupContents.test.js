import test from 'node:test'
import assert from 'node:assert/strict'
import { buildAppBackup, parseAppBackup } from '../utils/appBackup.js'
import { BACKUP_CONTENTS, availableBackupContents, selectBackupContents } from '../utils/backupContents.js'
import { restoreDeviceBackup } from './deviceBackup.js'

const profile = { id: 'device', username: '备份用户', avatar: null }
const native = {
  language: 'en', arrivalEnabled: false, importantNoticeSuppressed: true,
  railfanToolsEnabled: true, vehicleSearchHistory: ['G1'], stationVisits: [],
  speedRecordings: [], generatorDraft: null, monitorConfig: null,
}
const values = {
  'crticket-train-query-history': '["G1"]',
  'crticket-station-query-history': '["北京南"]',
  'crticket-important-notice-hidden': '1',
  'crticket-generator-draft': null,
}
const options = { theme: 'contrast-dark', native, storage: { getItem: key => values[key] ?? null } }
const source = () => buildAppBackup([], profile, options)

test('default export selects every available category and preserves schema 2', () => {
  const backup = source()
  assert.equal(backup.schemaVersion, 2)
  assert.deepEqual(backup.contents, BACKUP_CONTENTS.map(item => item.id))
  assert.deepEqual(availableBackupContents(parseAppBackup(JSON.stringify(backup))), backup.contents)
})

test('every single-category export omits all unrelated fields and personal metadata', () => {
  const expected = {
    tickets: {},
    search: { storage: {
      'crticket-train-query-history': '["G1"]', 'crticket-station-query-history': '["北京南"]',
    }, native: { vehicleSearchHistory: ['G1'] } },
    settings: { theme: 'contrast-dark', storage: { 'crticket-important-notice-hidden': '1' },
      native: { language: 'en', arrivalEnabled: false, importantNoticeSuppressed: true, railfanToolsEnabled: true } },
    profile: { profile: { username: profile.username, avatar: null } },
    visits: { native: { stationVisits: [] } },
    speed: { native: { speedRecordings: [] } },
    draft: { storage: { 'crticket-generator-draft': null }, native: { generatorDraft: null } },
    monitor: { native: { monitorConfig: null } },
  }
  for (const { id } of BACKUP_CONTENTS) {
    const backup = buildAppBackup([], profile, { ...options, contents: [id] })
    assert.deepEqual(backup.settings, expected[id], id)
    assert.deepEqual(backup.contents, [id])
    assert.deepEqual(availableBackupContents(parseAppBackup(JSON.stringify(backup))), [id])
    if (id !== 'profile') assert.equal(backup.user, null)
  }
})

test('partial imports preserve unrelated storage and do not invoke profile or theme updates', async () => {
  const original = {
    'crticket-device-tickets-v1': '[{"id":"existing-ticket"}]',
    'crticket-device-owner-v1': '{"id":"device","username":"本机用户"}',
    'user': 'keep-user', 'login': 'keep-login', 'crticket-theme': 'classic-light',
    'crticket-generator-draft': '{"trainNo":"G2"}', 'crticket-generator-receipt': 'keep-receipt',
  }
  const target = new Map(Object.entries(original))
  const payload = selectBackupContents(parseAppBackup(JSON.stringify(source())), ['search', 'visits'])
  let restored
  await restoreDeviceBackup(payload, {
    storage: {
      getItem: key => target.get(key) ?? null,
      setItem: (key, value) => target.set(key, value), removeItem: key => target.delete(key),
    },
    validateNative: () => {}, restoreNative: async value => { restored = value },
    applyTheme: () => assert.fail('unselected theme changed'),
    updateUser: () => assert.fail('unselected profile changed'),
  })
  for (const [key, value] of Object.entries(original)) assert.equal(target.get(key), value, key)
  assert.deepEqual(restored, { vehicleSearchHistory: ['G1'], stationVisits: [] })
  assert.equal(target.get('crticket-train-query-history'), '["G1"]')
})

test('legacy and older full backups expose only fields present in the file', () => {
  for (const value of [[], { tickets: [] }, { schemaVersion: 1, tickets: [] }]) {
    const backup = parseAppBackup(JSON.stringify(value))
    assert.deepEqual(availableBackupContents(backup), ['tickets'])
    assert.equal(selectBackupContents(backup).settings, null)
  }
  const older = { app: 'CRTicket', schemaVersion: 2, tickets: [], settings: { theme: 'classic-system' } }
  assert.deepEqual(availableBackupContents(parseAppBackup(JSON.stringify(older))), ['tickets', 'settings'])
  const partial = selectBackupContents(source(), ['search'])
  assert.throws(() => selectBackupContents(partial, ['tickets']))
  assert.throws(() => selectBackupContents(source(), []))
  assert.throws(() => selectBackupContents(source(), ['unknown']))
  assert.throws(() => parseAppBackup(JSON.stringify({ ...source(), contents: ['unknown'] })))
})

test('ticket selection and combined categories survive export and import selection independently', () => {
  const ticket = { ticket_number: 'E1', train_no: 'G1' }
  const full = buildAppBackup([ticket], profile, options)
  const exported = buildAppBackup([ticket], profile, { ...options, contents: ['search', 'draft'] })
  assert.deepEqual(exported.tickets, [])
  assert.deepEqual(exported.contents, ['search', 'draft'])
  assert.equal(exported.settings.native.generatorDraft, null)
  assert.equal(exported.settings.native.language, undefined)
  assert.equal(exported.settings.profile, undefined)
  const imported = selectBackupContents(parseAppBackup(JSON.stringify(full)), ['tickets', 'profile'])
  assert.equal(imported.tickets[0].ticket_number, 'E1')
  assert.deepEqual(Object.keys(imported.settings), ['profile'])
  const ticketsOnly = selectBackupContents(parseAppBackup(JSON.stringify(full)), ['tickets'])
  assert.deepEqual(ticketsOnly.settings, {})
  assert.deepEqual(selectBackupContents(full, ['settings']).tickets, [])
})
