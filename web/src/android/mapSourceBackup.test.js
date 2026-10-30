import test from 'node:test'
import assert from 'node:assert/strict'
import { buildAppBackup, parseAppBackup, validateAppSettings } from '../utils/appBackup.js'
import { availableBackupContents, selectBackupContents } from '../utils/backupContents.js'
import { mapSources } from '../utils/maps/mapSources.js'
import { restoreDeviceBackup } from './deviceBackup.js'

const user = { username: '地图备份验证', avatar: null }
function storage(initial = {}) {
  const values = new Map(Object.entries(initial))
  return {
    getItem: key => values.get(key) ?? null,
    setItem: (key, value) => values.set(key, value),
    removeItem: key => values.delete(key),
    values,
  }
}
const exported = id => buildAppBackup([], user, {
  storage: storage({ mapSource: 'osm' }), native: { mapSource: id }, contents: ['settings'],
})

test('every map choice survives settings export, JSON parsing and native/browser restore', async () => {
  for (const { id } of mapSources) {
    const backup = parseAppBackup(JSON.stringify(exported(id)))
    assert.deepEqual(availableBackupContents(backup), ['settings'])
    assert.equal(backup.settings.native.mapSource, id)
    assert.equal(backup.settings.storage.mapSource, id)
    const target = storage({ mapSource: 'topo' })
    let native
    await restoreDeviceBackup(backup, {
      storage: target, validateNative: () => {},
      restoreNative: async value => { native = value },
      applyTheme: () => assert.fail('theme changed'), updateUser: () => assert.fail('profile changed'),
    })
    assert.equal(target.getItem('mapSource'), id)
    assert.equal(native.mapSource, id)
  }
})

test('native-only and browser-only map backups are portable; unset browser preference means automatic', () => {
  for (const settings of [
    { native: { mapSource: 'transport' } },
    { storage: { mapSource: 'transport' } },
  ]) {
    const value = validateAppSettings(settings)
    assert.equal(value.native.mapSource, 'transport')
    assert.equal(value.storage.mapSource, 'transport')
  }
  assert.equal(validateAppSettings({ storage: { mapSource: null } }).native.mapSource, 'auto')
  assert.deepEqual(validateAppSettings({ native: { arrivalEnabled: false } }),
    { native: { arrivalEnabled: false } })
})

test('unselected settings and older backups preserve the current map source', async () => {
  const full = buildAppBackup([], user, { storage: storage(), native: { mapSource: 'france' } })
  const ticketsOnly = selectBackupContents(parseAppBackup(JSON.stringify(full)), ['tickets'])
  assert.equal(ticketsOnly.settings.native, undefined)
  const old = parseAppBackup(JSON.stringify({
    app: 'CRTicket', schemaVersion: 2, tickets: [], settings: { native: { arrivalEnabled: false } },
  }))
  for (const backup of [ticketsOnly, old]) {
    const target = storage({ mapSource: 'hot' })
    await restoreDeviceBackup(backup, {
      storage: target, validateNative: () => {},
      restoreNative: async value => assert.equal(value.mapSource, undefined),
      applyTheme: () => {}, updateUser: () => {},
    })
    assert.equal(target.getItem('mapSource'), 'hot')
  }
})

test('invalid source ids fail validation before writes and failed native restoration rolls storage back', async () => {
  for (const value of ['', 'unknown', 'https://example.com/tiles', 1, true, {}, []]) {
    assert.throws(() => validateAppSettings({ native: { mapSource: value } }))
    assert.throws(() => validateAppSettings({ storage: { mapSource: value } }))
  }
  assert.throws(() => validateAppSettings({ native: { mapSource: null } }))
  const target = storage({ mapSource: 'osm' })
  await assert.rejects(restoreDeviceBackup(parseAppBackup(JSON.stringify(exported('hot'))), {
    storage: target, validateNative: () => {},
    restoreNative: async () => { throw new Error('native commit failed') },
    applyTheme: () => {}, updateUser: () => {},
  }), /native commit failed/)
  assert.equal(target.getItem('mapSource'), 'osm')
})


test('retired railway source backups migrate both preference fields to automatic', () => {
  for (const settings of [
    { native: { mapSource: 'cnrail' } },
    { storage: { mapSource: 'cnrail' } },
    { native: { mapSource: 'cnrail' }, storage: { mapSource: 'hot' } },
  ]) {
    const result = validateAppSettings(settings)
    assert.equal(result.native.mapSource, 'auto')
    assert.equal(result.storage.mapSource, 'auto')
  }
})
