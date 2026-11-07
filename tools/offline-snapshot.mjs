import { createHash } from 'node:crypto'
import { mkdir, readFile, writeFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { resolve } from 'node:path'
import { parseLltArchive } from '../web/src/android/railway/lltParser.js'

const root = fileURLToPath(new URL('../', import.meta.url))
const directory = resolve(root, 'offline-data')
const digest = bytes => createHash('sha256').update(bytes).digest('hex')
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
        sha256: digest(bytes),
        bytes: bytes.length,
        source,
    }
}

async function downloadSnapshot(source) {
    const response = await fetch(source, {
        cache: 'no-store',
        signal: AbortSignal.timeout(60000),
    })
    if (!response.ok) throw new Error(`HTTP ${response.status}`)
    const bytes = new Uint8Array(await response.arrayBuffer())
    return { bytes, manifest: describeSnapshot(bytes, source) }
}

async function latestSnapshot() {
    const baseline = JSON.parse(await readFile(resolve(directory, 'manifest.json'), 'utf8'))
    const results = await Promise.allSettled(sources.map(downloadSnapshot))
    let latest
    for (const [index, result] of results.entries()) {
        if (result.status === 'rejected') {
            console.error(`${sources[index]}: ${result.reason.message}`)
            continue
        }
        const candidate = result.value
        console.log(`${candidate.manifest.source}: timetable ${candidate.manifest.version}`)
        if (!latest || candidate.manifest.version > latest.manifest.version) latest = candidate
    }
    if (!latest) throw new Error('Unable to download a valid offline snapshot')
    if (latest.manifest.version < baseline.version) {
        throw new Error(`Upstream timetable ${latest.manifest.version} is older than ${baseline.version}`)
    }
    return latest
}

async function writeSnapshot(output, { bytes, manifest }) {
    await mkdir(output, { recursive: true })
    await writeFile(resolve(output, 'an.db'), bytes)
    await writeFile(resolve(output, 'manifest.json'), JSON.stringify(manifest, null, 2) + '\n')
    console.log(`Offline timetable ${manifest.version}: ${bytes.length} bytes, SHA256 ${manifest.sha256}`)
}

async function importSnapshot() {
    const bytes = await readFile(resolve(directory, 'an.db'))
    const manifest = describeSnapshot(bytes, sources[0])
    await writeFile(resolve(directory, 'manifest.json'), JSON.stringify(manifest, null, 2) + '\n')
}

if (process.argv[2] === 'import') await importSnapshot()
else if (process.argv[2] === 'update') await writeSnapshot(directory, await latestSnapshot())
else if (process.argv[2] === 'package') {
    const output = resolve(root, 'app/build/generated/offline-assets/web/offline')
    await writeSnapshot(output, await latestSnapshot())
}
else throw new Error('Usage: node tools/offline-snapshot.mjs update|import|package')
