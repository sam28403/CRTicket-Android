(() => {
    let original
    const open = () => new Promise((resolve, reject) => {
        const request = indexedDB.open('crticket-phone-timetable', 1)
        request.onupgradeneeded = () => request.result.createObjectStore('archives')
        request.onsuccess = () => resolve(request.result)
        request.onerror = () => reject(request.error)
    })
    window.storageTimetableFixture = async action => {
        window.storageTimetableResult = 'pending'
        const db = await open()
        try {
            if (action === 'seed') {
                original = await new Promise((resolve, reject) => {
                    const request = db.transaction('archives').objectStore('archives').get('current')
                    request.onsuccess = () => resolve(request.result)
                    request.onerror = () => reject(request.error)
                })
            }
            if (action === 'exists') {
                const exists = await new Promise((resolve, reject) => {
                    const request = db.transaction('archives').objectStore('archives').get('current')
                    request.onsuccess = () => resolve(Boolean(request.result))
                    request.onerror = () => reject(request.error)
                })
                window.storageTimetableResult = exists ? 'exists' : 'empty'
                return
            }
            await new Promise((resolve, reject) => {
                const transaction = db.transaction('archives', 'readwrite')
                const store = transaction.objectStore('archives')
                if (action === 'seed') store.put({ bytes: new Uint8Array(8192), checkedAt: Date.now() }, 'current')
                else if (original) store.put(original, 'current')
                else store.delete('current')
                transaction.oncomplete = resolve
                transaction.onerror = () => reject(transaction.error)
                transaction.onabort = () => reject(transaction.error)
            })
            window.storageTimetableResult = 'done'
        } catch {
            window.storageTimetableResult = 'error'
        } finally {
            db.close()
        }
    }
})()
