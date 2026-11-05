import { readonly, ref } from 'vue'
import { showNonRunningTrains } from '../android/timetableVisibility.js'

const enabled = ref(showNonRunningTrains())
globalThis.window?.addEventListener('crticket-timetable-visibility-changed', () => {
  enabled.value = showNonRunningTrains()
})

export function useTimetableVisibility() {
  return readonly(enabled)
}
