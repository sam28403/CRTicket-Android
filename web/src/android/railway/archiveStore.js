const DATABASE = 'crticket-phone-timetable'

async function openStore() {
    if (!globalThis.indexedDB) return null
    return new Promise((resolve, reject) => {
        const request = indexedDB.open(DATABASE, 1)
        request.onupgradeneeded = () => request.result.createObjectStore('archives')
        request.onsuccess = () => resolve(request.result)
        request.onerror = () => reject(request.error)
    })
}

export async function readArchive() {
    const db = await openStore()
    if (!db) return null
    try {
        return await new Promise((resolve, reject) => {
            const request = db.transaction('archives').objectStore('archives').get('current')
            request.onsuccess = () => resolve(request.result || null)
            request.onerror = () => reject(request.error)
        })
    } finally {
        db.close()
    }
}

export async function writeArchive(archive) {
    const db = await openStore()
    if (!db) return
    try {
        await new Promise((resolve, reject) => {
            const transaction = db.transaction('archives', 'readwrite')
            transaction.objectStore('archives').put(archive, 'current')
            transaction.oncomplete = resolve
            transaction.onerror = () => reject(transaction.error)
            transaction.onabort = () => reject(transaction.error)
        })
    } finally {
        db.close()
    }
}
