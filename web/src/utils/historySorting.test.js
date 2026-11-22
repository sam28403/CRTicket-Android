import test from 'node:test'
import assert from 'node:assert/strict'
import { compareHistoryDeparture } from './historySorting.js'

test('日期优先，同一天按出发时间排序，无有效时刻的记录排在当天末尾', () => {
  const rows = [
    { id: 'missing', date: '2026-02-08', time: '' },
    { id: 'late', date: '2026年2月8日', time: '10:05' },
    { id: 'previous', date: '2026-02-07', time: '23:59' },
    { id: 'early', date: '2026/02/08', time: '9:15' },
    { id: 'tie', date: '2026-02-08', time: '09:15' },
    { id: 'invalid', date: '2026-02-08', time: '24:00' },
  ]
  const order = direction =>
    [...rows].sort((a, b) => compareHistoryDeparture(a, b, direction)).map(row => row.id)
  assert.deepEqual(order(1), ['previous', 'early', 'tie', 'late', 'missing', 'invalid'])
  assert.deepEqual(order(-1), ['late', 'early', 'tie', 'missing', 'invalid', 'previous'])
})
