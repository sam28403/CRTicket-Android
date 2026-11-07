import { unzipSync } from 'fflate'
import { decodeArchive, loadLltDatabase } from './lltLoader.js'
import {
    legacyImportId, manualSelectionKey, openArchiveStore, readArchiveRecords, selectedImportId,
} from './archiveStore.js'

const prefix = 'import:'
const isLegacy = archive => archive?.imported && !archive.importId
let bundledVersion

function readBundledVersion() {
    bundledVersion ??= fetch('/offline/manifest.json', { cache: 'no-store' })
        .then(async response => {
            if (!response.ok) return null
            const { version } = await response.json()
            return typeof version === 'string' && /^\d{8}$/.test(version) ? version : null
        })
        .catch(() => null)
    return bundledVersion
}

function archiveVersion(archive) {
    if (!archive) return null
    if (archive.version) return archive.version
    try {
        const files = unzipSync(new Uint8Array(archive.bytes), {
            filter: entry => entry.name === 'ver.txt' && entry.originalSize <= 1024,
        })
        const version = new TextDecoder().decode(files['ver.txt']).trim()
        return /^\d{8}$/.test(version) ? version : null
    } catch {
        return null
    }
}

function readCatalog() {
    return readArchiveRecords('current', manualSelectionKey, legacyImportId, IDBKeyRange.bound(prefix, 'import;'))
}

async function writeCatalog(write) {
    const db = await openArchiveStore()
    if (!db) throw new Error('数据库存储不可用')
    try {
        await new Promise((resolve, reject) => {
            const transaction = db.transaction('archives', 'readwrite')
            write(transaction.objectStore('archives'))
            transaction.oncomplete = resolve
            transaction.onabort = () => reject(transaction.error)
        })
    } finally {
        db.close()
    }
}

async function archiveHash(bytes) {
    const digest = await crypto.subtle.digest('SHA-256', bytes)
    return Array.from(new Uint8Array(digest), byte => byte.toString(16).padStart(2, '0')).join('')
}

export async function importTimetable(bytes) {
    const hash = await archiveHash(bytes)
    const [current, , savedLegacy, ids] = await readCatalog()
    if (ids.some(id => id.endsWith(`:${hash}`))) return false
    const legacy = isLegacy(current) ? current : savedLegacy
    if (legacy && await archiveHash(legacy.bytes) === hash) return false
    const { version } = await decodeArchive(bytes)
    await writeCatalog(store => store.add({ bytes, checkedAt: 0, imported: true }, `${prefix}${version}:${hash}`))
    return true
}

function validateId(id) {
    if (id !== legacyImportId && !/^import:\d{8}:[a-f0-9]{64}$/.test(id)) {
        throw new Error('路路通数据库版本无效')
    }
}

export async function selectTimetable(id) {
    validateId(id)
    const [current, selection, saved] = await readArchiveRecords('current', manualSelectionKey, id)
    if (selectedImportId(current, selection) === id) return
    const archive = id === legacyImportId && isLegacy(current) ? current : saved
    if (!archive) throw new Error('本机暂无路路通数据库')
    await loadLltDatabase.importArchive(new Uint8Array(archive.bytes), () =>
        writeCatalog(store => store.put(id, manualSelectionKey)))
}

export async function selectOfficialTimetable() {
    const [current, selection] = await readArchiveRecords('current', manualSelectionKey)
    if (selectedImportId(current, selection)) {
        await writeCatalog(store => store.put(null, manualSelectionKey))
    }
    await loadLltDatabase.reloadSelection()
}

export async function deleteTimetable(id) {
    validateId(id)
    const [current, selection, saved] = await readArchiveRecords('current', manualSelectionKey, id)
    const wasActive = selectedImportId(current, selection) === id
    const wasCurrent = selectedImportId(current) === id
    const archive = saved ?? (wasCurrent ? current : null)
    if (!archive) throw new Error('本机暂无路路通数据库')
    await writeCatalog(store => {
        store.delete(id)
        if (wasActive) store.put(null, manualSelectionKey)
        if (wasCurrent) {
            const request = store.get('current')
            request.onsuccess = () => {
                if (selectedImportId(request.result) === id) store.delete('current')
            }
        }
    })
    if (wasActive) await loadLltDatabase.reloadSelection()
    return { id, archive, wasActive }
}

export async function restoreTimetable({ id, archive, wasActive }) {
    const [current, selection] = await readArchiveRecords('current', manualSelectionKey)
    if (wasActive && !selectedImportId(current, selection)) {
        await loadLltDatabase.importArchive(new Uint8Array(archive.bytes), () =>
            writeCatalog(store => {
                store.put(archive, id)
                store.put(id, manualSelectionKey)
            }))
    } else {
        await writeCatalog(store => store.put(archive, id))
    }
}

export async function timetableCatalog(currentVersion, autoUpdate) {
    const [current, selection, savedLegacy, ids] = await readCatalog()
    const activeId = autoUpdate ? null : selectedImportId(current, selection)
    const entries = ids.map(id => ({ id, version: id.split(':')[1] }))
    const legacy = isLegacy(current) ? current : savedLegacy
    if (legacy) {
        const version = activeId === legacyImportId && currentVersion
            ? currentVersion
            : archiveVersion(legacy)
        if (version) entries.push({ id: legacyImportId, version })
    }
    const officialVersion = (!activeId && currentVersion)
        || (!current?.imported && archiveVersion(current))
        || await readBundledVersion()
    return {
        activeId,
        officialVersion,
        entries: entries.sort((a, b) => b.version.localeCompare(a.version) || a.id.localeCompare(b.id)),
    }
}
