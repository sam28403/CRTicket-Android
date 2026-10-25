import test from 'node:test'
import assert from 'node:assert/strict'
import { buildAppBackup, parseAppBackup } from '../utils/appBackup.js'
import { availableBackupContents, selectBackupContents } from '../utils/backupContents.js'
import { restoreDeviceBackup } from './deviceBackup.js'

const updates = { updatePreviews: true, updateAutomatic: false }
const source = (native = updates) => buildAppBackup([], { username: '备份用户' }, {
  theme: 'classic-system', storage: { getItem: () => null }, native,
})

test('update choices round trip in settings and are excluded from other categories', async () => {
  const exported = selectBackupContents(source(), ['settings'])
  assert.deepEqual(Object.fromEntries(Object.keys(updates)
    .map(key => [key, exported.settings.native[key]])), updates)
  const imported = parseAppBackup(JSON.stringify(exported))
  let restored
  await restoreDeviceBackup(imported, {
    storage: { getItem: () => null, setItem: () => {}, removeItem: () => {} },
    validateNative: () => {},
    restoreNative: async value => { restored = value },
    applyTheme: () => {}, updateUser: () => assert.fail('profile was not selected'),
  })
  assert.deepEqual(restored, exported.settings.native)
  for (const category of ['tickets', 'profile']) {
    const selection = selectBackupContents(source(), [category])
    assert.equal(selection.settings.native, undefined)
  }
  assert.equal(selectBackupContents(imported, ['settings']).settings.native.updateAutomatic, false)
})

test('a backup containing only update choices exposes the settings category', () => {
  for (const key of Object.keys(updates)) {
    const backup = { app: 'CRTicket', schemaVersion: 2, tickets: [], contents: ['settings'],
      settings: { native: { [key]: updates[key] } } }
    assert.deepEqual(availableBackupContents(parseAppBackup(JSON.stringify(backup))), ['settings'])
  }
})

test('older backups omit update choices without inventing defaults', () => {
  const older = parseAppBackup(JSON.stringify(source({ importantNoticeSuppressed: true })))
  assert.equal(Object.hasOwn(older.settings.native, 'updatePreviews'), false)
  assert.equal(Object.hasOwn(older.settings.native, 'updateAutomatic'), false)
})

test('update preferences accept booleans only, including explicit false', () => {
  for (const key of Object.keys(updates)) {
    for (const invalid of [null, 'true', 'false', 0, 1, [], {}]) {
      assert.throws(() => parseAppBackup(JSON.stringify({
        app: 'CRTicket', schemaVersion: 2, tickets: [], settings: { native: { [key]: invalid } },
      })), /备份设置格式无效/)
    }
    assert.equal(source({ [key]: false }).settings.native[key], false)
  }
})
