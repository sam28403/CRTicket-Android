import test from 'node:test'
import assert from 'node:assert/strict'
import { buildAppBackup, parseAppBackup } from '../utils/appBackup.js'
import { selectBackupContents } from '../utils/backupContents.js'

const source = () => buildAppBackup([], { username: '备份测试', avatar: null }, {
  theme: 'classic-system', storage: { getItem: () => null },
  native: { saleAlarmEnabled: true, saleSameCity: false, saleSearchHistory: ['北京南'] },
})

test('sale settings and search history belong to independent selectable categories', () => {
  const value = parseAppBackup(JSON.stringify(source()))
  assert.deepEqual(selectBackupContents(value, ['settings']).settings.native,
    { saleAlarmEnabled: true, saleSameCity: false })
  assert.deepEqual(selectBackupContents(value, ['search']).settings.native,
    { saleSearchHistory: ['北京南'] })
})

test('malformed sale settings fail before restoration and older backups stay valid', () => {
  for (const native of [
    { saleAlarmEnabled: 'true' }, { saleSameCity: 1 },
    { saleSearchHistory: [12] }, { saleSearchHistory: Array(11).fill('北京') },
    { saleSearchHistory: ['\n北京'] },
  ]) {
    const value = source()
    value.settings.native = native
    assert.throws(() => parseAppBackup(JSON.stringify(value)))
  }
  const old = source()
  old.settings.native = { arrivalEnabled: false }
  assert.deepEqual(parseAppBackup(JSON.stringify(old)).settings.native, { arrivalEnabled: false })
})
