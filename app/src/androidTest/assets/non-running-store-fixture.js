(() => {
    let original
    let captured = false
    window.nonRunningStore = async action => {
        window.nonRunningStoreResult = 'pending'
        const db = await new Promise((resolve, reject) => {
            const open = indexedDB.open('crticket-phone-timetable', 1)
            open.onupgradeneeded = () => open.result.createObjectStore('archives')
            open.onsuccess = () => resolve(open.result)
            open.onerror = () => reject(open.error)
        })
        try {
            if (action === 'save' || action === 'persist') {
                original = await new Promise((resolve, reject) => {
                    const request = db.transaction('archives').objectStore('archives').get('current')
                    request.onsuccess = () => resolve(request.result)
                    request.onerror = () => reject(request.error)
                })
                captured = true
                if (action === 'persist') {
                    await new Promise((resolve, reject) => {
                        const tx = db.transaction('archives', 'readwrite')
                        tx.objectStore('archives').put({ value: original || null }, 'device-test-backup')
                        tx.oncomplete = resolve
                        tx.onerror = () => reject(tx.error)
                    })
                }
            } else if (action === 'recover') {
                await new Promise((resolve, reject) => {
                    const tx = db.transaction('archives', 'readwrite')
                    const store = tx.objectStore('archives')
                    const request = store.get('device-test-backup')
                    request.onsuccess = () => {
                        if (!request.result) return
                        if (request.result.value) store.put(request.result.value, 'current')
                        else store.delete('current')
                        store.delete('device-test-backup')
                    }
                    tx.oncomplete = resolve
                    tx.onerror = () => reject(tx.error)
                })
            } else if (captured) {
                await new Promise((resolve, reject) => {
                    const transaction = db.transaction('archives', 'readwrite')
                    const store = transaction.objectStore('archives')
                    if (original) store.put(original, 'current')
                    else store.delete('current')
                    transaction.oncomplete = resolve
                    transaction.onerror = () => reject(transaction.error)
                    transaction.onabort = () => reject(transaction.error)
                })
            }
            window.nonRunningStoreResult = 'done'
        } catch {
            window.nonRunningStoreResult = 'error'
        } finally {
            db.close()
        }
    }
})()
