import { ref, watch } from 'vue'
import { loadLltDatabase } from '@/android/railway/lltLoader.js'
import { nextRoutingTrain } from '@/android/railway/nextRouting.js'

// Resolve independently: missing routing data must not delay or fail the timetable.
export function useNextRouting(result) {
  const next = ref(null)
  const completed = new WeakMap()
  watch(result, async (snapshot, _, onCleanup) => {
    let current = true
    onCleanup(() => { current = false })
    if (!snapshot?.stops?.length) {
      next.value = null
      return
    }
    if (completed.has(snapshot)) {
      next.value = completed.get(snapshot)
      return
    }
    next.value = null
    try {
      const database = await loadLltDatabase()
      const row = nextRoutingTrain(database, snapshot.train, snapshot.date)
      completed.set(snapshot, row)
      if (current) next.value = row
    } catch {
      // An absent table or failed offline download simply hides the optional section.
    }
  }, { immediate: true })
  return next
}
