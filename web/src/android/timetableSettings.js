import { loadLltDatabase } from './railway/lltLoader.js'
import {
  deleteTimetable, importTimetable, restoreTimetable, selectOfficialTimetable, selectTimetable, timetableCatalog,
} from './railway/importedArchives.js'
import { timetableAutoUpdate } from './networkFeatures.js'

export function initializeTimetableSettings() {
  let transfer
  let busy = false
  let removed
  let snapshotSequence = 0
  const send = (id, status, details) => globalThis.CRTicketAndroid?.settingsResult(JSON.stringify({
    id, status, success: status === 'success', ...details,
  }))
  const manual = () => globalThis.CRTicketDeveloper?.lltAutoUpdate?.() === false

  async function change(id, action, manualOnly = true) {
    if (busy || (manualOnly && !manual())) return send(id, 'failed')
    busy = true
    const revision = loadLltDatabase.revision
    let status
    try {
      status = await action()
      if (manualOnly && status === 'success' && revision !== loadLltDatabase.revision) {
        await loadLltDatabase().catch(() => {})
        window.dispatchEvent(new Event('crticket-timetable-replaced'))
      }
    } catch {
      status = 'failed'
    } finally {
      busy = false
    }
    send(id, status)
  }

  window.crticketTimetable = {
    begin(id, size) {
      if (busy || !Number.isInteger(size) || size <= 0 || size > 16 * 1024 * 1024) return false
      transfer = { id, bytes: new Uint8Array(size), offset: 0 }
      return true
    },
    append(id, base64) {
      if (!transfer || transfer.id !== id) return false
      const chunk = Uint8Array.from(atob(base64), char => char.charCodeAt(0))
      if (transfer.offset + chunk.length > transfer.bytes.length) return false
      transfer.bytes.set(chunk, transfer.offset)
      transfer.offset += chunk.length
      return true
    },
    finish(id) {
      if (!transfer || transfer.id !== id) return send(id, 'failed')
      const current = transfer
      transfer = null
      return change(id, async () => {
        if (current.offset !== current.bytes.length) throw new Error('文件不完整')
        return await importTimetable(current.bytes) ? 'success' : 'duplicate'
      }, false)
    },
    cancel(id) {
      if (transfer?.id === id) transfer = null
    },
    select(id, archiveId) {
      return change(id, async () => {
        if (archiveId) await selectTimetable(archiveId)
        else await selectOfficialTimetable()
        return 'success'
      })
    },
    delete(id, archiveId) {
      return change(id, async () => {
        removed = await deleteTimetable(archiveId)
        return 'success'
      })
    },
    restore(id, archiveId) {
      return change(id, async () => {
        if (removed?.id !== archiveId) return 'failed'
        await restoreTimetable(removed)
        removed = null
        return 'success'
      })
    },
    discardUndo(archiveId) {
      if (removed?.id === archiveId) removed = null
    },
    async configure() {
      if (!timetableAutoUpdate()) {
        await loadLltDatabase.pauseUpdates()
        return
      }
      const revision = loadLltDatabase.revision
      await selectOfficialTimetable().catch(() => {})
      await loadLltDatabase.fresh().catch(() => {})
      if (revision !== loadLltDatabase.revision) window.dispatchEvent(new Event('crticket-timetable-replaced'))
    },
    async snapshot(localOnly = false) {
      const sequence = ++snapshotSequence
      const database = await (localOnly ? loadLltDatabase.local() : loadLltDatabase()).catch(() => null)
      const version = database?.version ?? null
      try {
        const catalog = await timetableCatalog(version, timetableAutoUpdate())
        if (sequence === snapshotSequence) send('timetable:snapshot', 'success', { ...catalog, version })
      } catch {
        if (sequence === snapshotSequence) send('timetable:snapshot', 'failed')
      }
      return version
    },
  }
}
