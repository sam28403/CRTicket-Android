import assert from 'node:assert/strict'
import test from 'node:test'
import { lcrTrainDetail, listLcrStation, listLcrTrains } from './lcrOffline.js'
import { listLltStation } from './stationLlt.js'
import { createStationQuery } from './station.js'
import { stationTrainLocation } from '../../utils/stationNavigation.js'

test('离线车次按星期与闭区间筛选并保留同号线路', () => {
  assert.equal(lcrTrainDetail('C85', '2027-01-15').dataset, 'lcr')
  assert.equal(lcrTrainDetail('C85', '2027-01-16').stops.length, 0)
  assert.equal(lcrTrainDetail('C95', '2026-11-11').stops.length, 0)
  assert.ok(lcrTrainDetail('C95', '2026-11-13').stops.length)
  assert.equal(lcrTrainDetail('K11', '2026-11-25').stops.length, 0)
  assert.equal(lcrTrainDetail('D88', '2026-11-11').stops.length, 0)
  const domestic = { trains: [{ train: 'C85', from: '昆明', to: '香格里拉' }] }
  const rows = listLcrTrains('2026-11-11', domestic).trains.filter(row => row.train === 'C85')
  assert.equal(rows.length, 2)
  assert.equal(rows[1].dataset, 'lcr')
})

test('万象大屏在北京时间零点后仍查询当地当天，详情默认查询同一线路的在线时刻', async () => {
  const request = createStationQuery({
    now: () => Date.parse('2026-11-11T16:30:00Z'),
    loadDatabase: async () => { throw new Error('offline') },
  })
  const date = '2026-11-11'
  const board = await request({ station: '万象', date })
  const row = board.trains.find(item => item.train === 'C85')
  assert.equal(board.date, date)
  assert.deepEqual(stationTrainLocation(row, board.source, date).query, {
    train: 'C85', date, dataset: 'lcr',
  })
  const beijing = lcrTrainDetail('C85', date).stops.find(stop => stop.station === '万象')
  assert.equal(row.arrival, `${String(Number(beijing.arrival.slice(0, 2)) - 1).padStart(2, '0')}${beijing.arrival.slice(2)}`)
  assert.ok(listLcrStation('磨丁', date).length)
})

test('北京时间次日首小时的停站归入万象前一天', () => {
  const item = {
    train: 'D88',
    schedule: { startDate: '2026-11-11', endDate: '2026-11-11' },
    stops: [
      { station: '磨憨', departure: '23:40' },
      { station: '磨丁', arrival: '00:15', arrivalDay: 1, departure: '01:10', departureDay: 1 },
    ],
  }
  const database = { trains: new Map([[item.train, item]]) }
  const [first] = listLltStation(database, '磨丁', '2026-11-11')
  const [second] = listLltStation(database, '磨丁', '2026-11-12')
  assert.equal(first.arrival, '23:15')
  assert.equal(first.departureLabel, '次日 00:10')
  assert.equal(second.arrivalLabel, '昨日 23:15')
  assert.equal(second.departure, '00:10')
  assert.equal(second.trainDate, '2026-11-11')
})
