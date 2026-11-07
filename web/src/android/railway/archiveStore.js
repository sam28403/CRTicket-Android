const DATABASE = 'crticket-phone-timetable'
export const legacyImportId = 'legacy-import'
export const manualSelectionKey = 'manual-selection'

export async function openArchiveStore() {
    if (!globalThis.indexedDB) return null
    return new Promise((resolve, reject) => {
        const request = indexedDB.open(DATABASE, 1)
        request.onupgradeneeded = () => request.result.createObjectStore('archives')
        request.onsuccess = () => resolve(request.result)
        request.onerror = () => reject(request.error)
    })
}

export async function readArchiveRecords(...keys) {
    const db = await openArchiveStore()
    if (!db) return []
    try {
        const store = db.transaction('archives').objectStore('archives')
        return await Promise.all(keys.map(key => new Promise((resolve, reject) => {
            const request = key instanceof IDBKeyRange ? store.getAllKeys(key) : store.get(key)
            request.onsuccess = () => resolve(request.result)
            request.onerror = () => reject(request.error)
        })))
    } finally {
        db.close()
    }
}

export async function readArchive() {
    const [current] = await readArchiveRecords('current')
    return current ?? null
}

export function selectedImportId(current, selection) {
    if (selection !== undefined) return selection
    return current?.imported ? current.importId || legacyImportId : null
}

export async function readEffectiveArchive(autoUpdate) {
    if (typeof __CRTICKET_CI__ !== 'undefined' && !__CRTICKET_CI__) return readArchive()
    const [current, selection] = await readArchiveRecords('current', manualSelectionKey)
    if (!autoUpdate) {
        const id = selectedImportId(current, selection)
        if (id) {
            if (current?.imported && selectedImportId(current) === id) return current
            const [archive] = await readArchiveRecords(id)
            if (archive) return archive
        }
    }
    return current?.imported ? null : current
}

export async function writeArchive(archive) {
    const db = await openArchiveStore()
    if (!db) throw new Error('数据库存储不可用')
    try {
        await new Promise((resolve, reject) => {
            const transaction = db.transaction('archives', 'readwrite')
            const store = transaction.objectStore('archives')
            if (typeof __CRTICKET_CI__ === 'undefined' || __CRTICKET_CI__) {
                preserveLegacyImport(store)
            }
            store.put(archive, 'current')
            transaction.oncomplete = resolve
            transaction.onerror = () => reject(transaction.error)
            transaction.onabort = () => reject(transaction.error)
        })
    } finally {
        db.close()
    }
}

// The previous CI build only saved its import under "current".
export function preserveLegacyImport(store) {
    const request = store.get('current')
    request.onsuccess = () => {
        const current = request.result
        if (current?.imported && !current.importId) store.put(current, legacyImportId)
    }
}

export async function clearArchive() {
    const db = await openArchiveStore()
    if (!db) return
    try {
        await new Promise((resolve, reject) => {
            const transaction = db.transaction('archives', 'readwrite')
            const store = transaction.objectStore('archives')
            const request = store.get('current')
            request.onsuccess = () => {
                if (!request.result?.imported) store.delete('current')
            }
            transaction.oncomplete = resolve
            transaction.onerror = () => reject(transaction.error)
            transaction.onabort = () => reject(transaction.error)
        })
    } finally {
        db.close()
    }
}
