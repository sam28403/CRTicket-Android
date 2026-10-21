import { onBeforeUnmount } from 'vue'
import { native } from './platform.js'
import { loadLltDatabase } from './railway/lltLoader.js'
import { queryLltRouting } from './railway/lltRouting.js'

export function useRoutingQuery() {
  if (typeof native?.trainListResult !== 'function') return
  let generation = 0
  async function request(event) {
    const { train, date, requestId } = event.detail || {}
    const token = ++generation
    try {
      const database = await loadLltDatabase()
      const result = queryLltRouting(database, train, date)
      if (token === generation) native.trainListResult(JSON.stringify({
        kind: 'routing', requestId, result,
      }))
    } catch {
      if (token === generation) native.trainListResult(JSON.stringify({
        kind: 'routing', requestId, error: true,
      }))
    }
  }
  window.addEventListener('crticket-routing-request', request)
  onBeforeUnmount(() => {
    generation++
    window.removeEventListener('crticket-routing-request', request)
  })
}
