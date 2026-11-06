import { readFile, readdir, writeFile } from 'node:fs/promises'
import { resolve } from 'node:path'
import { zipSync } from '../web/node_modules/fflate/esm/index.mjs'
import { parseLltArchive } from '../web/src/android/railway/lltParser.js'

const number128 = (bytes, offset) => bytes[offset] * 128 + bytes[offset + 1]
function write255(bytes, offset, value) {
    bytes[offset] = Math.floor(value / 255)
    bytes[offset + 1] = value % 255
}

function convertTrains(bytes, headerSize) {
    const records = []
    let offset = 0
    while (offset < bytes.length) {
        let end = offset + headerSize
        while (end < bytes.length && bytes[end] !== 128) end += 7
        if (end >= bytes.length) throw new Error('旧版车次记录缺少结束标记')
        const header = Buffer.alloc(17)
        write255(header, 0, number128(bytes, offset))
        if (headerSize === 13) {
            header.set(bytes.subarray(offset + 2, offset + 4), 2)
            header.set(bytes.subarray(offset + 4, offset + 12), 5)
        } else {
            header.set(bytes.subarray(offset + 2, offset + 13), 2)
        }
        header[14] = bytes[offset + headerSize - 1]
        header.writeUInt16BE((end - offset - headerSize) / 7, 15)
        const stops = Buffer.from(bytes.subarray(offset + headerSize, end))
        for (let i = 0; i < stops.length; i += 7) {
            write255(stops, i, number128(bytes, offset + headerSize + i))
            write255(stops, i + 5, number128(bytes, offset + headerSize + i + 5))
        }
        records.push(header, stops)
        offset = end + 1
    }
    return Buffer.concat(records)
}

function convertSegments(bytes) {
    const records = []
    let offset = 0
    while (offset < bytes.length) {
        const size = bytes[offset + 4]
        const end = offset + 5 + size * 3
        if (size == null || end > bytes.length) throw new Error('旧版换号记录不完整')
        const record = Buffer.from(bytes.subarray(offset, end))
        write255(record, 0, number128(bytes, offset))
        for (let i = 5; i < record.length; i += 3) {
            write255(record, i, number128(bytes, offset + i))
        }
        records.push(record)
        offset = end
    }
    const count = Buffer.alloc(2)
    write255(count, 0, records.length)
    return Buffer.concat([count, ...records])
}

for (const argument of process.argv.slice(2)) {
    const directory = resolve(argument)
    const source = {}
    for (const name of await readdir(directory)) {
        if (name === 'an.db') continue
        source[name] = await readFile(resolve(directory, name))
    }
    const version = source['ver.txt'].toString().trim()
    if (!['20180118', '20191108'].includes(version)) {
        throw new Error(`尚未核验的旧版格式：${version}`)
    }
    // Preserve every source byte separately; old optional metadata has a different layout.
    const files = Object.fromEntries(Object.entries(source).map(([name, bytes]) =>
        [`original/${name}`, bytes]))
    files['ver.txt'] = source['ver.txt']
    files['s.i'] = source.s_i
    files['t.i'] = source.t_i
    files['t.rule'] = Buffer.from(source.t_rule.toString().trim().split(/\r?\n/).map(line => {
        const [period, mask] = line.split(' ').map(Number)
        // Some old masks contain unused high bits; only one period is meaningful.
        return `${period} ${mask % 2 ** period}`
    }).join('\n'))
    for (let i = 0; i < 20; i++) {
        files[`T${i}.dat`] = convertTrains(source[`t${i}.dat`], version === '20180118' ? 13 : 14)
    }
    files['xw.dat'] = convertSegments(source['xw.dat'])
    const bytes = zipSync(files, { level: 9 })
    const database = parseLltArchive(bytes)
    await writeFile(resolve(directory, 'an.db'), bytes)
    console.log(JSON.stringify({
        directory, version: database.version, trains: database.trains.size,
        stops: [...database.trains.values()].reduce((sum, train) => sum + train.stops.length, 0),
        segmentedTrains: [...database.trains.values()].filter(train =>
            train.stops.some(stop => stop.arrivalTrain)).length,
        routing: database.routing?.length ?? 0, bytes: bytes.length,
    }))
}
