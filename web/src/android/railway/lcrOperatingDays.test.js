import assert from 'node:assert/strict'
import test from 'node:test'
import { lcrOperatingDays } from './lcrOperatingDays.js'

test('老挝班次日历使用原规则的开行区间和星期', () => {
  const daily = new Map(lcrOperatingDays({ train: 'C85', dataset: 'lcr' }))
  assert.equal(daily.get('20261101'), '1')
  assert.equal(daily.get('20270115'), '1')
  assert.equal(daily.has('20270116'), false)

  const c95 = new Map(lcrOperatingDays({ train: 'C95', timeZone: 'vientiane' }))
  assert.equal(c95.get('20261112'), '0')
  assert.equal(c95.get('20261113'), '1')
  const c96 = new Map(lcrOperatingDays({ train: 'C96', dataset: 'lcr' }))
  assert.equal(c96.get('20261112'), '1')
  assert.equal(c96.get('20261117'), '0')

  const k11 = new Map(lcrOperatingDays({ train: 'K11', dataset: 'lcr', stops: [] }))
  assert.equal(k11.get('20261121'), '1')
  assert.equal(k11.get('20261122'), '0')
  assert.equal(k11.has('20261125'), false)
})

test('同号国内线路与 D 字头不使用老挝段离线规则', () => {
  assert.deepEqual(lcrOperatingDays({ train: 'C85', source: '12306' }), [])
  assert.deepEqual(lcrOperatingDays({ train: 'D88', source: '路路通' }), [])
  assert.deepEqual(lcrOperatingDays(null), [])
})
