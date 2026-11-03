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

async function download() {
    for (const source of sources) {
        try {
            const response = await fetch(source, { signal: AbortSignal.timeout(60000) })
            if (!response.ok) throw new Error(`HTTP ${response.status}`)
            const bytes = new Uint8Array(await response.arrayBuffer())
            const database = parseLltArchive(bytes)
            if (!/^\d{8}$/.test(database.version) || !database.trains.size) {
                throw new Error('Invalid timetable snapshot')
            }
            await mkdir(directory, { recursive: true })
            await writeFile(resolve(directory, 'an.db'), bytes)
            await writeFile(resolve(directory, 'manifest.json'), JSON.stringify({
                version: database.version,
                sha256: digest(bytes),
                bytes: bytes.length,
                source,
            }, null, 2) + '\n')
            return
        } catch (error) {
            console.error(`${source}: ${error.message}`)
        }
    }
    throw new Error('Unable to download a valid offline snapshot')
}

async function packageSnapshot() {
    const bytes = await readFile(resolve(directory, 'an.db'))
    const manifest = JSON.parse(await readFile(resolve(directory, 'manifest.json'), 'utf8'))
    const database = parseLltArchive(bytes)
    if (digest(bytes) !== manifest.sha256 || bytes.length !== manifest.bytes ||
        database.version !== manifest.version || !database.trains.size) {
        throw new Error('Offline snapshot validation failed')
    }
    const output = resolve(root, 'app/build/generated/offline-assets/web/offline')
    await mkdir(output, { recursive: true })
    await writeFile(resolve(output, 'an.db'), bytes)
    await writeFile(resolve(output, 'manifest.json'), JSON.stringify(manifest) + '\n')
    console.log(`Offline timetable ${manifest.version}: ${bytes.length} bytes, SHA256 ${manifest.sha256}`)
}

async function importSnapshot() {
    const bytes = await readFile(resolve(directory, 'an.db'))
    const database = parseLltArchive(bytes)
    if (!/^\d{8}$/.test(database.version) || !database.trains.size) {
        throw new Error('Invalid timetable snapshot')
    }
    await writeFile(resolve(directory, 'manifest.json'), JSON.stringify({
        version: database.version,
        sha256: digest(bytes),
        bytes: bytes.length,
        source: sources[0],
    }, null, 2) + '\n')
}

if (process.argv[2] === 'import') await importSnapshot()
else if (process.argv[2] === 'update') await download()
else if (process.argv[2] === 'package') await packageSnapshot()
else throw new Error('Usage: node tools/offline-snapshot.mjs update|import|package')
