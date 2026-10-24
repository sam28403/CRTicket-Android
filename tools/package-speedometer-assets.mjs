import { mkdir, writeFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { parseStations } from '../web/src/utils/stations.js'

export async function packageSpeedometerAssets() {
    const target = new URL('../app/src/main/assets/speedometer-lib/', import.meta.url)
    await mkdir(target, { recursive: true })
    const stations = parseStations().map(station => [station.name, station.abbr, station.en])
    await writeFile(fileURLToPath(new URL('stations.json', target)), JSON.stringify(stations))
}
