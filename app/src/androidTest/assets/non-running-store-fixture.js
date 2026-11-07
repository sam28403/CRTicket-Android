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
                    const store = db.transaction('archives').objectStore('archives')
                    const current = store.get('current')
                    const selection = store.get('manual-selection')
                    const legacy = store.get('legacy-import')
                    const keys = store.getAllKeys(IDBKeyRange.bound('import:', 'import;'))
                    keys.onsuccess = () => resolve({
                        value: current.result,
                        selection: selection.result,
                        legacy: legacy.result,
                        imports: keys.result,
                    })
                    store.transaction.onabort = () => reject(store.transaction.error)
                })
                captured = true
                if (action === 'persist') {
                    await new Promise((resolve, reject) => {
                        const tx = db.transaction('archives', 'readwrite')
                        tx.objectStore('archives').put(original, 'device-test-backup')
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
                        restoreCatalog(store, request.result)
                        store.delete('device-test-backup')
                    }
                    tx.oncomplete = resolve
                    tx.onerror = () => reject(tx.error)
                })
            } else if (captured) {
                await new Promise((resolve, reject) => {
                    const transaction = db.transaction('archives', 'readwrite')
                    const store = transaction.objectStore('archives')
                    restoreCatalog(store, original)
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

    function restoreCatalog(store, backup) {
        if (backup.value) store.put(backup.value, 'current')
        else store.delete('current')
        if (!backup.imports) return
        if (backup.selection === undefined) store.delete('manual-selection')
        else store.put(backup.selection, 'manual-selection')
        if (backup.legacy === undefined) store.delete('legacy-import')
        else store.put(backup.legacy, 'legacy-import')
        const keys = store.getAllKeys(IDBKeyRange.bound('import:', 'import;'))
        keys.onsuccess = () => {
            for (const key of keys.result) {
                if (!backup.imports.includes(key)) store.delete(key)
            }
        }
    }
})()
