import { computed, ref, watch } from 'vue'
import { hasTrainTimeZoneOptions, trainStopsInTimeZone, trainTimeZones } from '../utils/trainTimeZoneDisplay.js'

export function useTrainTimeZone(timing, schedule = timing) {
  const selected = ref('')
  const options = computed(() => {
    const snapshot = schedule.value
    if (!snapshot?.stops.length) return []
    if (snapshot.timeZone === 'vientiane' || snapshot.defaultTimeZone === 'vientiane') {
      return trainTimeZones.filter(zone => zone.value !== 'local')
    }
    return hasTrainTimeZoneOptions(snapshot.train) ? trainTimeZones : []
  })
  watch(schedule, snapshot => {
    selected.value = options.value.length
      ? snapshot.defaultTimeZone || snapshot.timeZone || 'local'
      : ''
  }, { flush: 'sync' })
  const result = computed(() => {
    const snapshot = timing.value
    const zone = options.value.find(option => option.value === selected.value)
    if (!snapshot || !zone) return snapshot
    const convert = stops => trainStopsInTimeZone(stops, zone.value, snapshot.timeZone)
    return {
      ...snapshot,
      stops: convert(snapshot.stops),
      ...(snapshot.scheduledStops ? { scheduledStops: convert(snapshot.scheduledStops) } : {}),
      displayTimeZone: zone.label,
    }
  })
  return {
    result, selected, options,
    select(value) {
      if (options.value.some(zone => zone.value === value)) selected.value = value
    },
  }
}
