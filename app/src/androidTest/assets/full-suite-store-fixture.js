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
          if (action === 'save') store.put({ value: request.result || null }, backupKey)
          else if (action === 'cache') {
            if (!request.result) return transaction.abort()
            store.put({ ...request.result, imported: false }, 'current')
          }
          else {
            if (!request.result) return transaction.abort()
            if (request.result.value) store.put(request.result.value, 'current')
            else store.delete('current')
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
