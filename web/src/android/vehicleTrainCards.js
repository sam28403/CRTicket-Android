import { onBeforeUnmount } from 'vue'
import { native } from './platform.js'
import { loadLltDatabase } from './railway/lltLoader.js'
import { findLltTrainCandidates, lltTrainSummary } from './railway/lltList.js'

// Reuse the exact timetable summaries used by the train list, on each history date.
export function useVehicleTrainCards() {
  if (typeof native?.trainListResult !== 'function') return
  let generation = 0
  async function request(event) {
    const { records, requestId } = event.detail || {}
    if (!Array.isArray(records) || records.length > 500) return
    const token = ++generation
    try {
      const database = await loadLltDatabase()
      const rows = records.flatMap(({ train, date }) => {
        const candidate = findLltTrainCandidates(database, train.split('/')[0], date)[0]
        if (!candidate) return []
        return [{ ...lltTrainSummary(candidate), train, key: `${date}|${train}` }]
      })
      if (token === generation) native.trainListResult(JSON.stringify({
        kind: 'vehicleCards', requestId, rows,
      }))
    } catch {
      if (token === generation) native.trainListResult(JSON.stringify({
        kind: 'vehicleCards', requestId, rows: [],
      }))
    }
  }
  window.addEventListener('crticket-vehicle-cards-request', request)
  onBeforeUnmount(() => {
    generation++
    window.removeEventListener('crticket-vehicle-cards-request', request)
  })
}
