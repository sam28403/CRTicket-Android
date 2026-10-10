import test from 'node:test'
import assert from 'node:assert/strict'
import { zipSync } from 'fflate'
import { parseLltArchive, parseLltFiles, readLltNumber, readLltDate } from './lltParser.js'

function dictionary(names) {
  const count = Buffer.alloc(2)
  count.writeUInt16BE(names.length)
  return Buffer.concat([count, ...names.map(name => {
    const content = Buffer.from(name)
    const length = Buffer.alloc(2)
    length.writeUInt16BE(content.length)
    return Buffer.concat([length, content])
  })])
}

function fixture() {
  const files = Object.fromEntries(Array.from({ length: 20 }, (_, index) => [`T${index}.dat`, Buffer.alloc(0)]))
  files['t.i'] = dictionary(['G1'])
  files['s.i'] = dictionary(Array.from({ length: 257 }, (_, index) => `车站${index}`))
  files['ver.txt'] = Buffer.from('20261004')
  const record = Buffer.alloc(17 + 3 * 7)
  record.writeUInt16BE(3, 15)
  // 23:58 到达、停 5 分钟，下一站为次日；索引 256 与里程 1318 均跨字节。
  record.set([0, 0, 20, 0, 0, 0, 0], 17)
  record.set([0, 1, 23, 58, 5, 4, 3], 24)
  record.set([1, 1, 1, 0, 0, 5, 43], 31)
  files['T0.dat'] = record
  return files
}

test('按 255 进制解析车次索引、站点索引和累计里程', () => {
  assert.equal(readLltNumber(Buffer.from([0x21, 0xa1]), 0), 8576)
  const data = parseLltArchive(zipSync(fixture()))
  assert.equal(data.version, '20261004')
  const stops = data.trains.get('G1').stops
  assert.equal(stops[2].station, '车站256')
  assert.deepEqual(stops.map(stop => stop.mileage), [0, 1023, 1318])
  assert.equal(stops[1].departure, '00:03')
  assert.deepEqual(stops.map(stop => stop.arrivalDay), [0, 0, 1])
})

test('拒绝损坏数据，里程倒退的记录不参与补全', () => {
  const files = fixture()
  files['T0.dat'][36] = 0
  files['T0.dat'][37] = 0
  assert.ok(parseLltFiles(files).trains.get('G1').stops.every(stop => stop.mileage === null))
  assert.throws(() => parseLltFiles({ ...fixture(), 'T0.dat': Buffer.alloc(16) }))
  assert.throws(() => parseLltFiles({ ...fixture(), 't.i': Buffer.from([0, 1, 0, 5, 65]) }))
  const badIndex = fixture()
  badIndex['T0.dat'].set([255, 255], 17)
  assert.throws(() => parseLltFiles(badIndex))
})

test('开行日期使用小端 128 进制，循环规则按零基索引关联', () => {
  const files = fixture()
  files['T0.dat'].set([60, 80, 84, 9], 5) // 20260924
  files['T0.dat'].set([15, 81, 84, 9], 9) // 20261007
  files['T0.dat'].set([0, 1], 13)
  files['t.rule'] = Buffer.from('1 1\r\n2 1\r\n')
  assert.equal(readLltDate(files['T0.dat'], 5), '2026-09-24')
  assert.deepEqual(parseLltArchive(zipSync(files)).trains.get('G1').schedule, {
    startDate: '2026-09-24', endDate: '2026-10-07', rule: { period: 2, mask: 1 },
  })
  assert.throws(() => parseLltFiles({ ...files, 't.rule': Buffer.from('1 1\n2 4') }), /规则无效/)
  assert.throws(() => parseLltFiles({ ...files, 't.rule': undefined }), /规则索引/)
})

