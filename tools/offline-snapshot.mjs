import { createHash } from 'node:crypto'
import { mkdir, readFile, readdir, writeFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { resolve } from 'node:path'
import { parseLltArchive } from '../web/src/android/railway/lltParser.js'

const root = fileURLToPath(new URL('../', import.meta.url))
const directory = resolve(root, 'data')
const sources = [
    'http://down.lltskb.com/an.db',
    'http://223.107.87.50:8011/an.db',
]

function describeSnapshot(bytes, source) {
    const database = parseLltArchive(bytes)
    if (!/^\d{8}$/.test(database.version) || !database.trains.size) {
        throw new Error('Invalid timetable snapshot')
    }
    return {
        version: database.version,
        sha256: createHash('sha256').update(bytes).digest('hex'),
        bytes: bytes.length,
        source,
    }
}

async function localSnapshot() {
    const names = (await readdir(directory)).filter(name => /^\d{8}\.db$/.test(name)).sort().reverse()
    if (!names.length) throw new Error('No versioned timetable in data/')
    const name = names[0]
    const bytes = await readFile(resolve(directory, name))
    const manifest = describeSnapshot(bytes, `data/${name}`)
    if (name !== `${manifest.version}.db`) throw new Error(`Version does not match filename: ${name}`)
    return { bytes, manifest }
}

async function updateSnapshot() {
    const baseline = await localSnapshot()
    const results = await Promise.allSettled(sources.map(async source => {
        const response = await fetch(source, { cache: 'no-store', signal: AbortSignal.timeout(60000) })
        if (!response.ok) throw new Error(`HTTP ${response.status}`)
        const bytes = new Uint8Array(await response.arrayBuffer())
        return { bytes, manifest: describeSnapshot(bytes, source) }
    }))
    const candidates = results.filter(result => result.status === 'fulfilled').map(result => result.value)
    for (const [index, result] of results.entries()) {
        if (result.status === 'rejected') console.error(`${sources[index]}: ${result.reason.message}`)
    }
    candidates.sort ( (a, b) => b.manifest.version.localeCompare(a.manifest.version) )
    const latest = candidates[0]
    if (!latest) throw new Error('Unable to download a valid offline snapshot')
    if (latest.manifest.version < baseline.manifest.version) throw new Error('Upstream timetable is older than local data')
    const destination = resolve(directory, `${latest.manifest.version}.db`)
    try {
        await writeFile(destination, latest.bytes, { flag: 'wx' })
    } catch (error) {
        if (error.code !== 'EEXIST') throw error
        const existing = await readFile(destination)
        if (createHash('sha256').update(existing).digest('hex') !== latest.manifest.sha256) {
            throw new Error(`Version already exists with different contents: ${destination}`)
        }
    }
    console.log(`Stored timetable ${latest.manifest.version}`)
}

if (process.argv[2] === 'update') await updateSnapshot()
else if (process.argv[2] === 'package') {
    const { bytes, manifest } = await localSnapshot()
    const output = resolve(root, 'app/build/generated/offline-assets/web/offline')
    await mkdir(output, { recursive: true })
    await writeFile(resolve(output, 'an.db'), bytes)
    await writeFile(resolve(output, 'manifest.json'), JSON.stringify(manifest, null, 2) + '\n')
    console.log(`Offline timetable ${manifest.version}: SHA256 ${manifest.sha256}`)
} else throw new Error('Usage: node tools/offline-snapshot.mjs update|package')
