import { writeFile } from 'node:fs/promises'
import stationCoordinates from '../web/src/utils/maps/stationCoordinates.js'
import { parseStations } from '../web/src/utils/stations.js'

const officialNames = new Set(parseStations().map(({ name }) => name))

function nameScore(name) {
    if (officialNames.has(name)) return -1000
    // 同一坐标的多语言名称合并，优先短中文名，保留全部别名。
    return (/^[\p{Script=Han}]+$/u.test(name) ? 0 : 100) + name.length
}

export function buildArrivalStations(coordinates = stationCoordinates) {
    const sites = new Map()
    for (const [name, [longitude, latitude]] of Object.entries(coordinates)) {
        if (!name || !Number.isFinite(longitude) || !Number.isFinite(latitude)
            || Math.abs(longitude) > 180 || Math.abs(latitude) > 90) {
            throw new Error(`Invalid arrival coordinates: ${name}`)
        }
        const key = `${longitude},${latitude}`
        let station = sites.get(key)
        if (!station) {
            station = { name, latitude, longitude, aliases: [] }
            sites.set(key, station)
        }
        station.aliases.push(name)
        if (nameScore(name) < nameScore(station.name)) station.name = name
    }
    return [...sites.values()]
}

export async function packageArrivalStations() {
    const stations = buildArrivalStations()
    const target = new URL('../app/src/main/assets/arrival-stations.json', import.meta.url)
    await writeFile(target, JSON.stringify(stations) + '\n')
    console.log(`Packaged all ${Object.keys(stationCoordinates).length} coordinate names`
        + ` as ${stations.length} distinct station locations for arrival alerts.`)
}
