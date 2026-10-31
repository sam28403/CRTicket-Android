import test from 'node:test'
import assert from 'node:assert/strict'
import { filterSearchTrains, filterSearchStations } from './globalSearch.js'
import { listLltTrains } from '../android/railway/lltList.js'

test('search catalogue retains X, N and V trains including future X records', () => {
  const date = '2026-10-31'
  const codes = ['X1', 'X12', 'X123/X124', 'N1', 'N1998', 'V1', 'V6501', 'V7998']
  const trains = new Map(codes.map(train => [train, {
    train,
    schedule: { startDate: date, endDate: date },
    stops: [
      { station: '始发站', departure: '08:00', mileage: 0 },
      { station: '终到站', arrival: '09:00', mileage: 100 },
    ],
  }]))
  const rows = listLltTrains({ version: 'fixture', trains }, date).trains

  for (const prefix of ['X', 'N', 'V']) {
    const expected = codes.filter(code => code.startsWith(prefix))
    assert.deepEqual(filterSearchTrains(rows, ` ${prefix.toLowerCase()} `)
      .map(row => row.train), expected)
    for (const code of expected) {
      assert.equal(filterSearchTrains(rows, code.toLowerCase())[0].train, code)
    }
  }
  assert.equal(filterSearchTrains(rows, 'x124')[0].train, 'X123/X124')
  assert.deepEqual(filterSearchTrains(rows, 'X9998'), [])
})

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
