import test from 'node:test'
import assert from 'node:assert/strict'
import { trainQuerySourceLocation, requiresOfflineTrainQuery } from './trainQuerySource.js'
import { trainQueryRange } from './trainQueryDate.js'
import { matchingTrainRecord, trainRecordCacheKey } from './trainQueryRecord.js'

const date = trainQueryRange().today

test('S2101 的路路通备用结果必须切换离线模式，而不是保留在线通道', () => {
  assert.equal(requiresOfflineTrainQuery(undefined, {
    train: 'S2101', source: '路路通（离线参考）', stops: [{ station: '甲' }],
  }), true)
  assert.equal(requiresOfflineTrainQuery(undefined, {
    offlineFallback: true, stops: [{ station: '甲' }],
  }), true)
  assert.equal(requiresOfflineTrainQuery(undefined, { stops: [] }), true)
  assert.equal(requiresOfflineTrainQuery('llt', { stops: [] }), false)
  assert.equal(requiresOfflineTrainQuery(undefined, {
    source: '12306', stops: [{ station: '甲' }],
  }), false)
})

test('切换离线来源保留车次和日期，并设置路路通参数', () => {
  assert.deepEqual(trainQuerySourceLocation({ train: ' d7976 ', date, offline: true }), {
    path: '/train',
    query: { train: 'D7976', date, source: 'llt' },
  })
})

test('离线双车次切换在线时使用单车次，并可切回完整离线记录', () => {
  const offlineRecord = 'G1/G2B'
  const online = trainQuerySourceLocation({ train: 'G1/G2', date, offline: false })
  assert.deepEqual(online.query, { train: 'G1', date, source: undefined })
  const offline = trainQuerySourceLocation({
    train: online.query.train, date, offline: true, offlineRecord,
  })
  assert.equal(offline.query.train, offlineRecord)
  assert.equal(offline.query.source, 'llt')
})

test('输入新车次后切换离线不会套用旧记录', () => {
  const location = trainQuerySourceLocation({
    train: 'G3', date, offline: true, offlineRecord: 'G1/G2B',
  })
  assert.equal(location.query.train, 'G3')
})

test('在线来源将范围外日期改为今天，离线来源保留原日期', () => {
  const options = { train: 'D7976', date: '2000-01-01' }
  assert.equal(trainQuerySourceLocation({ ...options, offline: false }).query.date, date)
  assert.equal(trainQuerySourceLocation({ ...options, offline: true }).query.date, options.date)
})

test('尚未输入车次也可以切换来源', () => {
  assert.deepEqual(trainQuerySourceLocation({ train: '', date, offline: true }).query, {
    train: undefined, date, source: 'llt',
  })
})

test('车站入口切换在线或离线均保留非首车次和完整记录关联', () => {
  for (const offline of [true, false]) {
    assert.deepEqual(trainQuerySourceLocation({
      train: 'G3244', date, offline, offlineRecord: 'G3241/G3244', record: 'G3241/G3244',
    }).query, {
      train: 'G3244', date, source: offline ? 'llt' : undefined, record: 'G3241/G3244',
    })
  }
  assert.equal(trainQuerySourceLocation({
    train: 'G2', date, offline: true, record: 'G3241/G3244',
  }).query.record, undefined)
})

test('完整记录匹配支持换号、省略前缀和方案后缀，拒绝过期关联及损坏输入', () => {
  for (const record of ['G3241/G3244', 'G3241/3244', 'G3241/G3244B']) {
    assert.equal(matchingTrainRecord('G3244', record), record)
  }
  for (const record of ['G3241/G32440', 'G3241/G3244/', {}, undefined]) {
    assert.equal(matchingTrainRecord('G3244', record), '')
  }
  assert.notEqual(trainRecordCacheKey('G3244', 'G3241/G3244'), trainRecordCacheKey('G3244', 'G3241/G3244B'))
  assert.notEqual(trainRecordCacheKey('G3244', 'G3241/G3244'), trainRecordCacheKey('G3241', 'G3241/G3244'))
})
