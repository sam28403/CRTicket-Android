import test from 'node:test'
import assert from 'node:assert/strict'
import { applyLltTurnStations } from './lltTurnStations.js'
import { parseLltArchive } from './lltParser.js'
import { readFileSync, existsSync } from 'node:fs'

const number = value => [Math.floor(value / 255), value % 255]
function fixture() {
  // Company dictionary, one extra record with one dated remark,
  // three stock lists, then two turn-table records.
  return Uint8Array.from([
    ...number(1), 1, 65,
    ...number(1), ...number(0), ...number(0), 1,
    ...number(0), 1, ...Array(8).fill(0), 1, 65,
    ...number(1), ...number(0), ...number(0), ...number(0),
    ...number(2), ...number(0), 2, ...number(1), ...number(2),
    ...number(1), 1, ...number(0),
  ])
}
function trains() {
  return new Map([
    ['6856/6857', { stops: ['呼和浩特', '集宁南', '二连', '锡林浩特'].map(station => ({ station })) }],
    ['G1', { stops: [{ station: '呼和浩特' }] }],
  ])
}
const trainNames = ['6856/6857', 'G1']
const stationNames = ['呼和浩特', '集宁南', '二连']
test('独立调向站表匹配车次和站名，跳过其他元数据', () => {
  const items = trains()
  applyLltTurnStations(fixture(), trainNames, stationNames, items)
  assert.deepEqual(items.get('6856/6857').stops.map(stop => stop.isTurn === true), [false, true, true, false])
})
test('缺失、截断或非法索引不影响查询，也不应用部分结果', () => {
  const corrupt = fixture()
  corrupt[corrupt.length - 1] = 254
  for (const bytes of [undefined, new Uint8Array(), fixture().slice(0, -1), corrupt]) {
    const items = trains()
    assert.doesNotThrow(() => applyLltTurnStations(bytes, trainNames, stationNames, items))
    assert.ok([...items.values()].every(item => item.stops.every(stop => stop.isTurn == null)))
  }
})
const archive = new URL('../../../../state/llt-20261105-check/an.db', import.meta.url)
test('真实数据包6856/7包含集宁南及二连调向', { skip: !existsSync(archive) }, () => {
  const database = parseLltArchive(readFileSync(archive))
  const stops = database.trains.get('6856/6857').stops
  assert.deepEqual(stops.filter(stop => stop.isTurn).map(stop => stop.station), ['集宁南', '二连'])
})
