import { loadLltDatabase } from './railway/lltLoader.js'

export function initializeTimetableSettings() {
  let transfer
  let importing = false
  const send = (id, success) => globalThis.CRTicketAndroid?.settingsResult(JSON.stringify({ id, success }))
  window.crticketTimetable = {
    begin(id, size) {
      if (importing || globalThis.CRTicketDeveloper?.lltAutoUpdate?.() !== false
        || !Number.isInteger(size) || size <= 0 || size > 16 * 1024 * 1024) return false
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
    async finish(id) {
      if (!transfer || transfer.id !== id || importing) return send(id, false)
      const current = transfer
      transfer = null
      importing = true
      try {
        if (current.offset !== current.bytes.length) throw new Error('文件不完整')
        await loadLltDatabase.importArchive(current.bytes)
        window.dispatchEvent(new Event('crticket-timetable-replaced'))
        send(id, true)
      } catch {
        send(id, false)
      } finally {
        importing = false
      }
    },
    async configure() {
      await loadLltDatabase.pauseUpdates()
      if (globalThis.CRTicketDeveloper?.lltAutoUpdate?.() !== false) {
        await loadLltDatabase().catch(() => {})
      }
    },
    async snapshot() {
      try {
        const database = await loadLltDatabase()
        send('timetable:snapshot', true)
        return database.version
      } catch {
        send('timetable:snapshot', false)
        return null
      }
    },
  }
}
