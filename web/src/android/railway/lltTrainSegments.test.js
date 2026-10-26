import test from 'node:test'
import assert from 'node:assert/strict'
import { applyLltTrainSegments } from './lltTrainSegments.js'
import { listLltStation } from './stationLlt.js'
import { stationBoardRows } from '../../utils/stationBoard.js'
import { stationTrainLocation } from '../../utils/stationNavigation.js'

function fixture(train = 'G100/G97') {
  const stops = [
    { station: '上海虹桥', mileage: 0, departure: '10:04' },
    { station: '南京南', mileage: 295, arrival: '11:03', departure: '11:05' },
    { station: '天津西', mileage: 1213, arrival: '14:09', departure: '14:12' },
    { station: '唐山', mileage: 1332, arrival: '14:49', departure: '14:51' },
    { station: '长春西', mileage: 2182, arrival: '19:07' },
  ].map(stop => ({ ...stop, train, arrivalDay: 0, departureDay: 0 }))
  const item = {
    train, stops,
    schedule: { startDate: '2026-10-01', endDate: '2026-10-31' },
  }
  return { trains: new Map([[train, item]]) }
}

const segments = Uint8Array.from([0, 1, 0, 0, 0, 48, 2, 0, 0, 0, 4, 193, 1])
const date = '2026-10-26'

test('按原始换号里程显示南京南 G100、唐山 G97，点击保留完整记录', () => {
  const database = fixture()
  applyLltTrainSegments(segments, ['G100/G97'], database.trains)
  for (const [station, train] of [['南京南', 'G100'], ['唐山', 'G97']]) {
    const [row] = listLltStation(database, station, date)
    for (const tab of ['all', 'arrival', 'departure']) {
      const [displayed] = stationBoardRows([row], tab)
      assert.equal(displayed.train, train)
      assert.deepEqual(stationTrainLocation(displayed, '路路通', date).query, {
        train, record: 'G100/G97', date,
      })
    }
  }
  const [terminal] = listLltStation(database, '长春西', date)
  assert.equal(terminal.train, 'G97')
})

test('换号站到达屏使用到达车次，出发屏及全部信息使用出发车次', () => {
  const database = fixture()
  applyLltTrainSegments(segments, ['G100/G97'], database.trains)
  const rows = listLltStation(database, '天津西', date)
  assert.equal(stationBoardRows(rows, 'arrival')[0].train, 'G100')
  assert.equal(stationBoardRows(rows, 'departure')[0].train, 'G97')
  assert.equal(stationBoardRows(rows, 'all')[0].train, 'G97')
})

test('终到恰在换号里程时，全部信息仍使用到达车次', () => {
  const database = fixture()
  const item = database.trains.get('G100/G97')
  item.stops = item.stops.slice(0, 3)
  item.stops[2].departure = null
  applyLltTrainSegments(segments, ['G100/G97'], database.trains)
  const rows = listLltStation(database, '天津西', date)
  assert.equal(stationBoardRows(rows, 'all')[0].train, 'G100')
  assert.equal(stationBoardRows(rows, 'arrival')[0].train, 'G100')
  assert.deepEqual(stationBoardRows(rows, 'departure'), [])
})

test('支持省略前缀、方案后缀、三个车次及非首分量始发', () => {
  for (const train of ['G100/97', 'G100/G97B']) {
    const database = fixture(train)
    applyLltTrainSegments(segments, [train], database.trains)
    assert.equal(database.trains.get(train).stops[1].train, 'G100')
  }
  const train = 'G100/G97/G99'
  const database = fixture(train)
  applyLltTrainSegments(Uint8Array.from([
    0, 1, 0, 0, 0, 48, 3, 0, 0, 1, 1, 40, 0, 4, 193, 2,
  ]), [train], database.trains)
  assert.deepEqual(database.trains.get(train).stops.map(stop => stop.train), [
    'G97', 'G100', 'G99', 'G99', 'G99',
  ])
})

test('可选换号表缺失、截断、索引错误或里程不可靠时保留完整车次', () => {
  const cases = [undefined, segments.slice(0, -1), Uint8Array.from([...segments, 0])]
  for (const [offset, value] of [[3, 1], [7, 1], [10, 20], [12, 51]]) {
    const bytes = segments.slice()
    bytes[offset] = value
    cases.push(bytes)
  }
  for (const bytes of cases) {
    const database = fixture()
    applyLltTrainSegments(bytes, ['G100/G97'], database.trains)
    assert.ok(database.trains.get('G100/G97').stops.every(stop => stop.train === 'G100/G97'))
  }
  const database = fixture()
  database.trains.get('G100/G97').stops[1].mileage = null
  applyLltTrainSegments(segments, ['G100/G97'], database.trains)
  assert.equal(listLltStation(database, '南京南', date)[0].train, 'G100/G97')
})
