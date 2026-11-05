import { loadLltDatabase } from './railway/lltLoader.js'

export function initializeTimetableVersion() {
  window.crticketTimetableVersion = async () => {
    try { return (await loadLltDatabase()).version } catch { return null }
  }
}
