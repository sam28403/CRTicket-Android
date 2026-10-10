import test from 'node:test'
import assert from 'node:assert/strict'
import { queryLeftTicket, parseTrains } from './leftTicket.js'
import { normalizeStops, matchesTrain, queryTrainInfo } from './trainInfo.js'

const date = new Date(Date.now() + 8 * 3600000).toISOString().slice(0, 10)

test('phone queries the current 12306 endpoint and preserves seat meanings', async () => {
    const fields = Array(56).fill('')
    Object.assign(fields, {
        1: '预订', 2: 'train-id', 3: 'G1', 6: 'BJP', 7: 'SHH',
        8: '08:00', 9: '12:30', 10: '04:30', 11: 'Y',
        26: '无', 30: '有', 31: '5', 32: '0',
    })
    const data = { result: [fields.join('|')], map: { BJP: '北京', SHH: '上海' } }
    const urls = []
    const result = await queryLeftTicket({ from: 'BJP', to: 'SHH', date }, async url => {
        urls.push(url)
        return url.endsWith('/init')
            ? new Response("var CLeftTicketUrl = 'leftTicket/queryG'")
            : Response.json({ status: true, data })
    })
    assert.equal(result.trains[0].seats.second, '>20')
    assert.equal(result.trains[0].seats.standing, '0')
    assert.match(urls[1], /leftTicket\/queryG/)
    assert.throws(() => parseTrains({ result: ['broken'], map: {} }))
})

test('train lookup uses exact train matches and calculates midnight crossings', async () => {
    assert.equal(matchesTrain('G10', 'G1'), false)
    assert.equal(matchesTrain('G1/2', 'G2'), true)
    const stops = normalizeStops([
        { station_name: '北京南', start_time: '23:50' },
        { station_name: '上海虹桥', arrive_time: '05:30' },
    ], 'G1')
    assert.equal(stops[1].arrivalDay, 1)
    const result = await queryTrainInfo('G1', date, async url => {
        if (url.includes('/search/')) return Response.json({ data: [{
            train_no: 'train-id', station_train_code: 'G1',
            from_station: '北京南', to_station: '上海虹桥',
        }] })
        return Response.json({ data: { data: [
            { station_name: '北京南', start_time: '08:00' },
            { station_name: '上海虹桥', arrive_time: '12:30' },
        ] } })
    }, async () => ({ version: '20261004', trains: new Map([['G1', {
        train: 'G1', stops: [
            { station: '北京南', mileage: 0 },
            { station: '上海虹桥', mileage: 1318 },
        ],
    }]]) }))
    assert.deepEqual(result.stops.map(stop => stop.mileage), [0, 1318])
})
