import { readFile, writeFile, mkdir } from 'node:fs/promises'
import { gzipSync, gunzipSync } from 'node:zlib'
import { createHash } from 'node:crypto'
import { fileURLToPath } from 'node:url'
import { parseLltArchive } from '../web/src/android/railway/lltParser.js'
import coordinates from '../web/src/utils/maps/stationCoordinates.js'
import { parseStations } from '../web/src/utils/stations.js'
import { attachQuizStationCities } from './quiz-city-catalog.mjs'

const root = new URL('../', import.meta.url)
const cache = new URL('build/quiz-sources/', root)
const target = new URL('app/src/main/assets/quiz-sources.bin', root)
if (process.argv.includes('--cities-only')) {
    const previous = JSON.parse(gunzipSync(await readFile(target)).toString('utf8'))
    const source = attachQuizStationCities(previous, parseStations())
    await writeFile(target, gzipSync(JSON.stringify(source), { level: 9 }))
    console.log(JSON.stringify({ cities: new Set(source.stations.map(row => row[3]).filter(Boolean)).size,
        cityStations: source.stations.filter(row => row[3]).length }))
    process.exit(0)
}
const lines = [
    '京沪线', '京广线', '京九线', '京哈线', '陇海线', '沪昆线',
    '兰新线', '青藏线', '京沪高速线', '京广高速线', '沪昆高速线',
    '京哈高速线京沈段', '京哈高速线沈哈段', '徐兰高速线',
    '成渝高速线成沙段', '贵广客专线', '南广线', '宁杭高速线',
    '杭甬线', '沪宁高速线', '合福高速线', '兰新客专线',
]
await mkdir(cache, { recursive: true })

async function download(url) {
    const response = await fetch(url, { signal: AbortSignal.timeout(45000) })
    if (!response.ok) throw new Error(`Source HTTP ${response.status}: ${url}`)
    return new Uint8Array(await response.arrayBuffer())
}

export function parseQuizLine(html, name) {
    const plain = text => text.replace(/<[^>]*>/g, '').replace(/&nbsp;/g, ' ')
        .replace(/&amp;/g, '&').trim()
    return [...html.matchAll(/<tr\b[^>]*>([\s\S]*?)<\/tr>/gi)].flatMap(row => {
        const cells = [...row[1].matchAll(/<td\b[^>]*>([\s\S]*?)<\/td>/gi)]
            .map(cell => plain(cell[1]))
        if (cells.length < 9 || cells[0] !== name) return []
        const km = cells[7].replace(/km$/i, '').replaceAll(',', '').trim()
        return [[cells[1], /^\d+(?:\.\d+)?$/.test(km) ? Number(km) : null]]
    })
}

const archiveArg = process.argv.find(arg => arg.startsWith('--archive='))?.slice(10)
const bytes = archiveArg ? await readFile(archiveArg) : await download('http://down.lltskb.com/an.db')
const database = parseLltArchive(bytes)
const official = new Set(parseStations().map(row => row.name))
const stationRows = [...official].sort().flatMap(name => coordinates[name]
    ? [[name, ...coordinates[name]]] : [])
const lineRows = []
for (const name of lines) {
    const url = `https://www.jprailfan.com/tools/stat/index.php?linename=${encodeURIComponent(name)}`
    const path = new URL(`${encodeURIComponent(name)}.html`, cache)
    let html
    try { html = await readFile(path, 'utf8') } catch {
        html = new TextDecoder().decode(await download(url))
        await writeFile(path, html)
    }
    const stops = parseQuizLine(html, name)
    if (stops.length < 3) throw new Error(`Incomplete line: ${name}`)
    lineRows.push({ name, stops, source: url })
    console.log(`${name}: ${stops.length} stops`)
}
const minutes = (clock, day) => clock == null ? null
    : day * 1440 + Number(clock.slice(0, 2)) * 60 + Number(clock.slice(3))
const source = {
    schema: 1,
    version: database.version,
    archiveSha256: createHash('sha256').update(bytes).digest('hex'),
    timetableSource: 'http://down.lltskb.com/an.db',
    coordinateSource: 'OpenStreetMap / existing audited station coordinate catalog',
    stations: stationRows,
    trains: [...database.trains.values()].map(item => ({
        train: item.train, schedule: item.schedule,
        stops: item.stops.map(stop => [
            stop.station,
            minutes(stop.arrival, stop.arrivalDay),
            minutes(stop.departure, stop.departureDay),
            stop.mileage,
        ]),
    })),
    routes: database.routing,
    lines: lineRows,
}
// Android's asset merger transparently expands .gz files and strips the extension.
const output = gzipSync(JSON.stringify(attachQuizStationCities(source, parseStations())), { level: 9 })
await writeFile(target, output)
console.log(JSON.stringify({
    target: fileURLToPath(target), version: source.version, bytes: output.length,
    stations: stationRows.length, trains: source.trains.length, routes: source.routes.length,
}))
