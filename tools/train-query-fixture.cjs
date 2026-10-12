const { zipSync } = require('../web/node_modules/fflate')

function dictionary(names) {
    const count = Buffer.alloc(2)
    count.writeUInt16BE(names.length)
    return Buffer.concat([count, ...names.map(name => {
        const text = Buffer.from(name)
        const length = Buffer.alloc(2)
        length.writeUInt16BE(text.length)
        return Buffer.concat([length, text])
    })])
}

function archive() {
    const files = Object.fromEntries(Array.from({ length: 20 }, (_, index) => [
        `T${index}.dat`, Buffer.alloc(0),
    ]))
    files['t.i'] = dictionary(['G100/G97', 'G100/G97B'])
    files['s.i'] = dictionary(['北京南', '天津西', '上海虹桥'])
    files['ver.txt'] = Buffer.from('20261013')
    files['T0.dat'] = Buffer.concat([0, 1].map(index => {
        const record = Buffer.alloc(17 + 3 * 7)
        record[1] = index
        for (const [offset, date] of [[5, 20000101], [9, 20991231]]) {
            for (let byte = 0; byte < 4; byte++) {
                record[offset + byte] = Math.floor(date / 128 ** byte) % 128
            }
        }
        record.writeUInt16BE(3, 15)
        record.set([0, 0, 8, 0, 0, 0, 0], 17)
        record.set([0, 1, 9, 0, 5, 0, 100], 24)
        record.set([0, 2, 12, 0, 0, 5, 43], 31)
        return record
    }))
    return Buffer.from(zipSync(files)).toString('base64')
}

async function installTrainQueryFixture(page) {
    await page.addInitScript(bytes => {
        window.onlineQueries = 0
        window.heldQueries = []
        window.cancelledQueries = []
        window.CRTicketAndroid = {
            configuration: () => JSON.stringify({ backend: 'phone' }),
            systemDarkMode: () => false,
            setAppearance() {},
            cancel: id => window.cancelledQueries.push(id),
            request(id, method, rawUrl) {
                const url = new URL(rawUrl)
                let data = {}
                if (url.pathname.includes('/train/search')) {
                    window.onlineQueries++
                    if (window.holdOnline) {
                        window.heldQueries.push(id)
                        return
                    }
                    data = { data: [{ station_train_code: 'G100/G97', train_no: 'fixture',
                        from_station: '北京南', to_station: '上海虹桥' }] }
                } else if (url.pathname.includes('/queryTrainInfo/query')) {
                    data = { data: { data: [
                        { station_name: '北京南', start_time: '08:00', station_no: '01' },
                        { station_name: '天津西', arrive_time: '09:00', start_time: '09:05', station_no: '02' },
                        { station_name: '上海虹桥', arrive_time: '12:00', station_no: '03' },
                    ] } }
                } else if (url.pathname === '/android.ver') {
                    data = { version: { data: '20261013' } }
                } else if (url.pathname === '/an.db') {
                    queueMicrotask(() => {
                        window.crticketBinaryChunk(id, { chunk: bytes })
                        window.crticketApiResult(id, { status: 200, binary: true, body: '' })
                    })
                    return
                }
                queueMicrotask(() => window.crticketApiResult(id, {
                    status: 200, contentType: 'application/json', body: JSON.stringify(data),
                }))
            },
        }
    }, archive())
}

module.exports = { installTrainQueryFixture }
