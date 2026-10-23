import { cp, readdir } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import path from 'node:path'
import { packageArrivalStations } from './package-arrival-stations.mjs'
import { packageSpeedometerAssets } from './package-speedometer-assets.mjs'
import { updateSaleTimes } from './update-sale-times.mjs'

const root = fileURLToPath(new URL('../', import.meta.url))
const source = path.join(root, 'web/public')
const target = path.join(root, 'app/src/main/assets/web')
await updateSaleTimes()
await packageArrivalStations()
await packageSpeedometerAssets()

for (const entry of await readdir(source, { withFileTypes: true })) {
    // Keep original fonts for editable source/test fixtures; package optimized fonts only.
    if (/\.(ttf|otf)$/i.test(entry.name) && entry.name !== 'consola.ttf') continue
    await cp(path.join(source, entry.name), path.join(target, entry.name), {
        recursive: true,
    })
}
