(() => {
  window.fullSuiteArchive = async action => {
    window.fullSuiteArchiveResult = 'pending'
    const db = await new Promise((resolve, reject) => {
      const request = indexedDB.open('crticket-phone-timetable', 1)
      request.onupgradeneeded = () => request.result.createObjectStore('archives')
      request.onsuccess = () => resolve(request.result)
      request.onerror = () => reject(request.error)
    })
    try {
      await new Promise((resolve, reject) => {
        const transaction = db.transaction('archives', 'readwrite')
        const store = transaction.objectStore('archives')
        const backupKey = 'full-suite-original'
        const request = store.get(action === 'restore' ? backupKey : 'current')
        request.onsuccess = () => {
          if (action === 'save') {
            const selection = store.get('manual-selection')
            const legacy = store.get('legacy-import')
            const keys = store.getAllKeys(IDBKeyRange.bound('import:', 'import;'))
            keys.onsuccess = () => store.put({
              value: request.result || null, selection: selection.result, legacy: legacy.result, imports: keys.result,
            }, backupKey)
          } else if (action === 'cache') {
            const selection = store.get('manual-selection')
            selection.onsuccess = () => {
              const id = selection.result ?? request.result?.importId ?? 'legacy-import'
              const selected = store.get(id)
              selected.onsuccess = () => {
                const archive = selected.result ?? request.result
                if (!archive?.imported) return transaction.abort()
                store.put({ bytes: archive.bytes, checkedAt: 0 }, 'current')
                store.put(null, 'manual-selection')
              }
            }
          } else {
            if (!request.result) return transaction.abort()
            const backup = request.result
            if (backup.value) store.put(backup.value, 'current')
            else store.delete('current')
            if (backup.imports) {
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
            store.delete(backupKey)
          }
        }
        transaction.oncomplete = resolve
        transaction.onerror = () => reject(transaction.error)
        transaction.onabort = () => reject(transaction.error)
      })
      window.fullSuiteArchiveResult = 'done'
    } catch { window.fullSuiteArchiveResult = 'error' }
    finally { db.close() }
  }
})()
