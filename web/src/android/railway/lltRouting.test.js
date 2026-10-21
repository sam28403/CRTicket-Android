import test from 'node:test'
import assert from 'node:assert/strict'
import { parseLltRouting, queryLltRouting, routingTrainCodes } from './lltRouting.js'

function routingBytes() {
  const header = Buffer.alloc(22)
  header.writeUInt32BE(1)
  header.writeUInt32BE(20260101, 4)
  header.writeUInt32BE(20991231, 8)
  const string = value => {
    const bytes = Buffer.from(value)
    return Buffer.concat([Buffer.from([bytes.length]), bytes])
  }
  return Buffer.concat([
    header, string('CR400BF-S 型重联'), string('G32#G1#G1958/5#0D55#DJ1234'),
    Buffer.from('unrelated trailing sections'),
  ])
}

const date = '2026-10-21'
function timetable(train) {
  return {
    train, schedule: { startDate: '2026-01-01', endDate: '2099-12-31' },
    stops: [
      { station: '北京南', departure: '06:30', departureDay: 0, mileage: 0 },
      { station: '上海虹桥', arrival: '11:24', arrivalDay: 0, mileage: 1318 },
    ],
  }
}
function database() {
  return {
    version: '20261024', routing: parseLltRouting(routingBytes()),
    trains: new Map(['G1', 'G32', 'G1955/G1958', '0D55', 'DJ1234'].map(train => [train, timetable(train)])),
  }
}

test('交路二进制读取完整记录，忽略后续其他数据段并拒绝截断', () => {
  const routes = parseLltRouting(routingBytes())
  assert.equal(routes[0].model, 'CR400BF-S 型重联')
  assert.equal(routes[0].trains.length, 5)
  assert.equal(routes[0].start, 20260101)
  assert.throws(() => parseLltRouting(routingBytes().subarray(0, 30)))
  assert.deepEqual(parseLltRouting(), [])
})

test('展开多车次简称，保留交路顺序，精确强调查询车次', () => {
  assert.deepEqual(routingTrainCodes('G1958/5'), ['G1958', 'G1955'])
  assert.deepEqual(routingTrainCodes('G82/79'), ['G82', 'G79'])
  const result = queryLltRouting(database(), ' g1 ', date)
  const rows = result.groups[0].rows
  assert.deepEqual(rows.map(row => row.train), ['G32', 'G1', 'G1958/5', '0D55', 'DJ1234'])
  assert.deepEqual(rows.map(row => row.highlighted), [false, true, false, false, false])
  assert.equal(rows[2].detailTrain, 'G1955/G1958')
  assert.equal(rows[1].mileage, 1318)
  assert.equal(rows[1].duration, '04:54')
  assert.equal(queryLltRouting(database(), 'G10', date).groups.length, 0)
  assert.equal(queryLltRouting(database(), 'G1955', date).groups[0].rows[2].highlighted, true)
})

test('0D 和 DJ 保留卡片但不可点击，缺失时刻表也保留，过期交路不命中', () => {
  const db = database()
  db.trains.delete('0D55')
  const rows = queryLltRouting(db, 'DJ1234', date).groups[0].rows
  assert.equal(rows[3].train, '0D55')
  assert.equal(rows[3].clickable, false)
  assert.equal(rows[4].clickable, false)
  assert.equal(rows[4].highlighted, true)
  assert.equal(rows[1].clickable, true)
  db.routing[0].end = 20261020
  assert.equal(queryLltRouting(db, 'G1', date).groups.length, 0)
  assert.throws(() => queryLltRouting(db, '<script>', date))
  assert.throws(() => queryLltRouting({ ...db, routing: null }, 'G1', date), /没有交路/)
})
