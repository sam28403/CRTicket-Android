import test from 'node:test'
import assert from 'node:assert/strict'
import { findLltTrainCandidates, listLltTrains, lltTrainSummary } from './lltList.js'

const daily = { startDate: '2026-09-24', endDate: '2026-10-10', rule: null }
const stops = [
  { station: '始发站', departure: '20:00', departureDay: 0, mileage: 0 },
  { station: '终到站', arrival: '01:05', arrivalDay: 2, mileage: 1318 },
]

test('列表按上游类别顺序排列，N、V 先于 T，四位数字先于 Y，其余类别最后', () => {
  const codes = [
    '1461', 'K10', 'T1', 'Z1', 'S1', 'C1', 'D1', 'G10', 'G2', 'L1',
    'G2B', 'G3/G4', 'Y10', 'Y2', 'N10', 'N2B', 'N2', 'N3/N4',
    'V10', 'V2', 'V3B', 'V4/V5', '1462B', '50001', 'X1',
  ]
  const trains = new Map(codes.map(train => [train, { train, schedule: daily, stops }]))
  trains.set('G99', {
    train: 'G99', schedule: { ...daily, endDate: '2026-10-07' }, stops,
  })
  trains.set('G100', {
    train: 'G100', schedule: daily,
    stops: [
      { ...stops[0], departure: '00:03', departureDay: 1 },
      { ...stops[1], arrivalDay: 1, mileage: null },
    ],
  })

  const result = listLltTrains({ version: '20261024', trains }, '2026-10-08')
  assert.deepEqual(result.trains.map(row => row.train), [
    'G2', 'G2B', 'G3/G4', 'G10', 'G100', 'D1', 'C1',
    'S1', 'Z1', 'N2', 'N2B', 'N3/N4', 'N10',
    'V2', 'V3B', 'V4/V5', 'V10', 'T1', 'K10', 'L1',
    '1461', '1462B', 'Y2', 'Y10', 'X1', '50001',
  ])
  assert.equal(result.total, 26)
  assert.deepEqual(result.trains[0], {
    train: 'G2', from: '始发站', to: '终到站', duration: '29:05',
    stationCount: 2, mileage: 1318,
  })
  assert.equal(result.trains[4].duration, '01:02')
  assert.equal(result.trains[4].mileage, null)
})

test('N、V 双车次支持完整记录和省略字头查询，保持日期筛选及精确匹配', () => {
  for (const prefix of ['N', 'V']) {
    const item = { train: `${prefix}100/97`, schedule: daily, stops }
    const variant = { train: `${prefix}100/${prefix}97B`, schedule: daily, stops }
    const database = { trains: new Map([[item.train, item], [variant.train, variant]]) }
    for (const train of [`${prefix}100`, `${prefix}97`, item.train]) {
      assert.deepEqual(findLltTrainCandidates(database, train, '2026-10-08'), [item])
    }
    assert.deepEqual(findLltTrainCandidates(database, variant.train, '2026-10-08'), [variant])
    assert.deepEqual(findLltTrainCandidates(database, `${prefix}9`, '2026-10-08'), [])
    assert.deepEqual(findLltTrainCandidates(database, `${prefix}97`, '2026-10-11'), [])
    const exact = { train: `${prefix}97`, schedule: daily, stops }
    database.trains.set(exact.train, exact)
    assert.deepEqual(findLltTrainCandidates(database, exact.train, '2026-10-08'), [exact])
  }
})

test('vehicle cards and train list use the same summary for overnight services', () => {
  const item = {
    train: 'G101/G104',
    schedule: { startDate: '2026-10-01', endDate: '2026-10-31' },
    stops: [
      { station: '甲', departure: '23:30', departureDay: 0, mileage: 0 },
      { station: '乙', arrival: '01:00', arrivalDay: 1, mileage: 300 },
    ],
  }
  const database = { version: '20261021', trains: new Map([[item.train, item]]) }
  const found = findLltTrainCandidates(database, 'G104', '2026-10-21')[0]
  assert.deepEqual(lltTrainSummary(found), listLltTrains(database, '2026-10-21').trains[0])
  assert.deepEqual(lltTrainSummary(found), {
    train: 'G101/G104', from: '甲', to: '乙', duration: '01:30', stationCount: 2, mileage: 300,
  })
  assert.equal(findLltTrainCandidates(database, 'G104', '2026-11-01').length, 0)
})
