import test from 'node:test'
import assert from 'node:assert/strict'
import { newerBackup, checkBackupCompatibility } from '../utils/backupCompatibility.js'
import { buildAppBackup, parseAppBackup } from '../utils/appBackup.js'

const build = version => buildAppBackup([], { username: '用户' }, {
  storage: { getItem: () => null }, appVersion: version,
})
const check = (value, validate) => checkBackupCompatibility(
  JSON.stringify(value), '0.10.3', parseAppBackup, validate, 'UPDATE_REQUIRED',
)

test('exports software version and preserves legacy and browser backups', () => {
  assert.equal(build('0.10.3').appVersion, '0.10.3')
  for (const value of [build('0.9.5'), build(undefined), [],
    { schemaVersion: 1, tickets: [] }]) {
    assert.equal(check(value).newer, false)
  }
})

test('compares numeric releases rather than strings', () => {
  assert.equal(newerBackup({ appVersion: '0.11.0' }, '0.10.3'), true)
  assert.equal(newerBackup({ appVersion: '0.9.9' }, '0.10.3'), false)
  assert.equal(newerBackup({ appVersion: '0.10.3' }, '0.10.3'), false)
})

test('compatible newer backups import and trigger an update reminder', () => {
  const result = check(build('0.11.0'))
  assert.equal(result.newer, true)
  assert.deepEqual(result.backup.tickets, [])
})

test('newer incompatible schema, settings and native values require an update', () => {
  for (const value of [
    { ...build('0.11.0'), schemaVersion: 3 },
    { ...build('0.11.0'), settings: { theme: 'future-theme' } },
  ]) assert.throws(() => check(value), /UPDATE_REQUIRED/)
  assert.throws(() => check(build('0.11.0'), () => { throw new Error('native') }), /UPDATE_REQUIRED/)
  assert.throws(() => check(build('0.9.5'), () => { throw new Error('native') }), /native/)
})
