import test from 'node:test'
import assert from 'node:assert/strict'
import { lltTrainDetail } from './lltDetail.js'

const date = '2026-10-13'
const schedule = { startDate: '2026-10-01', endDate: '2026-10-31', rule: null }
const stops = [{ station: '北京南' }, { station: '上海虹桥' }]
const record = train => ({ train, schedule, stops })
const database = (...records) => ({
  version: '20261013',
  trains: new Map(records.map(item => [item.train, item])),
})

test('按换号后的单车次或完整记录查询，返回完整离线车次与站序', () => {
  const data = database(record('G100/G97'))
  for (const train of ['G100', 'G97', 'G100/G97']) {
    const result = lltTrainDetail(data, train, date)
    assert.equal(result.train, 'G100/G97')
    assert.deepEqual(result.stops, stops)
    assert.equal(result.from, '北京南')
    assert.equal(result.to, '上海虹桥')
    assert.equal(result.mileageVersion, data.version)
  }
  for (const train of ['G9', 'G970', 'G97B', 'G97/G100']) {
    assert.deepEqual(lltTrainDetail(data, train, date).stops, [])
  }
  assert.deepEqual(lltTrainDetail(data, 'G97', '2026-11-01').stops, [])
})

test('省略前缀的组合号可匹配，方案后缀仍须精确匹配', () => {
  const data = database(record('G100/97'), record('G100/G97B'))
  assert.equal(lltTrainDetail(data, 'G97', date).train, 'G100/97')
  assert.equal(lltTrainDetail(data, 'G100/G97B', date).train, 'G100/G97B')
  data.trains.delete('G100/97')
  assert.deepEqual(lltTrainDetail(data, 'G97', date).stops, [])
})

test('同名记录冲突须选择完整车次，精确记录优先且遵守开行日期', () => {
  const data = database(record('G100/G97'), record('G102/G97'))
  assert.throws(() => lltTrainDetail(data, 'G97', date), /选择完整车次/)
  assert.equal(lltTrainDetail(data, 'G100/G97', date).train, 'G100/G97')
  data.trains.set('G97', record('G97'))
  assert.equal(lltTrainDetail(data, 'G97', date).train, 'G97')
  data.trains.get('G97').schedule = { ...schedule, endDate: '2026-10-12' }
  assert.throws(() => lltTrainDetail(data, 'G97', date), /选择完整车次/)
})

test('空值和非法车次不会查询离线库', () => {
  for (const train of [undefined, '', 'G1?', 'G1/'.repeat(20)]) {
    assert.throws(() => lltTrainDetail(database(), train, date), /有效车次/)
  }
})
