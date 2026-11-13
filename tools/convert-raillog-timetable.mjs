import assert from 'node:assert/strict'
import { readFile, writeFile } from 'node:fs/promises'
import { zipSync } from '../web/node_modules/fflate/esm/index.mjs'
import { validListDate } from '../web/src/android/railway/lltList.js'
import { parseLltArchive, parseLltFiles } from '../web/src/android/railway/lltParser.js'

const minutesPerDay = 1440
const maxLltNumber = 255 * 256 - 1

function encodeDictionary(names) {
    const parts = [Buffer.alloc(2)]
    parts[0].writeUInt16BE(names.length)
    for (const name of names) {
        const bytes = Buffer.from(name)
        const length = Buffer.alloc(2)
        length.writeUInt16BE(bytes.length)
        parts.push(length, bytes)
    }
    return Buffer.concat(parts)
}

function writeLltNumber(buffer, offset, value) {
    assert(Number.isInteger(value) && value >= 0 && value <= maxLltNumber, `索引或里程超出范围：${value}`)
    buffer[offset] = Math.floor(value / 255)
    buffer[offset + 1] = value % 255
}

function writeLltDate(buffer, offset, value) {
    for (let i = 0; i < 4; i++) {
        buffer[offset + i] = value % 128
        value = Math.floor(value / 128)
    }
}

function clockMinutes(time) {
    if (!time) return null
    const match = time.match(/^(\d{1,2}):(\d{1,2})$/)
    assert(match, `时刻格式异常：${time}`)
    const hour = Number(match[1])
    const minute = Number(match[2])
    assert(hour <= 24 && minute <= 59 && (hour !== 24 || minute === 0), `时刻无效：${time}`)
    return hour * 60 + minute
}

function normalizeClock(time) {
    if (!time) return null
    if (!/^\d{1,2}:\d{1,2}$/.test(time)) return null
    let minutes
    try {
        minutes = clockMinutes(time) % minutesPerDay
    } catch {
        return null
    }
    return `${String(Math.floor(minutes / 60)).padStart(2, '0')}:${String(minutes % 60).padStart(2, '0')}`
}

function encodeTrain(stops, trainIndex, stationIndexes) {
    assert(stops.length > 0 && stops.length <= 65535, '停站数量无效')
    const record = Buffer.alloc(17 + 7 * stops.length)
    writeLltNumber(record, 0, trainIndex)
    // 没有历史开行规则，按约定在格式可表达的日期范围内每日开行。
    writeLltDate(record, 5, 10000101)
    writeLltDate(record, 9, 99991231)
    record.writeUInt16BE(stops.length, 15)

    // 旧格式仅支持一字节停站时长，超出的值和跨日偏移由 stops.json 保留。
    for (const [i, stop] of stops.entries()) {
        const arrivalTime = normalizeClock(i === 0 ? null : stop.arrive_time)
        const departureTime = normalizeClock(stop.start_time)
        const arrival = arrivalTime === null
            ? departureTime === null ? 0 : clockMinutes(departureTime)
            : clockMinutes(arrivalTime)
        const departure = departureTime === null ? arrival : clockMinutes(departureTime)
        const stay = (departure - arrival + minutesPerDay) % minutesPerDay
        const offset = 17 + 7 * i
        writeLltNumber(record, offset, stationIndexes.get(stop.station_name))
        record.set([Math.floor(arrival / 60) % 24, arrival % 60, stay <= 255 ? stay : 0], offset + 2)
        const mileage = stop.mileage
        const fits = Number.isInteger(mileage) && mileage >= 0 && mileage <= maxLltNumber
        writeLltNumber(record, offset + 5, fits ? mileage : 0)
    }
    return record
}

function sourceFields(stops) {
    let previousEvent = 0
    return stops.map(source => {
        const arrivalTime = normalizeClock(source.arrive_time)
        const departureTime = normalizeClock(source.start_time)
        const arrival = arrivalTime === null ? null : clockMinutes(arrivalTime)
        const departure = departureTime === null ? null : clockMinutes(departureTime)
        const arrivalDay = source.arrive_day_diff
            + (arrival !== null && clockMinutes(source.arrive_time) === minutesPerDay ? 1 : 0)
        const arrivalEvent = arrival === null ? null : arrivalDay * minutesPerDay + arrival
        previousEvent = arrivalEvent ?? previousEvent
        const departureDay = departure === null
            ? arrivalDay
            : Math.max(0, Math.ceil((previousEvent - departure) / minutesPerDay))
        const departureEvent = departure === null ? null : departureDay * minutesPerDay + departure
        previousEvent = departureEvent ?? previousEvent
        const stay = arrivalEvent === null || departureEvent === null ? null : departureEvent - arrivalEvent
        return [
            arrivalTime, departureTime, arrivalDay, departureDay,
            stay, source.mileage,
        ]
    })
}

