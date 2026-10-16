import test from 'node:test'
import assert from 'node:assert/strict'
import { createStationQuery } from './station.js'

const date = '2026-10-11'
const noon = Date.parse(`${date}T12:00:00+08:00`)
const params = { station: '北京南', date }
const row = { train: 'G1', departure: '08:00' }

function database(names = ['北京南']) {
  return {
    version: '20261011',
    trains: new Map(names.map(name => [name, {
      train: 'G1', schedule: { startDate: '2026-10-01', endDate: '2026-10-31' },
      stops: [{ station: name, departure: '08:00', departureDay: 0 }],
    }])),
  }
}

test('列表外的香港及老挝站点可查询，各自保留独立缓存', async () => {
  const called = []
  const request = createStationQuery({
    now: () => noon,
    loadDatabase: async () => {
      called.push('llt')
      return database(['万象', '琅勃拉邦', '香港西九龙'])
    },
  })
  for (const station of ['万象', '琅勃拉邦', '香港西九龙', '万象']) {
    const result = await request({ station })
    assert.equal(result.station, station)
    assert.equal(result.trains[0].train, 'G1')
  }
  assert.deepEqual(called, ['llt', 'llt', 'llt'])
  await assert.rejects(request({ station: '坏\n站名' }), /车站名称/)
  await assert.rejects(request({ station: '站'.repeat(31) }), /车站名称/)
})

test('本机车站查询限制有效站名和北京时间当天，合并并发及一分钟缓存', async () => {
  let time = noon
  let calls = 0
  const request = createStationQuery({
    now: () => time,
    loadDatabase: async () => {
      calls++
      await new Promise(resolve => setTimeout(resolve, 5))
      return database()
    },
  })
  await assert.rejects(request({ station: '' }), /车站名称/)
  await assert.rejects(request({ ...params, date: '2026-10-10' }), /当天/)
  const results = await Promise.all(Array.from({ length: 5 }, () => request(params)))
  assert.equal(calls, 1)
  for (const result of results) {
    assert.equal(result.success, true)
    assert.equal(result.station, params.station)
    assert.equal(result.date, date)
    assert.equal(result.total, 1)
    assert.equal(result.trains[0].train, row.train)
    assert.equal(result.trains[0].departure, row.departure)
  }
  await request(params)
  assert.equal(calls, 1)
  time += 60001
  await request(params)
  assert.equal(calls, 2)
  time = Date.parse('2026-10-12T00:00:00+08:00')
  await assert.rejects(request(params), /当天/)
  assert.equal((await request({ station: '北京南' })).date, '2026-10-12')
  assert.equal(calls, 3)
})

test('午夜前开始、午夜后返回的查询不能展示前一天数据', async () => {
  let time = noon
  const request = createStationQuery({
    now: () => time,
    loadDatabase: async () => {
      time = Date.parse('2026-10-12T00:00:00+08:00')
      return database()
    },
  })
  await assert.rejects(request(params), /日期已更新/)
})

test('取消一个等待者不会影响共享查询，已取消请求不访问上游', async () => {
  let finish
  let calls = 0
  const request = createStationQuery({
    now: () => noon,
    loadDatabase: () => {
      calls++
      return new Promise(resolve => { finish = resolve })
    },
  })
  const controller = new AbortController()
  const cancelled = request(params, controller.signal)
  const other = request(params)
  controller.abort()
  await assert.rejects(cancelled, error => error.code === 'ERR_CANCELED')
  finish(database())
  assert.equal((await other).total, 1)
  await assert.rejects(request(params, controller.signal), error => error.code === 'ERR_CANCELED')
  assert.equal(calls, 1)
})

test('路路通失败时显示查询错误，失败结果不缓存且后续可重试', async () => {
  let calls = 0
  const request = createStationQuery({
    now: () => noon,
    loadDatabase: async () => {
      calls++
      if (calls > 1) return database()
      throw new Error('offline')
    },
  })
  await assert.rejects(request(params), /路路通车站数据暂不可用/)
  assert.equal((await request(params)).source, '路路通')
  assert.equal(calls, 2)
})
