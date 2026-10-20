import test from 'node:test'
import assert from 'node:assert/strict'
import { filterSearchTrains, filterSearchStations } from './globalSearch.js'

test('train search includes partial numbers, ignores case and uses railway ordering', () => {
  const rows = ['G251', 'G250', 'G25', 'D1', 'G1', '1462', '1461', 'D3', 'D2']
    .map(train => ({ train }))
  assert.deepEqual(filterSearchTrains(rows, ' g25 ').map(row => row.train), ['G25', 'G250', 'G251'])
  assert.deepEqual(filterSearchTrains(rows, '1').map(row => row.train), ['G1', 'G251', 'D1', '1461', '1462'])
  assert.deepEqual(filterSearchTrains(rows, 'd').map(row => row.train), ['D1', 'D2', 'D3'])
  assert.deepEqual(filterSearchTrains(rows, ' '), [])
  assert.deepEqual(filterSearchTrains(rows, 'missing'), [])
})

test('station matching preserves source order and supports names, pinyin and abbreviations', () => {
  const catalogue = [
    { name: '北京南', en: 'beijingnan', abbr: 'bjn', telecode: 'VNP' },
    { name: '丹东', en: 'dandong', abbr: 'dd', telecode: 'DUT' },
    { name: '大连', en: 'dalian', abbr: 'dl', telecode: 'DLT' },
  ]
  const rows = ['大连', '北京南', '丹东'].map(station => ({ station }))
  assert.deepEqual(filterSearchStations(rows, catalogue, 'D').map(row => row.station), ['大连', '丹东'])
  for (const key of ['北京', 'beijing', 'BJN', 'vnp']) {
    assert.deepEqual(filterSearchStations(rows, catalogue, key), [rows[1]])
  }
  assert.deepEqual(filterSearchStations(rows, catalogue, ''), [])
})