function parsedFields(stop) {
    return [stop.arrival, stop.departure, stop.arrivalDay, stop.departureDay, stop.stay, stop.mileage]
}

function convert({ version, trains }) {
    assert.match(version, /^\d{4}\.\d{2}\.\d{2}$/)
    assert(validListDate(version.replaceAll('.', '-')), '快照日期无效')
    assert(trains.length > 0, '快照没有车次')
    const names = trains.map(train => train.train_codes)
    assert(names.every(name => typeof name === 'string' && name.trim()), '缺少规范车次号')
    assert.equal(new Set(names).size, names.length, '快照包含重复规范车次号')
    const stations = [...new Set(trains.flatMap(train => train.stops.map(stop => stop.station_name)))]
    assert(stations.every(name => typeof name === 'string' && name.trim()), '缺少车站名')
    const stationIndexes = new Map(stations.map((name, index) => [name, index]))
    const groups = Array.from({ length: 20 }, () => [])
    for (const [index, train] of trains.entries()) {
        try {
            assert.equal(train.version, version, '车次详情来自其他版本')
            groups[index % groups.length].push(encodeTrain(train.stops, index, stationIndexes))
        } catch (error) {
            throw new Error(`${names[index]}：${error.message}`, { cause: error })
        }
    }
    const files = {
        'ver.txt': Buffer.from(version.replaceAll('.', '')),
        's.i': encodeDictionary(stations),
        't.i': encodeDictionary(names),
        ...Object.fromEntries(groups.map((group, index) => [`T${index}.dat`, Buffer.concat(group)])),
    }
    const base = parseLltFiles(files)
    const expected = trains.map(train => sourceFields(train.stops))
    const overrides = []
    for (const [index, code] of names.entries()) {
        const { stops } = base.trains.get(code)
        const train = trains[index]
        const changes = expected[index].flatMap((fields, stop) => {
            const actual = parsedFields(stops[stop])
            const source = train.stops[stop]
            const extra = [
                source.arrive_time && !normalizeClock(source.arrive_time) ? source.arrive_time : null,
                source.start_time && !normalizeClock(source.start_time) ? source.start_time : null,
            ]
            return fields.every((value, i) => value === actual[i]) && extra.every(value => value === null)
                ? []
                : [[stop, ...fields, ...extra]]
        })
        if (changes.length) overrides.push([code, changes])
    }
    if (overrides.length) files['stops.json'] = Buffer.from(JSON.stringify(overrides))
    const bytes = zipSync(files, { level: 9 })
    const parsed = parseLltArchive(bytes)
    for (const [trainIndex, train] of trains.entries()) {
        const { stops } = parsed.trains.get(train.train_codes)
        for (const [i, source] of train.stops.entries()) {
            const stop = stops[i]
            assert.equal(stop.station, source.station_name)
            assert.deepEqual(parsedFields(stop), expected[trainIndex][i],
                `${train.train_codes} 第 ${i + 1} 站无法无损转换`)
        }
    }
    return { bytes, stationCount: stations.length, trainsWithOverrides: overrides.length }
}

const [input, output] = process.argv.slice(2)
if (process.argv.length !== 4) {
    throw new Error('用法：node tools/convert-raillog-timetable.mjs <快照JSON> <输出.db>')
}
const snapshot = JSON.parse((await readFile(input, 'utf8')).replace(/^\uFEFF/, ''))
const { bytes, stationCount, trainsWithOverrides } = convert(snapshot)
await writeFile(output, bytes, { flag: 'wx' })
console.log(JSON.stringify({
    output,
    version: snapshot.version,
    trains: snapshot.trains.length,
    stations: stationCount,
    stops: snapshot.trains.reduce((total, train) => total + train.stops.length, 0),
    trainsWithOverrides,
    bytes: bytes.length,
}))
